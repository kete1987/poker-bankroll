package io.github.kete1987.pokerbankroll.bankroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.exchange.MissingExchangeRate;
import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;
import org.jspecify.annotations.Nullable;

/**
 * Amounts in different currencies are never added up as they are: one bankroll per currency, and
 * every currency converted to the base currency, explicitly, in {@code converted}.
 */
public record BankrollSummaryResponse(
        @Schema(description = "One bankroll per currency, in its own money, by code")
        List<CurrencyBankroll> currencies,
        @Schema(description = "Every currency converted to the base currency. When the response is in a single "
                + "currency, show that currency instead")
        ConvertedBankroll converted) {

    /**
     * The bankroll of every currency together, converted. What happened (deposits, withdrawals,
     * bonuses, adjustments, games) is converted with the rates of its day. The bankroll without
     * {@code from} is a balance, converted with the rates of {@code balanceRatesOn}; so it differs
     * from deposited - withdrawn + adjustments + result by the exchange difference.
     */
    public record ConvertedBankroll(
            @Schema(description = "The base currency the amounts are converted to")
            String currencyCode,
            @Schema(description = "Every room of the response plus the movements without a room")
            BankrollFigures total,
            @Schema(description = "Movements that belong to no room, of every currency")
            BankrollFigures withoutRoom,
            @Schema(description = "Rooms of every currency, by name, with their amounts converted")
            List<RoomBankroll> rooms,
            @Schema(description = "Day whose rates convert the bankroll as a balance: `to`, or today. Null with "
                    + "`from`: then the bankroll is what changed in the period, converted day by day")
            @Nullable LocalDate balanceRatesOn,
            @Schema(description = "Amounts left out of these figures for lack of an exchange rate")
            List<MissingExchangeRate> missingRates) {
    }

    public record CurrencyBankroll(
            String currencyCode,
            @Schema(description = "The rooms in this currency plus the movements without a room")
            BankrollFigures total,
            @Schema(description = "Movements that belong to no room; they only count in the total")
            BankrollFigures withoutRoom,
            @Schema(description = "Rooms that are active or have games or movements, by name")
            List<RoomBankroll> rooms) {
    }

    public record RoomBankroll(RoomRef room, boolean active, BankrollFigures figures) {
    }

    /**
     * The poker bankroll: money set aside for poker and what was won or lost with it. It is not
     * the balance of the room account, so it can be negative (losses with no deposit recorded).
     */
    public record BankrollFigures(
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
