package io.github.kete1987.pokerbankroll.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The base currency, the rates typed by hand and the status of the downloads (off in the tests). */
class ExchangeRateApiTests extends ApiIntegrationTest {

    @Test
    void withNothingTheBaseCurrencyIsTheEuro() {
        assertThat(mvc.get().uri("/settings/currency")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"baseCurrencyCode": null, "automaticBaseCurrencyCode": "EUR", "effectiveBaseCurrencyCode": "EUR"}
                """);
    }

    @Test
    void theAutomaticBaseCurrencyIsTheOneWithMostGamesThenTheFirstInUse() {
        long stars = insertRoom("PokerStars", "USD");
        // A room without games: its currency, the only one there is.
        assertThat(effectiveBase()).isEqualTo("USD");

        long winamax = insertRoom("Winamax", "EUR");
        // No games: the first one.
        assertThat(effectiveBase()).isEqualTo("EUR");
        insertGame(stars, "TOURNAMENT", null);
        assertThat(effectiveBase()).isEqualTo("USD");
        insertGame(winamax, "TOURNAMENT", null);
        insertGame(winamax, "TOURNAMENT", null);
        assertThat(effectiveBase()).isEqualTo("EUR");
    }

    @Test
    void theBaseCurrencyIsChosenAndLeftAutomaticAgain() {
        long stars = insertRoom("PokerStars", "USD");
        insertGame(stars, "TOURNAMENT", null);

        assertThat(putJson("/settings/currency", "{\"baseCurrencyCode\": \"eur\"}")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"baseCurrencyCode": "EUR", "automaticBaseCurrencyCode": "USD", "effectiveBaseCurrencyCode": "EUR"}
                        """);
        assertThat(effectiveBase()).isEqualTo("EUR");

        assertThat(putJson("/settings/currency", "{\"baseCurrencyCode\": null}")).hasStatusOk().bodyJson()
                .extractingPath("$.effectiveBaseCurrencyCode").isEqualTo("USD");
        assertThat(putJson("/settings/currency", "{}")).hasStatusOk();
    }

    @Test
    void theBaseCurrencyMustExist() {
        assertThat(putJson("/settings/currency", "{\"baseCurrencyCode\": \"XYZ\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_CURRENCY");
    }

    @Test
    void ratesAreTypedReplacedAndDeleted() {
        insertRate("USD", "2026-10-01", "1.1200");

        assertThat(putJson("/exchange-rates/manual/usd/2026-10-01", "{\"rate\": 1.0850}")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"currencyCode": "USD", "date": "2026-10-01", "rate": 1.0850, "downloadedRate": 1.12}
                        """);
        assertThat(putJson("/exchange-rates/manual/USD/2026-10-01", "{\"rate\": 1.09}")).hasStatusOk();
        assertThat(putJson("/exchange-rates/manual/USD/2026-09-01", "{\"rate\": 1.05}")).hasStatusOk().bodyJson()
                .extractingPath("$.downloadedRate").isNull();

        String list = body("/exchange-rates/manual");
        assertThat(JsonPath.<List<String>>read(list, "$[*].date")).containsExactly("2026-10-01", "2026-09-01");
        assertThat(JsonPath.<Double>read(list, "$[0].rate")).isEqualTo(1.09);

        assertThat(mvc.delete().uri("/exchange-rates/manual/USD/2026-10-01")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/exchange-rates/manual/USD/2026-10-01")).hasStatus(HttpStatus.NOT_FOUND);
        // The downloaded one is still there.
        assertThat(jdbc.queryForObject("select count(*) from exchange_rate where source = 'ECB'", Integer.class))
                .isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(body("/exchange-rates/manual"), "$[*].date"))
                .containsExactly("2026-09-01");
    }

    @Test
    void aRateIsPositiveOfAKnownCurrencyOtherThanTheEuro() {
        assertThat(putJson("/exchange-rates/manual/EUR/2026-10-01", "{\"rate\": 1}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("EXCHANGE_RATE_OF_EUR");
        assertThat(putJson("/exchange-rates/manual/XYZ/2026-10-01", "{\"rate\": 1}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_CURRENCY");
        assertThat(putJson("/exchange-rates/manual/USD/2026-10-01", "{\"rate\": 0}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].code").isEqualTo("Positive");
        assertThat(putJson("/exchange-rates/manual/USD/2026-10-01", "{}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].code").isEqualTo("NotNull");
        assertThat(putJson("/exchange-rates/manual/USD/2026-10-01", "{\"rate\": 1.123456789}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors[0].code").isEqualTo("Digits");
        assertThat(putJson("/exchange-rates/manual/USD/not-a-date", "{\"rate\": 1}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theStatusSaysWhichCurrenciesNeedRatesAndWhichThereAre() {
        long winamax = insertRoom("Winamax", "EUR");
        long stars = insertRoom("PokerStars", "USD");
        insertRoom("Unused", "USD");
        jdbc.update("insert into game (played_on, room_id, game_type_code, buy_in) values ('2026-01-10', ?, 'CASH', 1)",
                stars);
        jdbc.update("insert into game (played_on, room_id, game_type_code, buy_in) values ('2026-01-05', ?, 'CASH', 1)",
                winamax);
        insertRate("USD", "2026-02-02", "1.10");
        insertRate("USD", "2026-03-02", "1.15");
        insertRate("USD", "2026-02-20", "1.12", "MANUAL");
        chooseBaseCurrency("EUR");

        assertThat(mvc.get().uri("/exchange-rates/status")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {
                  "enabled": false,
                  "running": false,
                  "lastAttemptAt": null,
                  "lastSuccessAt": null,
                  "lastError": null,
                  "baseCurrencyCode": "EUR",
                  "currencies": [{"currencyCode": "USD", "firstRateOn": "2026-02-02", "lastRateOn": "2026-03-02",
                                  "neededFrom": "2026-01-10", "manualRates": 1}]
                }
                """);

        // In dollars, the dollar is needed from the first game in euros.
        chooseBaseCurrency("USD");
        assertThat(mvc.get().uri("/exchange-rates/status")).hasStatusOk().bodyJson()
                .extractingPath("$.currencies[0].neededFrom").isEqualTo("2026-01-05");
    }

    @Test
    void refreshingIsRefusedWhenDownloadsAreOff() {
        assertThat(mvc.post().uri("/exchange-rates/refresh")).hasStatus(HttpStatus.CONFLICT).bodyJson()
                .extractingPath("$.code").isEqualTo("EXCHANGE_RATES_DISABLED");
    }

    private String effectiveBase() {
        return JsonPath.read(body("/settings/currency"), "$.effectiveBaseCurrencyCode");
    }

    private String body(String uri) {
        var result = mvc.get().uri(uri).exchange();
        assertThat(result).hasStatusOk();
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
