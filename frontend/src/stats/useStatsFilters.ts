import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

import type { StatsQuery } from '../api/stats';
import type { GameType } from '../api/types';
import { rangeOf, type DateRange } from '../components/period';
import { parseDate, parseList, parseOneOf, parsePositiveInteger } from '../components/urlParams';
import type { GameScope } from '../games/ScopeFilters';

/** How the time is cut in the charts. */
export const GRANULARITIES = ['DAY', 'WEEK', 'MONTH'] as const;
export type Granularity = (typeof GRANULARITIES)[number];

/** What the chart draws: the net added up over time, or the net of each period. */
export type ChartMode = 'cumulative' | 'period';

export interface StatsFilters extends GameScope {
  range: DateRange;
  /** The currency shown, when the URL names one. */
  currency?: string;
  /** Chosen by the user; otherwise it follows the length of the period. */
  granularity?: Granularity;
  chart: ChartMode;
}

const GAME_TYPES: readonly GameType[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];
const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * The cut that keeps a chart readable: days up to three months, weeks up to a year and a half,
 * months beyond that or when the period has an open end.
 */
export function granularityFor(range: DateRange): Granularity {
  if (!range.from || !range.to) {
    return 'MONTH';
  }
  const days = (Date.parse(range.to) - Date.parse(range.from)) / DAY_MS + 1;
  return days <= 92 ? 'DAY' : days <= 550 ? 'WEEK' : 'MONTH';
}

function parse(params: URLSearchParams): StatsFilters {
  const from = parseDate(params.get('from'));
  const to = parseDate(params.get('to'));
  return {
    // Without dates the statistics are about this year; "all time" has to be asked for.
    range: params.get('period') === 'all' ? {} : from || to ? { from, to } : rangeOf('thisYear'),
    gameTypes: parseList(params.get('type'), (text) => parseOneOf(text, GAME_TYPES)),
    roomIds: parseList(params.get('room'), parsePositiveInteger),
    variantIds: parseList(params.get('variant'), parsePositiveInteger),
    currency: params.get('currency')?.trim().toUpperCase() || undefined,
    granularity: parseOneOf(params.get('group')?.toUpperCase() ?? null, GRANULARITIES),
    chart: params.get('chart') === 'period' ? 'period' : 'cumulative',
  };
}

function serialize(filters: StatsFilters): URLSearchParams {
  const params = new URLSearchParams();
  const { from, to } = filters.range;
  const thisYear = rangeOf('thisYear');
  if (!from && !to) {
    params.set('period', 'all');
  } else if (from !== thisYear.from || to !== thisYear.to) {
    // Defaults are left out, so the plain URL is the plain page.
    if (from) {
      params.set('from', from);
    }
    if (to) {
      params.set('to', to);
    }
  }
  const lists: [string, readonly (string | number)[]][] = [
    ['type', filters.gameTypes],
    ['room', filters.roomIds],
    ['variant', filters.variantIds],
  ];
  for (const [key, values] of lists) {
    if (values.length > 0) {
      params.set(key, values.join(','));
    }
  }
  if (filters.currency) {
    params.set('currency', filters.currency);
  }
  if (filters.granularity) {
    params.set('group', filters.granularity.toLowerCase());
  }
  if (filters.chart === 'period') {
    params.set('chart', 'period');
  }
  return params;
}

/** Period, scope, currency and time cut of the statistics, kept in the URL. */
export function useStatsFilters() {
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => parse(params), [params]);

  const update = useCallback(
    (changes: Partial<StatsFilters>) => {
      setParams((current) => serialize({ ...parse(current), ...changes }), { replace: true });
    },
    [setParams],
  );

  return { filters, update };
}

/** The games these filters are about, as the statistics endpoints take them. */
export function toStatsQuery(filters: StatsFilters): StatsQuery {
  return {
    from: filters.range.from,
    to: filters.range.to,
    gameType: filters.gameTypes,
    roomId: filters.roomIds,
    variantId: filters.variantIds,
  };
}
