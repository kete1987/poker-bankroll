import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

import type { GameQuery } from '../api/games';
import type { GameType } from '../api/types';

/** Columns the table can be ordered by, as the API names them. */
export const SORT_FIELDS = ['playedOn', 'buyIn', 'prize', 'net'] as const;
export type SortField = (typeof SORT_FIELDS)[number];

export interface GameFilters {
  from?: string;
  to?: string;
  gameType?: GameType;
  roomId?: number;
  variantId?: number;
  q?: string;
  /** Zero-based. */
  page: number;
  sortField: SortField;
  sortDescending: boolean;
}

export const PAGE_SIZE = 25;
const GAME_TYPES: readonly string[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function positiveInteger(value: string | null): number | undefined {
  return value !== null && /^\d+$/.test(value) && Number(value) > 0 ? Number(value) : undefined;
}

function parse(params: URLSearchParams): GameFilters {
  const date = (key: string) => {
    const value = params.get(key);
    return value !== null && ISO_DATE.test(value) ? value : undefined;
  };
  const gameType = params.get('type');
  const [sortField, direction] = (params.get('sort') ?? '').split(',');
  return {
    from: date('from'),
    to: date('to'),
    gameType: GAME_TYPES.includes(gameType ?? '') ? (gameType as GameType) : undefined,
    roomId: positiveInteger(params.get('room')),
    variantId: positiveInteger(params.get('variant')),
    q: params.get('q')?.trim() || undefined,
    // The page is one-based in the URL, as people count.
    page: (positiveInteger(params.get('page')) ?? 1) - 1,
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
  set('type', filters.gameType);
  set('room', filters.roomId);
  set('variant', filters.variantId);
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
    filters.gameType ||
    filters.roomId ||
    filters.variantId ||
    filters.q,
  );

  return { filters, update, clear, hasFilters };
}

/** The finished games the table shows for these filters. */
export function toGameQuery(filters: GameFilters): GameQuery {
  return {
    from: filters.from,
    to: filters.to,
    gameType: filters.gameType,
    roomId: filters.roomId,
    variantId: filters.variantId,
    q: filters.q,
    status: 'FINISHED',
    page: filters.page,
    size: PAGE_SIZE,
    sort: `${filters.sortField},${filters.sortDescending ? 'desc' : 'asc'}`,
  };
}
