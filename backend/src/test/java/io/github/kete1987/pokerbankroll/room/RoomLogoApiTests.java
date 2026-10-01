package io.github.kete1987.pokerbankroll.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Only the first bytes of an image are checked, so the images of these tests are just the
 * signature of each format followed by filler.
 */
class RoomLogoApiTests extends ApiIntegrationTest {

    private static final byte[] PNG = image(24, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
    private static final byte[] JPEG = image(24, 0xFF, 0xD8, 0xFF, 0xE0);
    private static final byte[] WEBP = image(24, 'R', 'I', 'F', 'F', 16, 0, 0, 0, 'W', 'E', 'B', 'P');

    // ---- upload ----

    @Test
    void uploadsALogoOfEachAcceptedFormat() {
        long png = insertRoom("Winamax", "EUR");
        long jpeg = insertRoom("888poker", "EUR");
        long webp = insertRoom("PokerStars", "USD");

        var json = assertThat(upload(png, "image/png", PNG)).hasStatusOk().bodyJson();
        json.extractingPath("$.id").isEqualTo((int) png);
        json.extractingPath("$.name").isEqualTo("Winamax");
        json.extractingPath("$.logoVersion").isEqualTo(storedVersion(png));
        assertThat(upload(jpeg, "image/jpeg", JPEG)).hasStatusOk();
        assertThat(upload(webp, "image/webp", WEBP)).hasStatusOk();

        assertThat(storedContentType(png)).isEqualTo("image/png");
        assertThat(storedContentType(jpeg)).isEqualTo("image/jpeg");
        assertThat(storedContentType(webp)).isEqualTo("image/webp");
        assertThat(jdbc.queryForObject("select content from room_logo where room_id = ?", byte[].class, webp))
                .isEqualTo(WEBP);
        assertThat(jdbc.queryForObject("select size_bytes from room_logo where room_id = ?", Integer.class, webp))
                .isEqualTo(WEBP.length);
    }

    @Test
    void replacingTheLogoChangesItsVersion() {
        long room = insertRoom("Winamax", "EUR");
        upload(room, "image/png", PNG);
        String first = storedVersion(room);

        assertThat(upload(room, "image/jpeg", JPEG)).hasStatusOk()
                .bodyJson().extractingPath("$.logoVersion").isNotEqualTo(first);

        assertThat(jdbc.queryForObject("select count(*) from room_logo", Integer.class)).isOne();
        var logo = mvc.get().uri("/rooms/{id}/logo", room).exchange();
        assertThat(logo).hasStatusOk().hasContentType("image/jpeg");
        assertThat(logo.getResponse().getContentAsByteArray()).isEqualTo(JPEG);
    }

    @Test
    void theFormatIsTheOneOfTheContentNotTheDeclaredOne() {
        long room = insertRoom("Winamax", "EUR");

        assertThat(upload(room, "image/jpeg", PNG)).hasStatusOk();
        assertThat(storedContentType(room)).isEqualTo("image/png");

        assertThat(upload(room, "application/octet-stream", WEBP)).hasStatusOk();
        assertThat(storedContentType(room)).isEqualTo("image/webp");
        assertThat(mvc.put().uri("/rooms/{id}/logo", room).content(JPEG)).hasStatusOk();
        assertThat(storedContentType(room)).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsContentThatIsNotAnAcceptedImage() {
        long room = insertRoom("Winamax", "EUR");
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] gif = image(24, 'G', 'I', 'F', '8', '9', 'a');
        // A RIFF container that is not WebP.
        byte[] wav = image(24, 'R', 'I', 'F', 'F', 16, 0, 0, 0, 'W', 'A', 'V', 'E');

        var json = assertThat(upload(room, "image/svg+xml", svg))
                .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE).bodyJson();
        json.extractingPath("$.code").isEqualTo("LOGO_UNSUPPORTED_TYPE");
        json.extractingPath("$.detail").isEqualTo("The logo must be a PNG, JPEG or WebP image.");
        // Declaring an accepted type does not help, nor does a signature cut short.
        for (byte[] content : new byte[][] {svg, "just text".getBytes(StandardCharsets.UTF_8), gif, wav,
                Arrays.copyOf(PNG, 7), Arrays.copyOf(JPEG, 2), Arrays.copyOf(WEBP, 11)}) {
            assertThat(upload(room, "image/png", content))
                    .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .bodyJson().extractingPath("$.code").isEqualTo("LOGO_UNSUPPORTED_TYPE");
        }
        assertThat(jdbc.queryForObject("select count(*) from room_logo", Integer.class)).isZero();
    }

