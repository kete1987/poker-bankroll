import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

import type { MovementType } from '../api/types';
import type { DateRange } from '../components/period';
import { parseDate, parseList, parseOneOf, parsePositiveInteger } from '../components/urlParams';

export const MOVEMENT_TYPES: readonly MovementType[] = [
  'DEPOSIT',
  'WITHDRAWAL',
  'BONUS',
  'ADJUSTMENT',
];
export const MOVEMENTS_PAGE_SIZE = 25;

export interface BankrollFilters {
  /** Empty is all time: the bankroll as it is now. */
  range: DateRange;
  /** Only these rooms; empty is every room and the movements without one. */
  roomIds: number[];
  /** The currency shown, when the URL names one. */
  currency?: string;
  /** Only for the list of movements. */
  type?: MovementType;
  /** Page of the list of movements, zero-based. */
  page: number;
}

function parse(params: URLSearchParams): BankrollFilters {
  return {
    range: { from: parseDate(params.get('from')), to: parseDate(params.get('to')) },
    roomIds: parseList(params.get('room'), parsePositiveInteger),
    currency: params.get('currency')?.trim().toUpperCase() || undefined,
    type: parseOneOf(params.get('type'), MOVEMENT_TYPES),
    // The page is one-based in the URL, as people count.
    page: (parsePositiveInteger(params.get('page')) ?? 1) - 1,
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
  set('currency', filters.currency);
  set('type', filters.type);
  if (filters.page > 0) {
    params.set('page', String(filters.page + 1));
  }
  return params;
}

/** Period, rooms, currency, movement type and page of the bankroll screen, kept in the URL. */
export function useBankrollFilters() {
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => parse(params), [params]);

  /** Changes some filters; anything but a page change goes back to the first page. */
  const update = useCallback(
    (changes: Partial<BankrollFilters>) => {
      setParams((current) => serialize({ ...parse(current), page: 0, ...changes }), {
        replace: true,
      });
    },
    [setParams],
  );

  return { filters, update };
}
