package io.github.kete1987.pokerbankroll.stats;

/** How the games are grouped in {@code /stats/groups}. */
public enum GroupBy {
    DAY,
    /** Monday to Sunday. */
    WEEK,
    MONTH,
    YEAR,
    GAME_TYPE,
    /** A variant within its game type; games without a variant are a group of their type. */
    VARIANT,
    ROOM,
    MODALITY,
    /** Price of one entry; cash games apart. */
    BUY_IN;

    /** Groups in time are ordered by date and carry the cumulative net. */
    boolean isPeriod() {
        return this == DAY || this == WEEK || this == MONTH || this == YEAR;
    }
}
