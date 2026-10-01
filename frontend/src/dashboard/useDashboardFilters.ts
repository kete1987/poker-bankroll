import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

import { rangeOf, type DateRange } from '../components/period';

/** How the results table is broken down. */
export type Breakdown = 'type' | 'variant';

export interface DashboardFilters {
  /** The period the results are about. */
  range: DateRange;
  /** Only these rooms; empty is every room. */
  roomIds: number[];
  /** The currency shown, when the URL names one. */
  currency?: string;
  breakdown: Breakdown;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const MAX_INTEGER = 2_147_483_647;

function date(value: string | null): string | undefined {
  if (value === null || !ISO_DATE.test(value)) {
    return undefined;
  }
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
    ? value
    : undefined;
}

function roomIds(value: string | null): number[] {
  const ids = (value ?? '')
    .split(',')
    .filter((text) => /^\d+$/.test(text.trim()))
    .map(Number)
    .filter((id) => id > 0 && id <= MAX_INTEGER);
  return [...new Set(ids)];
}

function parse(params: URLSearchParams): DashboardFilters {
  const from = date(params.get('from'));
  const to = date(params.get('to'));
  // Without dates the dashboard is about this year; "all time" has to be asked for.
  const range =
    params.get('period') === 'all' ? {} : from || to ? { from, to } : rangeOf('thisYear');
  return {
    range,
    roomIds: roomIds(params.get('room')),
    currency: params.get('currency')?.trim().toUpperCase() || undefined,
    breakdown: params.get('by') === 'variant' ? 'variant' : 'type',
  };
}

function serialize(filters: DashboardFilters): URLSearchParams {
  const params = new URLSearchParams();
  const { from, to } = filters.range;
  const thisYear = rangeOf('thisYear');
  if (!from && !to) {
    params.set('period', 'all');
  } else if (from !== thisYear.from || to !== thisYear.to) {
    // Defaults are left out, so the plain URL is the plain dashboard.
    if (from) {
      params.set('from', from);
    }
    if (to) {
      params.set('to', to);
    }
  }
  if (filters.roomIds.length > 0) {
    params.set('room', filters.roomIds.join(','));
  }
  if (filters.currency) {
    params.set('currency', filters.currency);
  }
  if (filters.breakdown === 'variant') {
    params.set('by', 'variant');
  }
  return params;
}

/** Period, rooms, currency and breakdown of the dashboard, kept in the URL. */
export function useDashboardFilters() {
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => parse(params), [params]);

  const update = useCallback(
    (changes: Partial<DashboardFilters>) => {
      setParams((current) => serialize({ ...parse(current), ...changes }), { replace: true });
    },
    [setParams],
  );

  return { filters, update };
}
