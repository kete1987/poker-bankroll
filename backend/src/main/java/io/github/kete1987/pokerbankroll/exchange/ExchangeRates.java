package io.github.kete1987.pokerbankroll.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The base currency and the exchange rates, as the figures that add up several currencies need
 * them: which currency they are shown in, and a {@link Converter} with the rates of the days they
 * are about.
 */
@Service
@Transactional(readOnly = true)
public class ExchangeRates {

    /** When there is nothing to choose from. */
    static final String DEFAULT_CURRENCY = "EUR";

    private final JdbcClient jdbc;

    ExchangeRates(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The currency chosen by the user, or {@code null} when it is automatic. */
    public @Nullable String chosenBaseCurrency() {
        return jdbc.sql("select base_currency_code from currency_setting")
                .query(String.class).optional().orElse(null);
    }

    /**
     * The base currency when none is chosen: the one with most games; without games, the first
     * (alphabetically) of the rooms and movements; without anything, EUR.
     */
    public String automaticBaseCurrency() {
        return jdbc.sql("""
                select currency_code from (
                    select r.currency_code, count(g.id) as games
                    from room r left join game g on g.room_id = r.id
                    group by r.currency_code
                    union all
                    select currency_code, 0 from bankroll_movement where currency_code is not null
                ) in_use
                group by currency_code
                order by sum(games) desc, currency_code
                limit 1
                """).query(String.class).optional().orElse(DEFAULT_CURRENCY);
    }

    /** The currency amounts of several currencies are converted to: the chosen one, or the automatic one. */
    public String baseCurrency() {
        String chosen = chosenBaseCurrency();
        return chosen != null ? chosen : automaticBaseCurrency();
    }

    /**
     * A converter to the base currency with the rates the given currencies need between two days
     * (both included): every rate in the span and the last one before it.
     *
     * @param first first day of an amount to convert; {@code null} when there is none
     */
    public Converter converter(String baseCurrency, Collection<String> currencies, @Nullable LocalDate first,
            @Nullable LocalDate last) {
        Set<String> needed = new TreeSet<>(currencies);
        needed.add(baseCurrency);
        needed.remove(DEFAULT_CURRENCY);
        Map<String, NavigableMap<LocalDate, BigDecimal>> rates = new HashMap<>();
        if (first != null && last != null && !needed.isEmpty()) {
            // Ordered so that a manual rate comes after the downloaded one of its day, and wins.
            jdbc.sql("""
                    select currency_code, rate_date, rate
                    from exchange_rate e
                    where currency_code in (:currencies)
                      and rate_date <= :last
                      and rate_date >= coalesce(
                          (select max(rate_date) from exchange_rate p
                           where p.currency_code = e.currency_code and p.rate_date <= :first),
                          :first)
                    order by currency_code, rate_date, case source when 'MANUAL' then 1 else 0 end
                    """)
                    .param("currencies", List.copyOf(needed))
                    .param("first", first)
                    .param("last", last)
                    .query((row, number) -> rates
                            .computeIfAbsent(row.getString("currency_code"), code -> new TreeMap<>())
                            .put(row.getObject("rate_date", LocalDate.class), row.getBigDecimal("rate")))
                    .list();
        }
        return new Converter(baseCurrency, rates);
    }
}
