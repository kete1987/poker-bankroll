package io.github.kete1987.pokerbankroll.room;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms/{id}/logo")
@Tag(name = "Rooms")
// Method names are the operation ids of the contract: unique ones keep the ids of other endpoints.
class RoomLogoController {

    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";
    private static final String WEBP = "image/webp";

    private final RoomLogoService service;

    RoomLogoController(RoomLogoService service) {
        this.service = service;
    }

    @PutMapping
    @Operation(summary = "Set the logo of a room",
            description = """
                    The body is the image itself: PNG, JPEG or WebP, up to 256 kB. The format is detected \
                    from the content and the declared `Content-Type` is ignored. Replaces the logo the room \
                    had and returns the room with its new `logoVersion`. Fails with `LOGO_EMPTY` (400), \
                    `LOGO_TOO_LARGE` (413) or `LOGO_UNSUPPORTED_TYPE` (415).""",
            requestBody = @RequestBody(required = true, content = {
                @Content(mediaType = PNG, schema = @Schema(type = "string", format = "binary")),
                @Content(mediaType = JPEG, schema = @Schema(type = "string", format = "binary")),
                @Content(mediaType = WEBP, schema = @Schema(type = "string", format = "binary"))}))
    RoomResponse replaceLogo(@PathVariable long id, @Parameter(hidden = true) InputStream body) throws IOException {
        // Never more than one byte over the limit in memory, however large the body is.
        return service.replace(id, body.readNBytes(RoomLogoService.MAX_BYTES + 1));
    }

    @GetMapping
    @Operation(summary = "Get the logo of a room",
            description = """
                    The image, with a strong `ETag` (`If-None-Match` is answered with 304). Request it as \
                    `?v=<logoVersion of the room>`: that URL never changes its content, so it is served to \
                    be cached for a year without revalidation. Without `v`, or with another value, it is \
                    revalidated on every use.""")
    @ApiResponse(responseCode = "200", description = "OK", content = {
        @Content(mediaType = PNG, schema = @Schema(type = "string", format = "binary")),
        @Content(mediaType = JPEG, schema = @Schema(type = "string", format = "binary")),
        @Content(mediaType = WEBP, schema = @Schema(type = "string", format = "binary"))})
    @ApiResponse(responseCode = "304", description = "Not modified", content = @Content)
    ResponseEntity<byte[]> getLogo(@PathVariable long id,
            @Parameter(description = "The `logoVersion` of the room")
            @RequestParam(required = false) @Nullable String v) {
        RoomLogoImage logo = service.get(id);
        CacheControl cacheControl = logo.version().equals(v)
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noCache();
        // Spring answers 304 without the body when If-None-Match has this ETag.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .eTag("\"" + logo.contentHash() + "\"")
                .cacheControl(cacheControl)
                // The content type is the one detected on upload: browsers must not guess another.
                .header("X-Content-Type-Options", "nosniff")
                .body(logo.content());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete the logo of a room")
    void deleteLogo(@PathVariable long id) {
        service.delete(id);
    }
}
