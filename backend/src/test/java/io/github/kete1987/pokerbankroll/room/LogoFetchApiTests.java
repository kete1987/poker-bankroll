package io.github.kete1987.pokerbankroll.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.kete1987.pokerbankroll.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Downloading a logo from a URL, against a web server started here. That server is on this
 * machine, which the application refuses to fetch from, so these tests run with private
 * addresses allowed; {@link LogoFetchBlockedTests} covers the refusal.
 */
@SpringBootTest(properties = {
    "poker-bankroll.logo-fetch.allow-private-addresses=true",
    // Short, so the test of a server that never finishes does not take long.
    "poker-bankroll.logo-fetch.timeout=2s"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LogoFetchApiTests {

    private static final byte[] PNG = image(64, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
    private static final byte[] JPEG = image(64, 0xFF, 0xD8, 0xFF, 0xE0);

    private static HttpServer server;
    private static String site;

    @Autowired
    MockMvcTester mvc;

    @BeforeAll
    static void startSite() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // The content type the site declares is wrong on purpose: it is not trusted.
        server.createContext("/logo.png", exchange -> respond(exchange, 200, "text/html", PNG));
        server.createContext("/logo.jpg", exchange -> respond(exchange, 200, "image/jpeg", JPEG));
        server.createContext("/page", exchange -> respond(exchange, 200, "text/html", "<html></html>".getBytes()));
        server.createContext("/svg", exchange -> respond(exchange, 200, "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()));
        server.createContext("/huge", exchange ->
                respond(exchange, 200, "image/png", Arrays.copyOf(PNG, RemoteImageFetcher.MAX_BYTES + 1)));
        server.createContext("/largest", exchange ->
                respond(exchange, 200, "image/png", Arrays.copyOf(PNG, RemoteImageFetcher.MAX_BYTES)));
        server.createContext("/missing", exchange -> respond(exchange, 404, "text/plain", new byte[0]));
        server.createContext("/moved", exchange -> redirect(exchange, "/logo.jpg"));
        server.createContext("/moved-twice", exchange -> redirect(exchange, "/moved"));
        server.createContext("/loop", exchange -> redirect(exchange, "/loop"));
        server.createContext("/to-file", exchange -> redirect(exchange, "file:///etc/passwd"));
        server.createContext("/drip", LogoFetchApiTests::drip);
        // Each request gets its own thread: the one that drips must not hold the others.
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        site = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopSite() {
        server.stop(0);
    }

    @Test
    void returnsTheImageWithTheTypeFoundInItsContent() {
        MvcTestResult result = fetch(site + "/logo.png");

        assertThat(result).hasStatusOk();
        assertThat(result).headers().hasValue("Content-Type", "image/png");
        assertThat(result).headers().hasValue("X-Content-Type-Options", "nosniff");
        assertThat(result).headers().hasValue("Cache-Control", "no-store");
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PNG);
    }

    @Test
    void followsRedirects() {
        assertThat(fetch(site + "/moved")).hasStatusOk()
                .headers().hasValue("Content-Type", "image/jpeg");
        assertThat(fetch(site + "/moved-twice").getResponse().getContentAsByteArray()).isEqualTo(JPEG);
    }

    @Test
    void rejectsWhatIsNotAnImageOfAnAcceptedFormat() {
        assertFails(site + "/page", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "LOGO_UNSUPPORTED_TYPE");
        assertFails(site + "/svg", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "LOGO_UNSUPPORTED_TYPE");
    }

    @Test
    void limitsTheSizeOfTheDownload() {
        assertThat(fetch(site + "/largest")).hasStatusOk();
        assertThat(fetch(site + "/huge")).hasStatus(HttpStatus.CONTENT_TOO_LARGE).bodyJson()
                .extractingPath("$.detail").isEqualTo("The logo is too large: the maximum is 5,120 kB.");
    }

    @Test
    void saysSoWhenTheImageCannotBeDownloaded() {
        assertFails(site + "/missing", HttpStatus.BAD_GATEWAY, "LOGO_URL_UNREACHABLE");
        assertFails(site + "/loop", HttpStatus.BAD_GATEWAY, "LOGO_URL_UNREACHABLE");
        assertFails("http://no-such-host.invalid/logo.png", HttpStatus.BAD_GATEWAY, "LOGO_URL_UNREACHABLE");
        // Nothing listens on port 1.
        assertFails("http://127.0.0.1:1/logo.png", HttpStatus.BAD_GATEWAY, "LOGO_URL_UNREACHABLE");
    }

    @Test
    void onlyHttpAndHttpsAddressesAlsoAfterARedirect() {
        for (String url : new String[] {"ftp://example.com/logo.png", "file:///etc/passwd", "logo.png",
                "http:///logo.png", "http://user:secret@example.com/logo.png", "http://exa mple.com/"}) {
            assertFails(url, HttpStatus.BAD_REQUEST, "LOGO_URL_INVALID");
        }
        // A redirect to anything else is not followed.
        assertFails(site + "/to-file", HttpStatus.BAD_GATEWAY, "LOGO_URL_UNREACHABLE");
    }

    @Test
    void givesUpOnAServerThatNeverFinishes() {
        long start = System.nanoTime();

        assertFails(site + "/drip", HttpStatus.BAD_GATEWAY, "LOGO_URL_UNREACHABLE");

        // The deadline is for the whole download: a byte now and then does not extend it.
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(6));
    }

    @Test
    void theUrlIsRequired() {
        assertThat(mvc.post().uri("/rooms/logo-fetch").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.errors[0].field").isEqualTo("url");
    }

    private MvcTestResult fetch(String url) {
        return mvc.post().uri("/rooms/logo-fetch").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"" + url + "\"}").exchange();
    }

    private void assertFails(String url, HttpStatus status, String code) {
        assertThat(fetch(url)).as(url).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    /** Starts like an image and then sends a byte every now and then, for much longer than the deadline. */
    private static void drip(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "image/png");
        exchange.sendResponseHeaders(200, 0);
        try (var body = exchange.getResponseBody()) {
            body.write(PNG);
            for (int i = 0; i < 40; i++) {
                body.write(0);
                body.flush();
                Thread.sleep(250);
            }
        } catch (IOException | InterruptedException ex) {
            // The client gave up, which is the point.
        } finally {
            exchange.close();
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    /** Bytes that start with the given signature, padded to the given length. */
    private static byte[] image(int length, int... signature) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < signature.length; i++) {
            bytes[i] = (byte) signature[i];
        }
        return bytes;
    }
}
