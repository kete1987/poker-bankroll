package io.github.kete1987.pokerbankroll.exchange;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.github.kete1987.pokerbankroll.catalog.CurrencyRepository;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.github.kete1987.pokerbankroll.exchange.ExchangeRateStatusResponse.CurrencyRates;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The base currency, the rates typed by hand and the state of the downloaded ones. */
@Service
@Transactional
public class ExchangeRateService {

    private static final String EUR = "EUR";

    private final ExchangeRates exchangeRates;
    private final ExchangeRateDownloader downloader;
    private final CurrencyRepository currencies;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    ExchangeRateService(ExchangeRates exchangeRates, ExchangeRateDownloader downloader,
            CurrencyRepository currencies, JdbcClient jdbc, ApplicationEventPublisher events) {
        this.exchangeRates = exchangeRates;
        this.downloader = downloader;
        this.currencies = currencies;
        this.jdbc = jdbc;
        this.events = events;
    }

    // ---- base currency ----

    @Transactional(readOnly = true)
    public CurrencySettingsResponse settings() {
        String chosen = exchangeRates.chosenBaseCurrency();
        String automatic = exchangeRates.automaticBaseCurrency();
        return new CurrencySettingsResponse(chosen, automatic, chosen != null ? chosen : automatic);
    }

    /** Chooses the base currency, or leaves it automatic; the rates it needs are downloaded afterwards. */
    public CurrencySettingsResponse updateSettings(CurrencySettingsRequest request) {
        String code = request.baseCurrencyCode() == null || request.baseCurrencyCode().isBlank()
                ? null
                : request.baseCurrencyCode().strip().toUpperCase(Locale.ROOT);
        if (code != null && !currencies.existsById(code)) {
            throw new ApiException(ErrorCode.UNKNOWN_CURRENCY, code);
        }
        String before = exchangeRates.baseCurrency();
        jdbc.sql("update currency_setting set base_currency_code = :code, updated_at = now()")
                .param("code", code)
                .update();
        if (!before.equals(exchangeRates.baseCurrency())) {
            events.publishEvent(new ExchangeRatesNeeded());
        }
        return settings();
    }

    // ---- manual rates ----

    /** Every rate typed by hand, newest first. */
    @Transactional(readOnly = true)
    public List<ManualRateResponse> manualRates() {
        return jdbc.sql("""
                select m.currency_code, m.rate_date, m.rate, e.rate as downloaded_rate
                from exchange_rate m
                left join exchange_rate e on e.currency_code = m.currency_code and e.rate_date = m.rate_date
                    and e.source = 'ECB'
                where m.source = 'MANUAL'
                order by m.rate_date desc, m.currency_code
                """).query((row, number) -> new ManualRateResponse(row.getString("currency_code"),
                        row.getObject("rate_date", LocalDate.class), row.getBigDecimal("rate"),
                        row.getBigDecimal("downloaded_rate")))
                .list();
    }

    /** Records the rate of a currency on a day, replacing the one typed before for that day. */
    public ManualRateResponse saveManualRate(String currencyCode, LocalDate date, ManualRateRequest request) {
        String code = currencyOfRate(currencyCode);
        jdbc.sql("""
                insert into exchange_rate (currency_code, rate_date, source, rate)
                values (:currency, :day, 'MANUAL', :rate)
                on conflict (currency_code, rate_date, source) do update set rate = excluded.rate, updated_at = now()
                """)
                .param("currency", code)
                .param("day", date)
                .param("rate", request.rate())
                .update();
        return manualRates().stream()
                .filter(rate -> rate.currencyCode().equals(code) && rate.date().equals(date))
                .findFirst().orElseThrow();
    }

    public void deleteManualRate(String currencyCode, LocalDate date) {
        int deleted = jdbc.sql("""
                delete from exchange_rate where currency_code = :currency and rate_date = :day and source = 'MANUAL'
                """)
                .param("currency", currencyCode.strip().toUpperCase(Locale.ROOT))
                .param("day", date)
                .update();
        if (deleted == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    private String currencyOfRate(String currencyCode) {
        String code = currencyCode.strip().toUpperCase(Locale.ROOT);
        if (code.equals(EUR)) {
            throw new ApiException(ErrorCode.EXCHANGE_RATE_OF_EUR);
        }
        if (!currencies.existsById(code)) {
            throw new ApiException(ErrorCode.UNKNOWN_CURRENCY, code);
        }
        return code;
    }

    // ---- downloads ----

    /** Downloads the missing rates now and says how it went. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public ExchangeRateStatusResponse refresh() {
        if (!downloader.isEnabled()) {
            throw new ApiException(ErrorCode.EXCHANGE_RATES_DISABLED);
        }
        downloader.download();
        return status();
    }

    @Transactional(readOnly = true)
    public ExchangeRateStatusResponse status() {
        String base = exchangeRates.baseCurrency();
        // The first day of the amounts of each currency in use.
        Map<String, LocalDate> firstDays = new HashMap<>();
        jdbc.sql("""
                select currency_code, min(day) as first_day from (
                    select r.currency_code, g.played_on as day from game g join room r on r.id = g.room_id
                    union all
                    select coalesce(r.currency_code, m.currency_code), m.occurred_on
                    from bankroll_movement m left join room r on r.id = m.room_id
                ) amounts group by currency_code
                """).query((row, number) -> firstDays.put(row.getString("currency_code"),
                        row.getObject("first_day", LocalDate.class)))
                .list();

        Set<String> needed = new TreeSet<>(firstDays.keySet());
        needed.add(base);
        needed.remove(EUR);
        Map<String, CurrencyRates> known = new HashMap<>();
        jdbc.sql("""
                select currency_code, min(rate_date) as first_day, max(rate_date) as last_day,
                       count(*) filter (where source = 'MANUAL') as manual
                from exchange_rate group by currency_code
                """).query((row, number) -> known.put(row.getString("currency_code"), new CurrencyRates(
                        row.getString("currency_code"), row.getObject("first_day", LocalDate.class),
                        row.getObject("last_day", LocalDate.class), null, row.getInt("manual"))))
                .list();

        List<CurrencyRates> rates = new ArrayList<>();
        for (String currency : needed) {
            CurrencyRates have = known.get(currency);
            rates.add(new CurrencyRates(currency, have == null ? null : have.firstRateOn(),
                    have == null ? null : have.lastRateOn(), neededFrom(currency, base, firstDays),
                    have == null ? 0 : have.manualRates()));
        }
        ExchangeRateDownloader.Status download = downloader.status();
        return new ExchangeRateStatusResponse(downloader.isEnabled(), download.running(), download.lastAttemptAt(),
                download.lastSuccessAt(), download.lastError(), base, rates);
    }

    /**
     * The first amount that needs the rate of a currency: its own amounts, converted to the base;
     * for the base currency, the amounts of every other one.
     */
    private static @Nullable LocalDate neededFrom(String currency, String base, Map<String, LocalDate> firstDays) {
        if (!currency.equals(base)) {
            return firstDays.get(currency);
        }
        return firstDays.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(base))
                .map(Map.Entry::getValue)
                .min(LocalDate::compareTo)
                .orElse(null);
    }
}
