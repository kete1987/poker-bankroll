package io.github.kete1987.pokerbankroll.exchange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/** Which exchange rates there are, and how their download went. */
public record ExchangeRateStatusResponse(
        @Schema(description = "Rates are downloaded (on start, every day and when asked for); false when the "
                + "installation turned it off, and then only manual rates exist")
        boolean enabled,
        @Schema(description = "A download is running now")
        boolean running,
        @Schema(description = "When the last download started, since the API started")
        @Nullable Instant lastAttemptAt,
        @Schema(description = "When the last download that got every currency it asked for ended, since the API started")
        @Nullable Instant lastSuccessAt,
        @Schema(description = "What went wrong in the last download; null when it went well. Technical text, not "
                + "translated")
        @Nullable String lastError,
        @Schema(description = "The base currency in use")
        String baseCurrencyCode,
        @Schema(description = "The currencies that need rates (the base one and those of games and movements, but "
                + "EUR, which is always 1), by code")
        List<CurrencyRates> currencies) {

    public record CurrencyRates(
            String currencyCode,
            @Schema(description = "First day with a rate, downloaded or manual")
            @Nullable LocalDate firstRateOn,
            @Schema(description = "Last day with a rate, downloaded or manual")
            @Nullable LocalDate lastRateOn,
            @Schema(description = "First day of an amount converted with this currency's rate: amounts before "
                    + "`firstRateOn` cannot be converted. Null when no amount needs it with this base currency")
            @Nullable LocalDate neededFrom,
            @Schema(description = "Rates typed by hand")
            int manualRates) {
    }
}
