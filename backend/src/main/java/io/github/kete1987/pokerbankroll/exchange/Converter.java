package io.github.kete1987.pokerbankroll.exchange;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/**
 * Converts amounts of several currencies to one (the base currency) with the rates of their day,
 * and remembers what it could not convert. Made by {@link ExchangeRates#converter} with the rates
 * of a span of days already loaded; one per request, not shared between threads.
 *
 * <p>Rates are per 1 EUR: converting X to the base B is {@code amount / rate(X) * rate(B)}, with
 * the last rate on or before the day of the amount (a manual one over the downloaded one of the
 * same day). An amount already in the base currency needs no rate.
 */
public final class Converter {

    /** Enough digits for any rate and amount; the figures are rounded to cents at the end. */
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final String EUR = "EUR";

    private final String currencyCode;
    private final Map<String, NavigableMap<LocalDate, BigDecimal>> rates;
    private final Map<String, Map<LocalDate, @Nullable BigDecimal>> factors = new HashMap<>();
    /** Per currency without a rate, the first and last day that needed it. */
    private final Map<String, LocalDate[]> missing = new TreeMap<>();

    Converter(String currencyCode, Map<String, NavigableMap<LocalDate, BigDecimal>> rates) {
        this.currencyCode = currencyCode;
        this.rates = rates;
    }

    /** The currency everything is converted to. */
    public String currencyCode() {
        return currencyCode;
    }

    /**
     * What an amount of a currency on a day is multiplied by to have it in the base currency, or
     * {@code null} when a rate is missing (then it is remembered, see {@link #missing()}).
     *
     * @param day the day of the amount; only {@code null} for an amount in the base currency
     */
    public @Nullable BigDecimal factor(String currency, @Nullable LocalDate day) {
        if (currency.equals(currencyCode)) {
            return BigDecimal.ONE;
        }
        if (day == null) {
            throw new IllegalArgumentException("An amount in " + currency + " needs its day to be converted");
        }
        Map<LocalDate, @Nullable BigDecimal> ofCurrency = factors.computeIfAbsent(currency, code -> new HashMap<>());
        if (ofCurrency.containsKey(day)) {
            BigDecimal known = ofCurrency.get(day);
            if (known == null) {
                remember(currency, day);
            }
            return known;
        }
        BigDecimal from = rateOf(currency, day);
        BigDecimal to = rateOf(currencyCode, day);
        BigDecimal factor = from == null || to == null ? null : to.divide(from, PRECISION);
        ofCurrency.put(day, factor);
        if (factor == null) {
            remember(currency, day);
        }
        return factor;
    }

    /** The amount in the base currency, or {@code null} when a rate is missing. */
    public @Nullable BigDecimal convert(BigDecimal amount, String currency, @Nullable LocalDate day) {
        BigDecimal factor = factor(currency, day);
        return factor == null ? null : amount.multiply(factor, PRECISION);
    }

    /** The amount in the base currency, or zero (and remembered) when a rate is missing. */
    public BigDecimal convertOrZero(BigDecimal amount, String currency, @Nullable LocalDate day) {
        BigDecimal converted = convert(amount, currency, day);
        return converted == null ? BigDecimal.ZERO : converted;
    }

    /** What could not be converted so far, by currency. */
    public List<MissingExchangeRate> missing() {
        List<MissingExchangeRate> result = new ArrayList<>();
        missing.forEach((code, days) -> result.add(new MissingExchangeRate(code, days[0], days[1])));
        return result;
    }

    private @Nullable BigDecimal rateOf(String currency, LocalDate day) {
        if (currency.equals(EUR)) {
            return BigDecimal.ONE;
        }
        NavigableMap<LocalDate, BigDecimal> ofCurrency = rates.get(currency);
        Map.Entry<LocalDate, BigDecimal> rate = ofCurrency == null ? null : ofCurrency.floorEntry(day);
        return rate == null ? null : rate.getValue();
    }

    /** The currency that lacks a rate on a day: the one of the amount, the base one, or both. */
    private void remember(String currency, LocalDate day) {
        if (rateOf(currency, day) == null) {
            widen(currency, day);
        }
        if (rateOf(currencyCode, day) == null) {
            widen(currencyCode, day);
        }
    }

    private void widen(String currency, LocalDate day) {
        LocalDate[] days = missing.computeIfAbsent(currency, code -> new LocalDate[] {day, day});
        if (day.isBefore(days[0])) {
            days[0] = day;
        }
        if (day.isAfter(days[1])) {
            days[1] = day;
        }
    }
}
