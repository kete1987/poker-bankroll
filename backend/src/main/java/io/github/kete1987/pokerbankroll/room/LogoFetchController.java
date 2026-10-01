package io.github.kete1987.pokerbankroll.room;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.github.kete1987.pokerbankroll.room.RemoteImageFetcher.FetchedImage;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms/logo-fetch")
@Tag(name = "Rooms")
class LogoFetchController {

    private final RemoteImageFetcher fetcher;

    LogoFetchController(RemoteImageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @PostMapping
    @Operation(summary = "Download the image of a URL, to use it as the logo of a room",
            description = """
                    Returns the image itself (PNG, JPEG or WebP, up to 5 MB). A browser cannot read \
                    images of other sites, so it asks for them here, resizes the result and uploads it \
                    with `PUT /rooms/{id}/logo`; nothing is stored by this request. Only `http` and \
                    `https` URLs of public addresses. Fails with `LOGO_URL_INVALID` (400), \
                    `LOGO_URL_NOT_PUBLIC` (400), `LOGO_URL_UNREACHABLE` (502), `LOGO_TOO_LARGE` (413) \
                    or `LOGO_UNSUPPORTED_TYPE` (415).""")
    @ApiResponse(responseCode = "200", description = "OK", content = {
        @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
        @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
        @Content(mediaType = "image/webp", schema = @Schema(type = "string", format = "binary"))})
    ResponseEntity<byte[]> fetchLogo(@Valid @RequestBody LogoFetchRequest request) {
        FetchedImage image = fetcher.fetch(request.url());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                // The content type is the one found in the bytes: browsers must not guess another.
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }

    /** The address of the image to download. */
    record LogoFetchRequest(@NotBlank @Size(max = 2000) String url) {
    }
}
