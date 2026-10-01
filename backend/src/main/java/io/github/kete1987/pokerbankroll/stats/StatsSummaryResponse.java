package io.github.kete1987.pokerbankroll.stats;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.catalog.GameType;

/** Amounts in different currencies are never added up: one summary per currency. */
public record StatsSummaryResponse(List<CurrencySummary> currencies) {

    public record CurrencySummary(
            String currencyCode,
            @Schema(description = "Every finished game in this currency")
            StatsFigures total,
            @Schema(description = "The game types with finished games, in catalog order")
            List<GameTypeSummary> byGameType,
            InPlay inPlay) {
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
