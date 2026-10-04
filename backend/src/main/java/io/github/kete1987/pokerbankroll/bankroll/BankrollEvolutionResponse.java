package io.github.kete1987.pokerbankroll.bankroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;
import io.github.kete1987.pokerbankroll.stats.TimePeriod;

/** Amounts in different currencies are never added up: one evolution per currency. */
public record BankrollEvolutionResponse(TimePeriod groupBy, List<CurrencyEvolution> currencies) {

    public record CurrencyEvolution(
            String currencyCode,
            @Schema(description = "The rooms of the response in this currency plus, without a room filter, the "
                    + "movements that belong to no room")
            EvolutionSeries total,
            @Schema(description = "Rooms with movements or games in the range, or with a bankroll at its start, by name")
            List<RoomEvolution> rooms) {
    }

    public record RoomEvolution(RoomRef room, boolean active, EvolutionSeries series) {
    }

    /** How a bankroll changed, period by period, from what it was when the range starts. */
    public record EvolutionSeries(
            @Schema(description = "Bankroll before the first day of the range (`from`): everything earlier "
                    + "counts. Zero without `from`")
            BigDecimal startingBankroll,
            @Schema(description = "Periods with movements or games, oldest first")
            List<EvolutionPeriod> periods) {
    }

    /** What changed the bankroll in a period, and the bankroll when it ends. */
    public record EvolutionPeriod(
            @Schema(description = "DAY: 2026-01-19; WEEK: its Monday, 2026-01-19; MONTH: 2026-01; YEAR: 2026")
            String period,
            @Schema(description = "First day of the period, which may be before `from`")
            LocalDate startsOn,
            @Schema(description = "Last day of the period, which may be after `to`")
            LocalDate endsOn,
            BigDecimal deposited,
            BigDecimal withdrawn,
            BigDecimal bonuses,
            @Schema(description = "Sum of the adjustments, positive or negative")
            BigDecimal adjustments,
            @Schema(description = "Net of the games, those in play included (their buy-in is already spent)")
            BigDecimal gamesNet,
            @Schema(description = "Bankroll at the end of the period: the starting bankroll plus deposited - "
                    + "withdrawn + adjustments + bonuses + gamesNet of this period and the earlier ones")
            BigDecimal bankroll) {
    }
}
