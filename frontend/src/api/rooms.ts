import { useQuery } from '@tanstack/react-query';

import { API_BASE, apiFetch } from './client';
import type { Room } from './types';

/** Every room, ordered by name; inactive ones included (they are not offered for new games). */
export function useRooms() {
  return useQuery({
    queryKey: ['rooms'],
    queryFn: () => apiFetch<Room[]>('/rooms'),
  });
}

/** Address of the logo of a room. The version is part of it, so browsers can keep it for good. */
export function roomLogoUrl(roomId: number, logoVersion: string): string {
  return `${API_BASE}/rooms/${roomId}/logo?v=${encodeURIComponent(logoVersion)}`;
}
