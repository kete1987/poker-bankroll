import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { API_BASE, apiFetch, apiFetchBlob } from './client';
import type { Room, RoomRequest } from './types';

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

/**
 * Downloads the image of a URL through the backend: a browser cannot read images of other sites.
 * Nothing is stored; the image is then resized and uploaded like a file chosen by hand.
 */
export function fetchRemoteImage(url: string): Promise<Blob> {
  return apiFetchBlob('/rooms/logo-fetch', { method: 'POST', body: JSON.stringify({ url }) });
}

/**
 * Mutations on rooms. A room is named in games, movements and statistics, so everything loaded
 * is refreshed afterwards, not only the list of rooms.
 */
function useRoomMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TInput>({
    mutationFn,
    onSuccess: async () => {
      await queryClient.invalidateQueries();
    },
  });
}

export function useCreateRoom() {
  return useRoomMutation((room: RoomRequest) =>
    apiFetch<Room>('/rooms', { method: 'POST', body: JSON.stringify(room) }),
  );
}

export function useUpdateRoom() {
  return useRoomMutation(({ id, room }: { id: number; room: RoomRequest }) =>
    apiFetch<Room>(`/rooms/${id}`, { method: 'PUT', body: JSON.stringify(room) }),
  );
}

export function useDeleteRoom() {
  return useRoomMutation((id: number) => apiFetch<void>(`/rooms/${id}`, { method: 'DELETE' }));
}

/** Sets the logo of a room: the body is the image itself. */
export function useSetRoomLogo() {
  return useRoomMutation(({ id, image }: { id: number; image: Blob }) =>
    apiFetch<Room>(`/rooms/${id}/logo`, {
      method: 'PUT',
      body: image,
      headers: { 'Content-Type': image.type || 'application/octet-stream' },
    }),
  );
}

export function useDeleteRoomLogo() {
  return useRoomMutation((id: number) => apiFetch<void>(`/rooms/${id}/logo`, { method: 'DELETE' }));
}
