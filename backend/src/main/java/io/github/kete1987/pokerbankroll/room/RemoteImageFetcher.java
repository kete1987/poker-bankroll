package io.github.kete1987.pokerbankroll.room;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Downloads an image from a URL given by the user, so the browser (which cannot read images of
 * other sites) can use it as the logo of a room.
 *
 * <p>Only {@code http}/{@code https}, only public addresses (checked for every redirect, which
 * are followed here and not by the HTTP client), a size limit and timeouts. The check resolves
 * the host name before connecting; a name that changes its answer in between could still get
 * through, which is accepted for an application meant for a trusted network.
 */
@Component
class RemoteImageFetcher {

    /** Largest image downloaded: the browser shrinks it before it is stored. */
    static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final int MAX_REDIRECTS = 5;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final boolean allowPrivateAddresses;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    RemoteImageFetcher(
            @Value("${poker-bankroll.logo-fetch.allow-private-addresses:false}") boolean allowPrivateAddresses) {
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    /** The image at the URL: its bytes and the format found in them. */
    FetchedImage fetch(String url) {
        URI uri = parse(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkIsAllowed(uri);
            HttpResponse<InputStream> response = get(uri);
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                Optional<String> location = response.headers().firstValue("Location");
                if (status >= 300 && status < 400 && location.isPresent()) {
                    uri = redirected(uri, location.get());
                    continue;
                }
                if (status != 200) {
                    throw new ApiException(ErrorCode.LOGO_URL_UNREACHABLE);
                }
                // Never more than one byte over the limit in memory, however large the image is.
                byte[] content = body.readNBytes(MAX_BYTES + 1);
                if (content.length > MAX_BYTES) {
                    throw new ApiException(ErrorCode.LOGO_TOO_LARGE, MAX_BYTES / 1024);
                }
                LogoImageType type = LogoImageType.detect(content)
                        .orElseThrow(() -> new ApiException(ErrorCode.LOGO_UNSUPPORTED_TYPE));
                return new FetchedImage(content, type.contentType());
            } catch (IOException ex) {
                throw new ApiException(ErrorCode.LOGO_URL_UNREACHABLE);
            }
        }
        throw new ApiException(ErrorCode.LOGO_URL_UNREACHABLE);
    }

    private static URI parse(String url) {
        try {
            return checkShape(new URI(url.strip()));
        } catch (URISyntaxException ex) {
            throw new ApiException(ErrorCode.LOGO_URL_INVALID);
        }
    }

    private static URI redirected(URI from, String location) {
        try {
            return checkShape(from.resolve(new URI(location.strip())));
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.LOGO_URL_UNREACHABLE);
        }
    }

    private static URI checkShape(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        // No credentials in the address: they would be sent to wherever it points.
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new ApiException(ErrorCode.LOGO_URL_INVALID);
        }
        return uri;
    }

    /** Every address the host name resolves to must be public. */
    private void checkIsAllowed(URI uri) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException ex) {
            throw new ApiException(ErrorCode.LOGO_URL_UNREACHABLE);
        }
        if (allowPrivateAddresses) {
            return;
        }
        for (InetAddress address : addresses) {
            if (!PublicAddress.isPublic(address)) {
                throw new ApiException(ErrorCode.LOGO_URL_NOT_PUBLIC);
            }
        }
    }

    private HttpResponse<InputStream> get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "image/png, image/jpeg, image/webp")
                .header("User-Agent", "poker-bankroll")
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.LOGO_URL_UNREACHABLE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.LOGO_URL_UNREACHABLE);
        }
    }

    record FetchedImage(byte[] content, String contentType) {
    }
}
