import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

import type { GameQuery } from '../api/games';
import type { GameType } from '../api/types';
import { parseDate, parseList, parseOneOf, parsePositiveInteger } from '../components/urlParams';

/** Columns the table can be ordered by, as the API names them. */
export const SORT_FIELDS = ['playedOn', 'buyIn', 'won', 'net'] as const;
export type SortField = (typeof SORT_FIELDS)[number];

export interface GameFilters {
  from?: string;
  to?: string;
  /** Several values of a filter select the games of any of them; none is no filter. */
  gameTypes: GameType[];
  roomIds: number[];
  variantIds: number[];
  q?: string;
  /** Zero-based. */
  page: number;
  sortField: SortField;
  sortDescending: boolean;
}

export const PAGE_SIZE = 25;
const GAME_TYPES: readonly GameType[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];

function parse(params: URLSearchParams): GameFilters {
  const [sortField, direction] = (params.get('sort') ?? '').split(',');
  return {
    from: parseDate(params.get('from')),
    to: parseDate(params.get('to')),
    gameTypes: parseList(params.get('type'), (text) => parseOneOf(text, GAME_TYPES)),
    roomIds: parseList(params.get('room'), parsePositiveInteger),
    variantIds: parseList(params.get('variant'), parsePositiveInteger),
    q: params.get('q')?.trim() || undefined,
    // The page is one-based in the URL, as people count.
    page: (parsePositiveInteger(params.get('page')) ?? 1) - 1,
    sortField: (SORT_FIELDS as readonly string[]).includes(sortField ?? '')
      ? (sortField as SortField)
      : 'playedOn',
    sortDescending: direction !== 'asc',
  };
}

function serialize(filters: GameFilters): URLSearchParams {
  const params = new URLSearchParams();
  const set = (key: string, value: string | number | undefined) => {
    if (value !== undefined && value !== '') {
      params.set(key, String(value));
    }
  };
  set('from', filters.from);
  set('to', filters.to);
  // Lists are written with commas: `room=1,2`.
  set('type', filters.gameTypes.join(','));
  set('room', filters.roomIds.join(','));
  set('variant', filters.variantIds.join(','));
  set('q', filters.q);
  // Defaults are left out, so the plain URL is the plain list.
  if (filters.sortField !== 'playedOn' || !filters.sortDescending) {
    params.set('sort', `${filters.sortField},${filters.sortDescending ? 'desc' : 'asc'}`);
  }
  if (filters.page > 0) {
    params.set('page', String(filters.page + 1));
  }
  return params;
}

/**
 * Filters, order and page of the games table, kept in the URL: they survive a reload and the
 * back button, and a filtered list can be bookmarked.
 */
export function useGameFilters() {
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => parse(params), [params]);

  /** Changes some filters; anything but a page change goes back to the first page. */
  const update = useCallback(
    (changes: Partial<GameFilters>) => {
      setParams(
        (current) => serialize({ ...parse(current), page: 0, ...changes }),
        // Typing in the search box must not fill the history.
        { replace: true },
      );
    },
    [setParams],
  );

  const clear = useCallback(() => setParams(new URLSearchParams(), { replace: true }), [setParams]);

  const hasFilters = Boolean(
    filters.from ||
    filters.to ||
    filters.gameTypes.length ||
    filters.roomIds.length ||
    filters.variantIds.length ||
    filters.q,
  );

  return { filters, update, clear, hasFilters };
}

/** The finished games the table shows for these filters. */
export function toGameQuery(filters: GameFilters): GameQuery {
  return {
    from: filters.from,
    to: filters.to,
    gameType: filters.gameTypes,
    roomId: filters.roomIds,
    variantId: filters.variantIds,
    q: filters.q,
    status: 'FINISHED',
    page: filters.page,
    size: PAGE_SIZE,
    sort: `${filters.sortField},${filters.sortDescending ? 'desc' : 'asc'}`,
  };
}
