import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { apiFetch } from './client';
import { toQueryString } from './query';
import type { GameType, GroupBy, StatsGroups, StatsSummary } from './types';

/** Which games the statistics are about; everything is optional. */
export interface StatsQuery {
  from?: string;
  to?: string;
  /** Games of any of these types, rooms or variants; an empty list is every one. */
  gameType?: GameType[];
  roomId?: number[];
  variantId?: number[];
  currency?: string;
}

/** Results overall and per game type, for each currency. */
export function useStatsSummary(query: StatsQuery, enabled = true) {
  return useQuery({
    queryKey: ['stats', 'summary', query],
    queryFn: () => apiFetch<StatsSummary>(`/stats/summary?${toQueryString(query)}`),
    placeholderData: keepPreviousData,
    enabled,
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

/**
 * The summary and the groups (each broken down by game type) of the same games, as one piece of data: both arrive together, so
 * a screen never shows the totals of one filter next to the groups of another.
 */
export function useStatsOverTime(groupBy: GroupBy, query: StatsQuery, enabled = true) {
  return useQuery({
    queryKey: ['stats', 'overTime', groupBy, query],
    queryFn: async () => {
      const [summary, groups] = await Promise.all([
        apiFetch<StatsSummary>(`/stats/summary?${toQueryString(query)}`),
        apiFetch<StatsGroups>(
          `/stats/groups?${toQueryString({ groupBy, byGameType: true, ...query })}`,
        ),
      ]);
      return { summary, groups };
    },
    placeholderData: keepPreviousData,
    enabled,
  });
}
