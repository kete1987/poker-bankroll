package io.github.kete1987.pokerbankroll.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import org.jspecify.annotations.Nullable;

/** Sums of a set of games in one currency, from which the figures are derived. */
final class GameTotals {

    private static final int RATE_SCALE = 4;
    private static final int MONEY_SCALE = 2;

    private long games;
    private long entries;
    private long winningGames;
    private BigDecimal invested = BigDecimal.ZERO;
    private BigDecimal prizes = BigDecimal.ZERO;
    private BigDecimal bounties = BigDecimal.ZERO;
    private BigDecimal ticketsWon = BigDecimal.ZERO;
    private BigDecimal net = BigDecimal.ZERO;

    // Cash games apart: a sitting has no prize to reach, and its buy-in is not the price of an entry.
    private long nonCashGames;
    private long gamesWithPrize;
    private long gamesInTheMoney;
    private BigDecimal nonCashBuyIns = BigDecimal.ZERO;

    /** Adds the sums of games of one type. */
    void add(GameType gameType, long games, long entries, long gamesWithPrize, long gamesInTheMoney,
            long winningGames, BigDecimal buyIns, BigDecimal invested, BigDecimal prizes, BigDecimal bounties,
            BigDecimal ticketsWon, BigDecimal net) {
        this.games += games;
        this.entries += entries;
        this.winningGames += winningGames;
        this.invested = this.invested.add(invested);
        this.prizes = this.prizes.add(prizes);
        this.bounties = this.bounties.add(bounties);
        this.ticketsWon = this.ticketsWon.add(ticketsWon);
        this.net = this.net.add(net);
        if (gameType != GameType.CASH) {
            this.nonCashGames += games;
            this.gamesWithPrize += gamesWithPrize;
            this.gamesInTheMoney += gamesInTheMoney;
            this.nonCashBuyIns = this.nonCashBuyIns.add(buyIns);
        }
    }

    long games() {
        return games;
    }

    BigDecimal invested() {
        return money(invested);
    }

    StatsFigures toFigures() {
        boolean hasNonCash = nonCashGames > 0;
        return new StatsFigures(
                games,
                entries,
                hasNonCash ? gamesWithPrize : null,
                rate(gamesWithPrize, nonCashGames),
                hasNonCash ? gamesInTheMoney : null,
                rate(gamesInTheMoney, nonCashGames),
                winningGames,
                rate(winningGames, games),
                hasNonCash
                        ? nonCashBuyIns.divide(BigDecimal.valueOf(nonCashGames), MONEY_SCALE, RoundingMode.HALF_UP)
                        : null,
                money(invested),
                money(prizes.add(bounties)),
                money(bounties),
                money(ticketsWon),
                money(net),
                invested.signum() == 0 ? null : net.divide(invested, RATE_SCALE, RoundingMode.HALF_UP));
    }

    private static @Nullable BigDecimal rate(long part, long whole) {
        return whole == 0
                ? null
                : BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
