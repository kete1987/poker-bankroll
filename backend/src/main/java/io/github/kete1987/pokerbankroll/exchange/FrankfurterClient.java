package io.github.kete1987.pokerbankroll.exchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.annotation.PreDestroy;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the exchange rates of the European Central Bank from Frankfurter
 * (<a href="https://frankfurter.dev">frankfurter.dev</a>, free, no key): its v2 API, restricted to
 * the ECB as provider, so the rates are the official reference rates of each working day.
 *
 * <p>The address comes from the configuration only ({@code poker-bankroll.exchange-rates.url}),
 * never from a request.
 */
@Component
class FrankfurterClient {

    /** A year of rates of one currency is about 15 kB: this is decades. */
    static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final String EUR = "EUR";

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final String url;
    private final CloseableHttpClient client;

    FrankfurterClient(
            @Value("${poker-bankroll.exchange-rates.url:https://api.frankfurter.dev/v2}") String url,
            @Value("${poker-bankroll.exchange-rates.timeout:30s}") Duration timeout) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        Timeout wait = Timeout.ofMilliseconds(timeout.toMillis());
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(wait)
                                .setSocketTimeout(wait)
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(wait).build())
                .setUserAgent("poker-bankroll")
                .disableCookieManagement()
                .disableAuthCaching()
                .build();
    }

    @PreDestroy
    void close() throws IOException {
        client.close();
    }

    /** Where rates are downloaded from, as configured. */
    String url() {
        return url;
    }

    /**
     * The ECB rates of a currency, per 1 EUR, published between two days (both included). The
     * service may add the last one before {@code from}. A currency the ECB does not publish has
     * none.
     *
     * @throws DownloadException when they cannot be read: no connection, an error of the service,
     *                           an answer that is not what was expected
     */
    List<DownloadedRate> fetch(String currencyCode, LocalDate from, LocalDate to) {
        URI uri = URI.create(url + "/rates?base=" + EUR + "&quotes=" + currencyCode + "&from=" + from + "&to=" + to
                + "&providers=ecb");
        HttpGet request = new HttpGet(uri);
        request.setHeader("Accept", "application/json");
        try {
            return client.execute(request, response -> read(response, currencyCode));
        } catch (IOException ex) {
            throw new DownloadException(uri.getHost() + ": " + ex.getMessage(), ex);
        }
    }

    private static List<DownloadedRate> read(ClassicHttpResponse response, String currencyCode) throws IOException {
        HttpEntity body = response.getEntity();
        int status = response.getCode();
        // A currency the service does not know: there are no rates to download for it.
        if (status == 404 || status == 422) {
            return List.of();
        }
        if (status != 200 || body == null) {
            throw new DownloadException("HTTP " + status, null);
        }
        byte[] content = body.getContent().readNBytes(MAX_BYTES + 1);
        if (content.length > MAX_BYTES) {
            throw new DownloadException("the answer is larger than " + MAX_BYTES + " bytes", null);
        }
        List<@Nullable Row> rows;
        try {
            rows = Arrays.asList(JSON.readValue(content, Row[].class));
        } catch (JacksonException ex) {
            throw new DownloadException("the answer is not a list of rates", ex);
        }
        List<DownloadedRate> rates = new ArrayList<>();
        for (Row row : rows) {
            if (row != null && row.date() != null && row.rate() != null && row.rate().signum() > 0
                    && EUR.equals(row.base()) && currencyCode.equals(row.quote())) {
                rates.add(new DownloadedRate(row.date(), row.rate()));
            }
        }
        return rates;
    }

    /** One rate as the service sends it. */
    private record Row(@Nullable LocalDate date, @Nullable String base, @Nullable String quote,
            @Nullable BigDecimal rate) {
    }

    /** 1 EUR is worth {@code rate} units of the currency on that day. */
    record DownloadedRate(LocalDate date, BigDecimal rate) {
    }

    /** The rates could not be downloaded; the message says why, for the status. */
    static final class DownloadException extends RuntimeException {

        DownloadException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }
}
