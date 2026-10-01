package io.github.kete1987.pokerbankroll.bankroll;

/** Kind of bankroll movement; it gives the direction of the amount. */
public enum MovementType {
    /** Money set aside for poker; the first one is the initial bankroll. */
    DEPOSIT,
    /** Money taken out of the bankroll. */
    WITHDRAWAL,
    /** Poker money that does not come from a game (rakeback, promotions): part of the result. */
    BONUS,
    /** Manual correction, positive or negative. */
    ADJUSTMENT
}
