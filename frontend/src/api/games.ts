import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from './client';
import type { Game, GamePage, GameRequest } from './types';

/** The games added last, newest first. */
export function useRecentGames(size: number) {
  return useQuery({
    queryKey: ['games', 'recent', size],
    queryFn: () => apiFetch<GamePage>(`/games?size=${size}&sort=createdAt,desc`),
  });
}

/** Records a game; every list and figure that depends on games is refreshed afterwards. */
export function useCreateGame() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (game: GameRequest) =>
      apiFetch<Game>('/games', { method: 'POST', body: JSON.stringify(game) }),
    onSuccess: () => invalidateGameData(queryClient),
  });
}

/** Games change statistics and the bankroll too. */
function invalidateGameData(queryClient: ReturnType<typeof useQueryClient>) {
  return Promise.all(
    ['games', 'stats', 'bankroll'].map((key) => queryClient.invalidateQueries({ queryKey: [key] })),
  );
}
