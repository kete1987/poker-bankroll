package io.github.kete1987.pokerbankroll.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Downloading the rates, against a web server started here that answers as Frankfurter does
 * (its v2 API: a list of {@code {date, base, quote, rate}}).
 */
@TestPropertySource(properties = {
    "poker-bankroll.exchange-rates.enabled=true",
    "poker-bankroll.exchange-rates.timeout=5s"})
class ExchangeRateDownloadApiTests extends ApiIntegrationTest {

    private static final String RATES = """
            [{"date":"2026-01-05","base":"EUR","quote":"USD","rate":1.1012},
             {"date":"2026-01-06","base":"EUR","quote":"USD","rate":1.1034},
             {"date":"2026-01-06","base":"EUR","quote":"GBP","rate":0.85}]
            """;

    private static final HttpServer SERVER = start();
    /** The query strings received. */
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    /** What the server answers: a status and a body, or {@code null} to hang up without answering. */
    private static volatile Answer answer = new Answer(200, RATES);

    @Autowired
    ExchangeRateDownloader downloader;

    long winamax;
    long pokerStars;

    private record Answer(int status, String body) {
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v2/rates", ExchangeRateDownloadApiTests::respond);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void frankfurter(DynamicPropertyRegistry registry) {
        registry.add("poker-bankroll.exchange-rates.url",
                () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/v2");
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    private static void respond(HttpExchange exchange) throws IOException {
        REQUESTS.add(exchange.getRequestURI().getQuery());
        Answer now = answer;
        if (now == null) {
            exchange.close();
            return;
        }
        byte[] body = now.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(now.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @BeforeEach
    void createRoomsAndGames() throws Exception {
        // The download of the start, if it is still running, finds nothing to do.
        downloader.awaitBackground();
        REQUESTS.clear();
        answer = new Answer(200, RATES);
        winamax = insertRoom("Winamax", "EUR");
        pokerStars = insertRoom("PokerStars", "USD");
        game(winamax, "2026-01-02");
        game(pokerStars, "2026-01-05");
        chooseBaseCurrency("EUR");
    }

    @Test
    void downloadsTheRatesOfEveryCurrencyInUseFromTheFirstAmountAndThenOnlyWhatIsMissing() {
        String status = refresh();

        String today = LocalDate.now().toString();
        assertThat(REQUESTS).containsExactly(
                "base=EUR&quotes=USD&from=2026-01-02&to=" + today + "&providers=ecb");
        // Only the rates of the currency asked for.
        assertThat(jdbc.queryForList("select rate_date || ' ' || currency_code || ' ' || rate || ' ' || source "
                + "from exchange_rate order by rate_date", String.class))
                .containsExactly("2026-01-05 USD 1.10120000 ECB", "2026-01-06 USD 1.10340000 ECB");
        assertThat(JsonPath.<Object>read(status, "$.lastError")).isNull();
        assertThat(JsonPath.<String>read(status, "$.lastSuccessAt")).isNotNull();
        assertThat(JsonPath.<Boolean>read(status, "$.enabled")).isTrue();
        assertThat(JsonPath.<String>read(status, "$.currencies[0].lastRateOn")).isEqualTo("2026-01-06");

        // Again: what is before the first rate (a weekend, a holiday...) and after the last one.
        REQUESTS.clear();
        refresh();
        assertThat(REQUESTS).containsExactly(
                "base=EUR&quotes=USD&from=2026-01-02&to=2026-01-04&providers=ecb",
                "base=EUR&quotes=USD&from=2026-01-07&to=" + today + "&providers=ecb");
    }

    @Test
    void aFailureIsReportedAndChangesNothing() {
        insertRate("USD", "2026-01-02", "1.05");
        answer = new Answer(500, "{\"status\":500}");
        String status = refresh();
        assertThat(JsonPath.<String>read(status, "$.lastError")).isEqualTo("USD: HTTP 500");
        assertThat(jdbc.queryForObject("select count(*) from exchange_rate", Integer.class)).isEqualTo(1);

        answer = null;
        assertThat(JsonPath.<String>read(refresh(), "$.lastError")).startsWith("USD: ");

        answer = new Answer(200, "<html>Not JSON</html>");
        assertThat(JsonPath.<String>read(refresh(), "$.lastError"))
                .isEqualTo("USD: the answer is not a list of rates");

        // It works again: the error is gone.
        answer = new Answer(200, RATES);
        assertThat(JsonPath.<Object>read(refresh(), "$.lastError")).isNull();
    }

    @Test
    void aCurrencyTheServiceDoesNotKnowHasNoRatesButIsNoError() {
        answer = new Answer(422, "{\"status\":422,\"message\":\"invalid currency: USD\"}");
        String status = refresh();
        assertThat(JsonPath.<Object>read(status, "$.lastError")).isNull();
        assertThat(JsonPath.<Object>read(status, "$.currencies[0].firstRateOn")).isNull();
    }

    @Test
    void anotherBaseCurrencyIsDownloadedInTheBackground() throws Exception {
        jdbc.update("delete from game where room_id = ?", pokerStars);
        // Only euros: nothing to download.
        refresh();
        assertThat(REQUESTS).isEmpty();

        assertThat(putJson("/settings/currency", "{\"baseCurrencyCode\": \"USD\"}")).hasStatusOk();
        downloader.awaitBackground();
        assertThat(REQUESTS).containsExactly(
                "base=EUR&quotes=USD&from=2026-01-02&to=" + LocalDate.now() + "&providers=ecb");
    }

    private String refresh() {
        var result = mvc.post().uri("/exchange-rates/refresh").exchange();
        assertThat(result).hasStatusOk();
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void game(long roomId, String playedOn) {
        jdbc.update("insert into game (played_on, room_id, game_type_code, buy_in) values (?::date, ?, 'CASH', 1)",
                playedOn, roomId);
    }
}
