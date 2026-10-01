import { useQuery } from '@tanstack/react-query';

import { apiFetch } from './client';
import type { Room } from './types';

/** Every room, ordered by name; inactive ones included (they are not offered for new games). */
export function useRooms() {
  return useQuery({
    queryKey: ['rooms'],
    queryFn: () => apiFetch<Room[]>('/rooms'),
  });
}
