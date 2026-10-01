import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { apiFetch } from './client';
import { toQueryString } from './query';
import type { BankrollSummary } from './types';

/** With dates, the figures of that period; with rooms, only those. Without anything, the bankroll now. */
export interface BankrollQuery {
  from?: string;
  to?: string;
  roomId?: number[];
}

/** The poker bankroll per currency and room. */
export function useBankrollSummary(query: BankrollQuery = {}) {
  return useQuery({
    queryKey: ['bankroll', 'summary', query],
    queryFn: () => apiFetch<BankrollSummary>(`/bankroll/summary?${toQueryString(query)}`),
    placeholderData: keepPreviousData,
  });
}
