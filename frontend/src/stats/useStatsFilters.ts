import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

import type { StatsQuery } from '../api/stats';
import type { GameType, GroupBy } from '../api/types';
import { rangeOf, type DateRange } from '../components/period';
import { parseDate, parseList, parseOneOf, parsePositiveInteger } from '../components/urlParams';
import type { GameScope } from '../games/ScopeFilters';

/** How the time is cut in the charts. */
export const GRANULARITIES = ['DAY', 'WEEK', 'MONTH'] as const;
export type Granularity = (typeof GRANULARITIES)[number];

/** What the chart draws: the net added up over time, or the net of each period. */
export type ChartMode = 'cumulative' | 'period';

/** The two halves of the screen: results over time, or broken down by something else. */
export type StatsView = 'evolution' | 'breakdown';

/** What the games can be broken down by, in the order they are offered. */
export const DIMENSIONS = [
  'ROOM',
  'GAME_TYPE',
  'VARIANT',
  'MODALITY',
  'BUY_IN_RANGE',
  'NAME',
  'WEEKDAY',
  'TAG',
] as const satisfies readonly GroupBy[];
export type Dimension = (typeof DIMENSIONS)[number];

/** Columns the table of a breakdown can be sorted by. */
export const SORT_COLUMNS = [
  'label',
  'games',
  'averageBuyIn',
  'itm',
  'invested',
  'won',
  'net',
  'roi',
] as const;
export type SortColumn = (typeof SORT_COLUMNS)[number];

export interface BreakdownSort {
  column: SortColumn;
  descending: boolean;
}

export interface StatsFilters extends GameScope {
  view: StatsView;
  /** What the breakdown is by. */
  dimension: Dimension;
  /** The column the breakdown is sorted by, when the user has chosen one. */
  sort?: BreakdownSort;
  range: DateRange;
  /** Chosen by the user; otherwise it follows the length of the period. */
  granularity?: Granularity;
  chart: ChartMode;
  /** Page of the table of periods, zero-based. */
  page: number;
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

/** `net,asc` or `net,desc`; anything else is no order. */
function parseSort(text: string | null): BreakdownSort | undefined {
  const [name, direction] = (text ?? '').split(',');
  const column = parseOneOf(name ?? null, SORT_COLUMNS);
  if (!column || (direction !== 'asc' && direction !== 'desc')) {
    return undefined;
  }
  return { column, descending: direction === 'desc' };
}

function parse(params: URLSearchParams): StatsFilters {
  const from = parseDate(params.get('from'));
  const to = parseDate(params.get('to'));
  return {
    view: params.get('view') === 'breakdown' ? 'breakdown' : 'evolution',
    dimension: parseOneOf(params.get('by')?.toUpperCase() ?? null, DIMENSIONS) ?? DIMENSIONS[0],
    sort: parseSort(params.get('sort')),
    // Without dates the statistics are about this year; "all time" has to be asked for.
    range: params.get('period') === 'all' ? {} : from || to ? { from, to } : rangeOf('thisYear'),
    gameTypes: parseList(params.get('type'), (text) => parseOneOf(text, GAME_TYPES)),
    roomIds: parseList(params.get('room'), parsePositiveInteger),
    variantIds: parseList(params.get('variant'), parsePositiveInteger),
    tagIds: parseList(params.get('tag'), parsePositiveInteger),
    granularity: parseOneOf(params.get('group')?.toUpperCase() ?? null, GRANULARITIES),
    chart: params.get('chart') === 'period' ? 'period' : 'cumulative',
    // The page is one-based in the URL, as people count.
    page: (parsePositiveInteger(params.get('page')) ?? 1) - 1,
  };
}

function serialize(filters: StatsFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.view === 'breakdown') {
    params.set('view', 'breakdown');
  }
  if (filters.dimension !== DIMENSIONS[0]) {
    params.set('by', filters.dimension.toLowerCase());
  }
  if (filters.sort) {
    params.set('sort', `${filters.sort.column},${filters.sort.descending ? 'desc' : 'asc'}`);
  }
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
    ['tag', filters.tagIds],
  ];
  for (const [key, values] of lists) {
    if (values.length > 0) {
      params.set(key, values.join(','));
    }
  }
  if (filters.granularity) {
    params.set('group', filters.granularity.toLowerCase());
  }
  if (filters.chart === 'period') {
    params.set('chart', 'period');
  }
  if (filters.page > 0) {
    params.set('page', String(filters.page + 1));
  }
  return params;
}

/** Period, scope and time cut of the statistics, kept in the URL. */
export function useStatsFilters() {
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => parse(params), [params]);

  const update = useCallback(
    (changes: Partial<StatsFilters>) => {
      // The kind of chart does not change what the table lists; anything else takes it back to
      // its first page.
      const keepsPage = Object.keys(changes).every((key) =>
        ['chart', 'page', 'view', 'dimension', 'sort'].includes(key),
      );
      setParams(
        (current) => {
          const now = parse(current);
          return serialize({ ...now, page: keepsPage ? now.page : 0, ...changes });
        },
        { replace: true },
      );
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
    tagId: filters.tagIds,
  };
}
