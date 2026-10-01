package io.github.kete1987.pokerbankroll.stats;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/**
 * Results of a set of finished games in one currency. Rates are fractions (0.3496 is 34.96 %).
 * Figures that make no sense for cash games are computed on the other games only, and are
 * {@code null} when there is none.
 */
public record StatsFigures(
        @Schema(description = "Games played")
        long games,
        @Schema(description = "Entries, re-entries included")
        long entries,
        @Schema(description = "Games with a prize, a bounty or a ticket won; cash games apart")
        @Nullable Long gamesWithPrize,
        @Schema(description = "gamesWithPrize / games, cash games apart")
        @Nullable BigDecimal withPrizeRate,
        @Schema(description = "Games with a prize or a ticket won (bounties do not count); cash games apart")
        @Nullable Long gamesInTheMoney,
        @Schema(description = "gamesInTheMoney / games, cash games apart")
        @Nullable BigDecimal inTheMoneyRate,
        @Schema(description = "Games with a positive net")
        long winningGames,
        @Schema(description = "winningGames / games")
        @Nullable BigDecimal winningRate,
        @Schema(description = "Mean buy-in of a game; cash games apart")
        @Nullable BigDecimal averageBuyIn,
        @Schema(description = "Money paid for the entries (an entry paid with a ticket costs nothing)")
        BigDecimal invested,
        @Schema(description = "Money won: prizes + bounties")
        BigDecimal won,
        @Schema(description = "Bounties, already included in won")
        BigDecimal bounties,
        @Schema(description = "Value of the tickets won; informative, not part of won, net or roi")
        BigDecimal ticketsWon,
        @Schema(description = "won - invested")
        BigDecimal net,
        @Schema(description = "net / invested; null when nothing was invested")
        @Nullable BigDecimal roi) {
}
