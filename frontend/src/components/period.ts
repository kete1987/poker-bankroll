/** A range of dates (ISO, inclusive); an open end is unbounded. */
export interface DateRange {
  from?: string;
  to?: string;
}

/** The periods offered, from the shortest to the longest. No two of them give the same dates. */
export const PERIODS = [
  'today',
  'yesterday',
  'thisWeek',
  'thisMonth',
  'lastMonth',
  'thisYear',
  'all',
  'custom',
] as const;
export type Period = (typeof PERIODS)[number];

function iso(year: number, month: number, day: number): string {
  // Day 0 is the last day of the previous month; days and months overflow into the next or
  // previous month and year.
  const date = new Date(Date.UTC(year, month, day));
  return date.toISOString().slice(0, 10);
}

/** The dates of a predefined period, relative to `today` (in the time zone of the browser). */
export function rangeOf(period: Exclude<Period, 'custom'>, today: Date = new Date()): DateRange {
  const year = today.getFullYear();
  const month = today.getMonth();
  const day = today.getDate();
  switch (period) {
    case 'all':
      return {};
    case 'today':
      return { from: iso(year, month, day), to: iso(year, month, day) };
    case 'yesterday':
      return { from: iso(year, month, day - 1), to: iso(year, month, day - 1) };
    case 'thisWeek': {
      // From Monday to Sunday, like the weeks of the statistics.
      const monday = day - ((today.getDay() + 6) % 7);
      return { from: iso(year, month, monday), to: iso(year, month, monday + 6) };
    }
    case 'thisMonth':
      return { from: iso(year, month, 1), to: iso(year, month + 1, 0) };
    case 'lastMonth':
      return { from: iso(year, month - 1, 1), to: iso(year, month, 0) };
    case 'thisYear':
      return { from: iso(year, 0, 1), to: iso(year, 11, 31) };
  }
}

/** The predefined period a range is, or `custom` when it is none of them. */
export function periodOf(range: DateRange, today: Date = new Date()): Period {
  const match = PERIODS.filter((period) => period !== 'custom').find((period) => {
    const candidate = rangeOf(period, today);
    return candidate.from === range.from && candidate.to === range.to;
  });
  return match ?? 'custom';
}

/**
 * Whether a range is a single day, whichever way it was chosen: a chart over time has nothing to
 * draw then.
 */
export function isSingleDay(range: DateRange): boolean {
  return range.from !== undefined && range.from === range.to;
}