    @Test
    void rejectsAnEmptyBody() {
        long room = insertRoom("Winamax", "EUR");

        assertThat(upload(room, "image/png", new byte[0]))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("LOGO_EMPTY");
        assertThat(mvc.put().uri("/rooms/{id}/logo", room).contentType("image/png"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("LOGO_EMPTY");
    }

    @Test
    void acceptsUpTo256KilobytesAndRejectsMore() {
        long room = insertRoom("Winamax", "EUR");

        assertThat(upload(room, "image/png", Arrays.copyOf(PNG, 256 * 1024))).hasStatusOk();
        String version = storedVersion(room);

        var json = assertThat(upload(room, "image/png", Arrays.copyOf(PNG, 256 * 1024 + 1)))
                .hasStatus(HttpStatus.CONTENT_TOO_LARGE).bodyJson();
        json.extractingPath("$.code").isEqualTo("LOGO_TOO_LARGE");
        json.extractingPath("$.detail").isEqualTo("The logo is too large: the maximum is 256 kB.");
        assertThat(mvc.put().uri("/rooms/{id}/logo", room).contentType("image/png")
                .content(Arrays.copyOf(PNG, 5 * 1024 * 1024)).header("Accept-Language", "es"))
                .hasStatus(HttpStatus.CONTENT_TOO_LARGE)
                .bodyJson().extractingPath("$.detail").isEqualTo("El logo es demasiado grande: el máximo es 256 kB.");
        assertThat(storedVersion(room)).isEqualTo(version);
    }

    @Test
    void cannotUploadALogoToAnUnknownRoom() {
        assertThat(upload(999_999, "image/png", PNG)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    // ---- serve ----

    @Test
    void servesTheLogoToBeCachedForeverUnderItsVersion() {
        long room = insertRoom("Winamax", "EUR");
        upload(room, "image/png", PNG);

        var logo = mvc.get().uri("/rooms/{id}/logo?v={version}", room, storedVersion(room)).exchange();

        assertThat(logo).hasStatusOk().hasContentType("image/png");
        assertThat(logo.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(logo).headers()
                .hasValue("Cache-Control", "max-age=31536000, public, immutable")
                .hasValue("X-Content-Type-Options", "nosniff");
        assertThat(logo.getResponse().getHeader("ETag")).matches("\"[0-9a-f]{32}\"");
    }

    @Test
    void withoutItsCurrentVersionTheLogoIsRevalidated() {
        long room = insertRoom("Winamax", "EUR");
        upload(room, "image/png", PNG);

        assertThat(mvc.get().uri("/rooms/{id}/logo", room)).hasStatusOk()
                .headers().hasValue("Cache-Control", "no-cache");
        assertThat(mvc.get().uri("/rooms/{id}/logo?v=old", room)).hasStatusOk()
                .headers().hasValue("Cache-Control", "no-cache");
    }

    @Test
    void answersNotModifiedWhenTheClientHasTheSameContent() {
        long room = insertRoom("Winamax", "EUR");
        upload(room, "image/png", PNG);
        String etag = mvc.get().uri("/rooms/{id}/logo", room).exchange().getResponse().getHeader("ETag");

        var notModified = mvc.get().uri("/rooms/{id}/logo", room).header("If-None-Match", etag).exchange();
        assertThat(notModified).hasStatus(HttpStatus.NOT_MODIFIED).headers().hasValue("ETag", etag);
        assertThat(notModified.getResponse().getContentAsByteArray()).isEmpty();

        assertThat(mvc.get().uri("/rooms/{id}/logo", room).header("If-None-Match", "\"another\"")).hasStatusOk();

        // The ETag follows the content: another image is served again, the same one is not.
        upload(room, "image/jpeg", JPEG);
        assertThat(mvc.get().uri("/rooms/{id}/logo", room).header("If-None-Match", etag)).hasStatusOk();
        upload(room, "image/png", PNG);
        assertThat(mvc.get().uri("/rooms/{id}/logo", room).header("If-None-Match", etag))
                .hasStatus(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void aMissingLogoIsNotFound() {
        long room = insertRoom("Winamax", "EUR");

        assertThat(mvc.get().uri("/rooms/{id}/logo", room)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(mvc.get().uri("/rooms/{id}/logo", 999_999).accept("image/*")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    // ---- delete ----

    @Test
    void deletesTheLogo() {
        long room = insertRoom("Winamax", "EUR");
        upload(room, "image/png", PNG);

        assertThat(mvc.delete().uri("/rooms/{id}/logo", room)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("select count(*) from room_logo", Integer.class)).isZero();
        assertThat(mvc.get().uri("/rooms/{id}/logo", room)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/rooms/{id}", room)).hasStatusOk()
                .bodyJson().extractingPath("$.logoVersion").isNull();
    }

    @Test
    void deletingAMissingLogoIsNotFound() {
        long room = insertRoom("Winamax", "EUR");

        assertThat(mvc.delete().uri("/rooms/{id}/logo", room)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(mvc.delete().uri("/rooms/{id}/logo", 999_999)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- rooms ----

    @Test
    void roomsTellTheVersionOfTheirLogo() {
        long winamax = insertRoom("Winamax", "EUR");
        insertRoom("888poker", "EUR");
        upload(winamax, "image/png", PNG);
        String version = storedVersion(winamax);

        var list = assertThat(mvc.get().uri("/rooms")).hasStatusOk().bodyJson();
        list.extractingPath("$[*].name").asArray().containsExactly("888poker", "Winamax");
        list.extractingPath("$[*].logoVersion").asArray().containsExactly(null, version);
        assertThat(mvc.get().uri("/rooms/{id}", winamax)).hasStatusOk()
                .bodyJson().extractingPath("$.logoVersion").isEqualTo(version);
        // Changing the room keeps its logo.
        assertThat(putJson("/rooms/" + winamax, """
                {"name": "Winamax.es", "currencyCode": "EUR"}""")).hasStatusOk()
                .bodyJson().extractingPath("$.logoVersion").isEqualTo(version);
    }

    @Test
    void aRoomThatOnlyHasALogoIsNotInUseAndItsLogoIsDeletedWithIt() {
        long room = insertRoom("Winamax", "EUR");

        assertThat(upload(room, "image/png", PNG)).hasStatusOk()
                .bodyJson().extractingPath("$.inUse").isEqualTo(false);
        assertThat(mvc.delete().uri("/rooms/{id}", room)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("select count(*) from room_logo", Integer.class)).isZero();
    }

    // ---- helpers ----

    private MvcTestResult upload(long room, String contentType, byte[] content) {
        return mvc.put().uri("/rooms/{id}/logo", room).contentType(contentType).content(content).exchange();
    }

    private String storedContentType(long room) {
        return jdbc.queryForObject("select content_type from room_logo where room_id = ?", String.class, room);
    }

    /** The version as the API gives it: the microseconds of the upload instant, in base 36. */
    private String storedVersion(long room) {
        long micros = jdbc.queryForObject(
                "select (extract(epoch from updated_at) * 1000000)::bigint from room_logo where room_id = ?",
                Long.class, room);
        return Long.toString(micros, 36);
    }

    /** {@code length} bytes starting with the given ones. */
    private static byte[] image(int length, int... start) {
        byte[] content = new byte[length];
        for (int i = 0; i < start.length; i++) {
            content[i] = (byte) start[i];
        }
        return content;
    }
}
