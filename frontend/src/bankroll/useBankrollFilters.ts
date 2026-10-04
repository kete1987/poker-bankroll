import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

import type { MovementType, TimePeriod } from '../api/types';
import type { DateRange } from '../components/period';
import { parseDate, parseList, parseOneOf, parsePositiveInteger } from '../components/urlParams';

export const MOVEMENT_TYPES: readonly MovementType[] = [
  'DEPOSIT',
  'WITHDRAWAL',
  'BONUS',
  'ADJUSTMENT',
];
export const MOVEMENTS_PAGE_SIZE = 25;

/** How the time is cut in the chart of the bankroll, when it is not left to the length of the period. */
export const GRANULARITIES: readonly TimePeriod[] = ['DAY', 'WEEK', 'MONTH', 'YEAR'];

export interface BankrollFilters {
  /** Empty is all time: the bankroll as it is now. */
  range: DateRange;
  /** Only these rooms; empty is every room and the movements without one. */
  roomIds: number[];
  /** Only for the list of movements. */
  type?: MovementType;
  /** Page of the list of movements, zero-based. */
  page: number;
  /** How the chart cuts the time, when chosen; otherwise it follows the length of the period. */
  granularity?: TimePeriod;
}

function parse(params: URLSearchParams): BankrollFilters {
  return {
    range: { from: parseDate(params.get('from')), to: parseDate(params.get('to')) },
    roomIds: parseList(params.get('room'), parsePositiveInteger),
    type: parseOneOf(params.get('type'), MOVEMENT_TYPES),
    // The page is one-based in the URL, as people count.
    page: (parsePositiveInteger(params.get('page')) ?? 1) - 1,
    granularity: parseOneOf(params.get('group')?.toUpperCase() ?? null, GRANULARITIES),
  };
}

function serialize(filters: BankrollFilters): URLSearchParams {
  const params = new URLSearchParams();
  const set = (key: string, value: string | undefined) => {
    if (value) {
      params.set(key, value);
    }
  };
  set('from', filters.range.from);
  set('to', filters.range.to);
  set('room', filters.roomIds.join(','));
  set('type', filters.type);
  if (filters.page > 0) {
    params.set('page', String(filters.page + 1));
  }
  set('group', filters.granularity?.toLowerCase());
  return params;
}

/** Period, rooms, movement type, page and cut of the chart of the bankroll screen, kept in the URL. */
export function useBankrollFilters() {
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => parse(params), [params]);

  /**
   * Changes some filters; anything that changes the movements listed goes back to the first page
   * (the cut of the chart does not).
   */
  const update = useCallback(
    (changes: Partial<BankrollFilters>) => {
      const keepsPage = Object.keys(changes).every((key) => key === 'granularity');
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
