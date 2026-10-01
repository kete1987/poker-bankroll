import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { apiFetch } from './client';
import { toQueryString } from './query';
import type { GroupBy, StatsGroups, StatsSummary } from './types';

/** Which games the statistics are about; everything is optional. */
export interface StatsQuery {
  from?: string;
  to?: string;
  /** Games in any of these rooms; empty is every room. */
  roomId?: number[];
  currency?: string;
}

/** Results overall and per game type, for each currency. */
export function useStatsSummary(query: StatsQuery) {
  return useQuery({
    queryKey: ['stats', 'summary', query],
    queryFn: () => apiFetch<StatsSummary>(`/stats/summary?${toQueryString(query)}`),
    placeholderData: keepPreviousData,
  });
}

/** Results per group (variant, room, month...), for each currency. */
export function useStatsGroups(groupBy: GroupBy, query: StatsQuery, enabled = true) {
  return useQuery({
    queryKey: ['stats', 'groups', groupBy, query],
    queryFn: () => apiFetch<StatsGroups>(`/stats/groups?${toQueryString({ groupBy, ...query })}`),
    placeholderData: keepPreviousData,
    enabled,
  });
}
