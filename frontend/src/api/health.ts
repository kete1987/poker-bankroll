import { useQuery } from '@tanstack/react-query';

import { apiFetch } from './client';

interface Health {
  status: string;
}

/** Backend health, refreshed every 30 seconds. */
export function useApiHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => apiFetch<Health>('/actuator/health'),
    refetchInterval: 30_000,
    retry: false,
  });
}
