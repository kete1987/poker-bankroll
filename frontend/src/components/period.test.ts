import { describe, expect, it } from 'vitest';

import { isSingleDay, PERIODS, periodOf, rangeOf, type Period } from './period';

/** A day at noon in the time zone of the browser, like a `new Date()` of that day. */
function day(year: number, month: number, date: number): Date {
  return new Date(year, month - 1, date, 12);
}

const SUNDAY = day(2026, 10, 4);
const MONDAY = day(2026, 10, 5);

describe('rangeOf', () => {
  it('gives today and yesterday as one day each', () => {
    expect(rangeOf('today', SUNDAY)).toEqual({ from: '2026-10-04', to: '2026-10-04' });
    expect(rangeOf('yesterday', SUNDAY)).toEqual({ from: '2026-10-03', to: '2026-10-03' });
  });

  it('starts this week on Monday', () => {
    expect(rangeOf('thisWeek', MONDAY)).toEqual({ from: '2026-10-05', to: '2026-10-11' });
    expect(rangeOf('thisWeek', SUNDAY)).toEqual({ from: '2026-09-28', to: '2026-10-04' });
    expect(rangeOf('thisWeek', day(2026, 9, 30))).toEqual({ from: '2026-09-28', to: '2026-10-04' });
  });

  it('crosses the beginning of a month', () => {
    // 1 March 2026 is a Sunday.
    const firstOfMarch = day(2026, 3, 1);
    expect(rangeOf('yesterday', firstOfMarch)).toEqual({ from: '2026-02-28', to: '2026-02-28' });
    expect(rangeOf('thisWeek', firstOfMarch)).toEqual({ from: '2026-02-23', to: '2026-03-01' });
    expect(rangeOf('yesterday', day(2028, 3, 1))).toEqual({ from: '2028-02-29', to: '2028-02-29' });
  });

  it('crosses the beginning of a year', () => {
    // 1 January 2026 is a Thursday.
    const newYear = day(2026, 1, 1);
    expect(rangeOf('yesterday', newYear)).toEqual({ from: '2025-12-31', to: '2025-12-31' });
    expect(rangeOf('thisWeek', newYear)).toEqual({ from: '2025-12-29', to: '2026-01-04' });
    expect(rangeOf('thisWeek', day(2025, 12, 31))).toEqual({
      from: '2025-12-29',
      to: '2026-01-04',
    });
  });

  it('keeps the longer periods', () => {
    expect(rangeOf('thisMonth', SUNDAY)).toEqual({ from: '2026-10-01', to: '2026-10-31' });
    expect(rangeOf('lastMonth', day(2026, 1, 15))).toEqual({
      from: '2025-12-01',
      to: '2025-12-31',
    });
    expect(rangeOf('thisYear', SUNDAY)).toEqual({ from: '2026-01-01', to: '2026-12-31' });
    expect(rangeOf('all', SUNDAY)).toEqual({});
  });
});

describe('periodOf', () => {
  it('names the short periods', () => {
    expect(periodOf({ from: '2026-10-04', to: '2026-10-04' }, SUNDAY)).toBe('today');
    expect(periodOf({ from: '2026-10-03', to: '2026-10-03' }, SUNDAY)).toBe('yesterday');
    expect(periodOf({ from: '2026-09-28', to: '2026-10-04' }, SUNDAY)).toBe('thisWeek');
  });

  it('calls custom any other range, also of one day or one week', () => {
    expect(periodOf({ from: '2026-10-02', to: '2026-10-02' }, SUNDAY)).toBe('custom');
    expect(periodOf({ from: '2026-09-21', to: '2026-09-27' }, SUNDAY)).toBe('custom');
    expect(periodOf({ from: '2026-10-04' }, SUNDAY)).toBe('custom');
  });

  it('gives back the period chosen on every day of a year, as no two give the same dates', () => {
    const predefined = PERIODS.filter(
      (period): period is Exclude<Period, 'custom'> => period !== 'custom',
    );
    // Every day of 2028, a leap year (day 366 is the 31st of December).
    for (let dayOfYear = 1; dayOfYear <= 366; dayOfYear++) {
      const today = day(2028, 1, dayOfYear);
      for (const period of predefined) {
        expect(periodOf(rangeOf(period, today), today)).toBe(period);
      }
    }
  });
});

describe('isSingleDay', () => {
  it('is a range starting and ending on the same day', () => {
    expect(isSingleDay({ from: '2026-10-04', to: '2026-10-04' })).toBe(true);
    expect(isSingleDay({ from: '2026-10-03', to: '2026-10-04' })).toBe(false);
    expect(isSingleDay({ from: '2026-10-04' })).toBe(false);
    expect(isSingleDay({})).toBe(false);
  });
});
