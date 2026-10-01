package io.github.kete1987.pokerbankroll.room;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Downloads an image from a URL given by the user, so the browser (which cannot read images of
 * other sites) can use it as the logo of a room.
 *
 * <p>Only {@code http}/{@code https} and only public addresses. The HTTP client asks this class
 * for the addresses of every host it connects to, redirects included, and connects to what it is
 * given: the addresses that are checked are the ones that are used, so a host name cannot answer
 * one thing for the check and another for the connection. There is a size limit, and a deadline
 * for the whole download, whatever the pace of the other server.
 */
@Component
class RemoteImageFetcher {

    /** Largest image downloaded: the browser shrinks it before it is stored. */
    static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final int MAX_REDIRECTS = 5;
    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(5);
    /** Longest silence of the other server that is waited for. */
    private static final Timeout IDLE_TIMEOUT = Timeout.ofSeconds(10);

    private final boolean allowPrivateAddresses;
    private final Duration deadline;
    private final CloseableHttpClient client;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "logo-fetch-deadline");
        thread.setDaemon(true);
        return thread;
    });

    RemoteImageFetcher(
            @Value("${poker-bankroll.logo-fetch.allow-private-addresses:false}") boolean allowPrivateAddresses,
            @Value("${poker-bankroll.logo-fetch.timeout:20s}") Duration deadline) {
        this.allowPrivateAddresses = allowPrivateAddresses;
        this.deadline = deadline;
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new CheckedResolver())
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(CONNECT_TIMEOUT)
                                .setSocketTimeout(IDLE_TIMEOUT)
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setMaxRedirects(MAX_REDIRECTS)
                        .setResponseTimeout(IDLE_TIMEOUT)
                        .build())
                .setUserAgent("poker-bankroll")
                // One attempt, and nothing remembered between downloads.
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                .build();
    }

    @PreDestroy
    void close() throws IOException {
        watchdog.shutdownNow();
        client.close();
    }

    /** The image at the URL: its bytes and the format found in them. */
    FetchedImage fetch(String url) {
        HttpGet request = new HttpGet(parse(url));
        request.setHeader("Accept", "image/png, image/jpeg, image/webp");
        // The timeouts of the client only limit each wait: a server that keeps sending a byte now
        // and then would never trip them. This ends the download, however far it got.
        ScheduledFuture<?> tooLong = watchdog.schedule(request::cancel, deadline.toMillis(), TimeUnit.MILLISECONDS);
        try {
            return client.execute(request, RemoteImageFetcher::read);
        } catch (IOException ex) {
            throw new ApiException(isRefusedAddress(ex)
                    ? ErrorCode.LOGO_URL_NOT_PUBLIC
                    : ErrorCode.LOGO_URL_UNREACHABLE);
        } finally {
            tooLong.cancel(false);
        }
    }

    private static FetchedImage read(ClassicHttpResponse response) throws IOException {
        HttpEntity body = response.getEntity();
        if (response.getCode() != 200 || body == null) {
            throw new ApiException(ErrorCode.LOGO_URL_UNREACHABLE);
        }
        // Never more than one byte over the limit in memory, however large the image is.
        byte[] content = body.getContent().readNBytes(MAX_BYTES + 1);
        if (content.length > MAX_BYTES) {
            throw new ApiException(ErrorCode.LOGO_TOO_LARGE, MAX_BYTES / 1024);
        }
        LogoImageType type = LogoImageType.detect(content)
                .orElseThrow(() -> new ApiException(ErrorCode.LOGO_UNSUPPORTED_TYPE));
        return new FetchedImage(content, type.contentType());
    }

    private static URI parse(String url) {
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException ex) {
            throw new ApiException(ErrorCode.LOGO_URL_INVALID);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        // No credentials in the address: they would be sent to wherever it points.
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new ApiException(ErrorCode.LOGO_URL_INVALID);
        }
        return uri;
    }

    private static boolean isRefusedAddress(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RefusedAddressException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves host names for the HTTP client, which connects to the addresses returned here and
     * to no other. A host with any address that is not public is refused as a whole.
     */
    private final class CheckedResolver implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (!allowPrivateAddresses) {
                for (InetAddress address : addresses) {
                    if (!PublicAddress.isPublic(address)) {
                        throw new RefusedAddressException(host);
                    }
                }
            }
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            return InetAddress.getByName(host).getCanonicalHostName();
        }
    }

    /** The host resolves to this machine or to a private network. */
    private static final class RefusedAddressException extends UnknownHostException {

        RefusedAddressException(String host) {
            super(host);
        }
    }

    record FetchedImage(byte[] content, String contentType) {
    }
}
