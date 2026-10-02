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
    BUY_IN,
    /** Price of one entry by ranges (free, below 1, from 1, 2, 5, 10, 20 and from 50 up); cash games apart. */
    BUY_IN_RANGE,
    /** Name of the game, ignoring case and surrounding spaces; games without a name are one group. */
    NAME,
    /** Day of the week the game was played on. */
    WEEKDAY;

    /** Groups in time are ordered by date and carry the cumulative net. */
    boolean isPeriod() {
        return this == DAY || this == WEEK || this == MONTH || this == YEAR;
    }
}
