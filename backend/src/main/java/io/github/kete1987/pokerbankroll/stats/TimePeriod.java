package io.github.kete1987.pokerbankroll.stats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

/**
 * A length of time results are grouped by, and how a day is put in its period. Periods are
 * worked out from the days here, not in the database.
 */
public enum TimePeriod {
    DAY,
    /** Monday to Sunday. */
    WEEK,
    MONTH,
    YEAR;

    /** The key of the period holding a day: 2026-01-19 (day, and the Monday of a week), 2026-01, 2026. */
    public String keyOf(LocalDate day) {
        return switch (this) {
            case DAY, WEEK -> firstDayOf(day).toString();
            case MONTH -> YearMonth.from(day).toString();
            case YEAR -> String.valueOf(day.getYear());
        };
    }

    /** The first day of the period holding a day. */
    public LocalDate firstDayOf(LocalDate day) {
        return switch (this) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
            case YEAR -> day.withDayOfYear(1);
        };
    }

    /** The last day of the period holding a day. */
    public LocalDate lastDayOf(LocalDate day) {
        return switch (this) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case MONTH -> day.with(TemporalAdjusters.lastDayOfMonth());
            case YEAR -> day.with(TemporalAdjusters.lastDayOfYear());
        };
    }
}
