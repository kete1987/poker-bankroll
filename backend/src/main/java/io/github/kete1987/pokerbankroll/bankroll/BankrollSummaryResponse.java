package io.github.kete1987.pokerbankroll.bankroll;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;

/** Amounts in different currencies are never added up: one bankroll per currency. */
public record BankrollSummaryResponse(List<CurrencyBankroll> currencies) {

    public record CurrencyBankroll(
            String currencyCode,
            @Schema(description = "The rooms in this currency plus the movements without a room")
            Figures total,
            @Schema(description = "Movements that belong to no room; they only count in the total")
            Figures withoutRoom,
            @Schema(description = "Rooms that are active or have games or movements, by name")
            List<RoomBankroll> rooms) {
    }

    public record RoomBankroll(RoomRef room, boolean active, Figures figures) {
    }

    /**
     * The poker bankroll: money set aside for poker and what was won or lost with it. It is not
     * the balance of the room account, so it can be negative (losses with no deposit recorded).
     */
    public record Figures(
            BigDecimal deposited,
            BigDecimal withdrawn,
            BigDecimal bonuses,
            @Schema(description = "Sum of the adjustments, positive or negative")
            BigDecimal adjustments,
            @Schema(description = "Net of the games, those in play included (their buy-in is already spent)")
            BigDecimal gamesNet,
            @Schema(description = "Won or lost playing: gamesNet + bonuses")
            BigDecimal result,
            @Schema(description = "deposited - withdrawn + adjustments + result")
            BigDecimal bankroll,
            @Schema(description = "Value of the tickets won; informative, not part of the bankroll")
            BigDecimal ticketsWon,
            @Schema(description = "Games without a result yet; informative")
            long gamesInPlay,
            @Schema(description = "Money paid for the entries of the games in play; already subtracted in gamesNet")
            BigDecimal investedInPlay) {
    }
}
