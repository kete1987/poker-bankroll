package io.github.kete1987.pokerbankroll.stats;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.exchange.MissingExchangeRate;

/**
 * Amounts in different currencies are never added up as they are: one summary per currency, and
 * every currency converted to the base currency, explicitly, in {@code converted}.
 */
public record StatsSummaryResponse(
        @Schema(description = "One summary per currency, in its own money, by code")
        List<CurrencySummary> currencies,
        @Schema(description = "Every game of the selection with its amounts converted to the base currency, each "
                + "with the rates of the day it was played. When the selection is in a single currency, show that "
                + "currency instead")
        ConvertedSummary converted) {

    public record CurrencySummary(
            String currencyCode,
            @Schema(description = "Every finished game in this currency")
            StatsFigures total,
            @Schema(description = "The game types with finished games, in catalog order")
            List<GameTypeSummary> byGameType,
            InPlay inPlay) {
    }

    /** The summary of every currency together, converted. */
    public record ConvertedSummary(
            @Schema(description = "The base currency the amounts are converted to")
            String currencyCode,
            @Schema(description = "Every finished game that could be converted")
            StatsFigures total,
            @Schema(description = "The game types with finished games, in catalog order")
            List<GameTypeSummary> byGameType,
            InPlay inPlay,
            @Schema(description = "Games left out of these figures for lack of an exchange rate")
            List<MissingExchangeRate> missingRates) {
    }

    public record GameTypeSummary(GameType gameType, StatsFigures figures) {
    }

    /** Games without a result yet: they are in none of the figures. */
    public record InPlay(
            long games,
            @Schema(description = "Money paid so far for their entries")
            BigDecimal invested) {
    }
}
