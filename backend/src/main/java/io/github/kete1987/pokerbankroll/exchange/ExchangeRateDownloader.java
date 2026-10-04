package io.github.kete1987.pokerbankroll.exchange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.annotation.PreDestroy;

import io.github.kete1987.pokerbankroll.exchange.FrankfurterClient.DownloadException;
import io.github.kete1987.pokerbankroll.exchange.FrankfurterClient.DownloadedRate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the downloaded exchange rates up to date: when the API starts, every day after the ECB
 * publishes (cron {@code poker-bankroll.exchange-rates.cron}), when other rates may be needed
 * ({@link ExchangeRatesNeeded}) and when the user asks for it.
 *
 * <p>Every currency in use but EUR (which is always 1), and the base currency, gets the rates
 * from the first day of any game or movement to today; only what is missing is asked for: the days
 * before the first rate downloaded and after the last one. Failures (no internet, the service
 * down) are logged and kept for the status, and never break anything: the rates there are keep
 * working. With {@code poker-bankroll.exchange-rates.enabled=false} nothing is downloaded.
 */
@Component
public class ExchangeRateDownloader {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateDownloader.class);
    private static final String EUR = "EUR";

    private final boolean enabled;
    private final FrankfurterClient client;
    private final ExchangeRates exchangeRates;
    private final JdbcClient jdbc;
    /** One download at a time, whoever asks for it. */
    private final ReentrantLock lock = new ReentrantLock();
    /** Downloads asked for in the background run here, one after another. */
    private final ExecutorService background = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("exchange-rates-", 0).factory());

    private volatile boolean running;
    private volatile @Nullable Instant lastAttemptAt;
    private volatile @Nullable Instant lastSuccessAt;
    private volatile @Nullable String lastError;

    ExchangeRateDownloader(@Value("${poker-bankroll.exchange-rates.enabled:true}") boolean enabled,
            FrankfurterClient client, ExchangeRates exchangeRates, JdbcClient jdbc) {
        this.enabled = enabled;
        this.client = client;
        this.exchangeRates = exchangeRates;
        this.jdbc = jdbc;
    }

    @PreDestroy
    void stop() {
        background.shutdownNow();
    }

    /** Whether rates are downloaded at all. */
    public boolean isEnabled() {
        return enabled;
    }

    /** On start, in the background: the API is ready meanwhile, and starts even without internet. */
    @EventListener(ApplicationReadyEvent.class)
    void onStart() {
        downloadSoon();
    }

    /** Every day, after the ECB publishes the rates of the day (around 16:00 CET). */
    @Scheduled(cron = "${poker-bankroll.exchange-rates.cron:0 0 17 * * *}",
            zone = "${poker-bankroll.exchange-rates.zone:Europe/Madrid}")
    void daily() {
        if (enabled) {
            download();
        }
    }

    /** Once what needs other rates is committed (or right away, outside a transaction). */
    @TransactionalEventListener(fallbackExecution = true)
    void on(ExchangeRatesNeeded event) {
        downloadSoon();
    }

    /** Downloads what is missing in the background; nothing when downloads are off. */
    public void downloadSoon() {
        if (enabled) {
            background.submit(this::download);
        }
    }

    /** Waits for the downloads asked for in the background so far (for the tests). */
    void awaitBackground() throws Exception {
        background.submit(() -> { }).get();
    }

    /** Downloads what is missing now, waiting for a download already running. */
    void download() {
        lock.lock();
        try {
            running = true;
            lastAttemptAt = Instant.now();
            List<String> failures = downloadMissing();
            if (failures.isEmpty()) {
                lastSuccessAt = Instant.now();
                lastError = null;
            } else {
                lastError = String.join("; ", failures);
            }
        } catch (RuntimeException ex) {
            // Never out of here: a failure must not stop the scheduler or the request that asked.
            log.warn("Exchange rates could not be downloaded", ex);
            lastError = ex.toString();
        } finally {
            running = false;
            lock.unlock();
        }
    }

    /** Asks for the missing rates of every currency that needs them; returns what failed. */
    private List<String> downloadMissing() {
        LocalDate today = LocalDate.now();
        LocalDate first = jdbc.sql("""
                select least((select min(played_on) from game), (select min(occurred_on) from bankroll_movement))
                """).query(LocalDate.class).optional().orElse(null);
        if (first == null) {
            return List.of();
        }
        if (first.isAfter(today)) {
            first = today;
        }
        Set<String> currencies = new TreeSet<>(jdbc.sql("""
                select r.currency_code from room r
                where exists (select 1 from game g where g.room_id = r.id)
                   or exists (select 1 from bankroll_movement m where m.room_id = r.id)
                union
                select currency_code from bankroll_movement where currency_code is not null
                """).query(String.class).list());
        currencies.add(exchangeRates.baseCurrency());
        currencies.remove(EUR);

        Map<String, LocalDate[]> downloaded = new HashMap<>();
        jdbc.sql("""
                select currency_code, min(rate_date) as first_day, max(rate_date) as last_day
                from exchange_rate where source = 'ECB' group by currency_code
                """).query((row, number) -> downloaded.put(row.getString("currency_code"), new LocalDate[] {
                        row.getObject("first_day", LocalDate.class), row.getObject("last_day", LocalDate.class)}))
                .list();

        List<String> failures = new ArrayList<>();
        for (String currency : currencies) {
            LocalDate[] have = downloaded.get(currency);
            List<LocalDate[]> wanted = new ArrayList<>();
            if (have == null) {
                wanted.add(new LocalDate[] {first, today});
            } else {
                if (have[0].isAfter(first)) {
                    wanted.add(new LocalDate[] {first, have[0].minusDays(1)});
                }
                if (have[1].isBefore(today)) {
                    wanted.add(new LocalDate[] {have[1].plusDays(1), today});
                }
            }
            for (LocalDate[] span : wanted) {
                try {
                    List<DownloadedRate> rates = client.fetch(currency, span[0], span[1]);
                    save(currency, rates);
                    log.info("Exchange rates of {} from {} to {}: {} downloaded", currency, span[0], span[1],
                            rates.size());
                } catch (DownloadException ex) {
                    log.warn("Exchange rates of {} from {} to {} could not be downloaded: {}", currency, span[0],
                            span[1], ex.getMessage());
                    failures.add(currency + ": " + ex.getMessage());
                }
            }
        }
        return failures;
    }

    private void save(String currency, List<DownloadedRate> rates) {
        for (DownloadedRate rate : rates) {
            jdbc.sql("""
                    insert into exchange_rate (currency_code, rate_date, source, rate)
                    values (:currency, :day, 'ECB', :rate)
                    on conflict (currency_code, rate_date, source)
                    do update set rate = excluded.rate, updated_at = now()
                    where exchange_rate.rate <> excluded.rate
                    """)
                    .param("currency", currency)
                    .param("day", rate.date())
                    .param("rate", rate.rate())
                    .update();
        }
    }

    /** How the downloads went, since the API started. */
    Status status() {
        return new Status(running, lastAttemptAt, lastSuccessAt, lastError);
    }

    record Status(boolean running, @Nullable Instant lastAttemptAt, @Nullable Instant lastSuccessAt,
            @Nullable String lastError) {
    }
}
