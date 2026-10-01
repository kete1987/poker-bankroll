package io.github.kete1987.pokerbankroll.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.UnknownHostException;
import java.time.Duration;

import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

/** The deadline of a download also covers what happens before there is a connection. */
class RemoteImageFetcherDeadlineTests {

    @Test
    void givesUpWhenTheNameIsNotResolvedInTime() throws Exception {
        RemoteImageFetcher fetcher = new RemoteImageFetcher(false, Duration.ofMillis(500), host -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            throw new UnknownHostException(host);
        });
        try {
            long start = System.nanoTime();

            assertThatThrownBy(() -> fetcher.fetch("http://slow-name.example/logo.png"))
                    .isInstanceOfSatisfying(ApiException.class,
                            failure -> assertThat(failure.getCode()).isEqualTo(ErrorCode.LOGO_URL_UNREACHABLE));

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        } finally {
            fetcher.close();
        }
    }
}
