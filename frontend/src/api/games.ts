import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from './client';
import type {
  FinishGameRequest,
  Game,
  GamePage,
  GameRequest,
  GameStatus,
  GameType,
  RebuyRequest,
} from './types';

/** What a list of games is asked for; everything is optional. */
export interface GameQuery {
  from?: string;
  to?: string;
  /** Games of any of these types (the same goes for rooms and variants); empty is no filter. */
  gameType?: GameType[];
  roomId?: number[];
  variantId?: number[];
  status?: GameStatus;
  q?: string;
  page?: number;
  size?: number;
  /** `<field>,<asc|desc>` with field one of playedOn, net, buyIn, prize, won, createdAt. */
  sort?: string;
}

function toQueryString(query: GameQuery): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (Array.isArray(value)) {
      // A list is sent as the parameter repeated once per value.
      value.forEach((item) => params.append(key, String(item)));
    } else if (value !== undefined && value !== '') {
      params.set(key, String(value));
    }
  }
  return params.toString();
}

/** One page of games. While another page or filter loads, the previous one stays on screen. */
export function useGames(query: GameQuery) {
  return useQuery({
    queryKey: ['games', 'list', query],
    queryFn: () => apiFetch<GamePage>(`/games?${toQueryString(query)}`),
    placeholderData: keepPreviousData,
  });
}

/** The games without a result yet, newest first. They are few: one page holds them all. */
export function useGamesInPlay() {
  return useGames({ status: 'IN_PLAY', size: 200, sort: 'playedOn,desc' });
}

/** Mutations on games; every list and figure that depends on games is refreshed afterwards. */
function useGameMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TInput>({
    mutationFn,
    // Games change statistics and the bankroll too.
    onSuccess: async () => {
      await Promise.all(
        ['games', 'stats', 'bankroll'].map((key) =>
          queryClient.invalidateQueries({ queryKey: [key] }),
        ),
      );
    },
  });
}

function send<T>(method: string, path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export function useCreateGame() {
  return useGameMutation((game: GameRequest) => send<Game>('POST', '/games', game));
}

export function useUpdateGame() {
  return useGameMutation(({ id, game }: { id: number; game: GameRequest }) =>
    send<Game>('PUT', `/games/${id}`, game),
  );
}

export function useDeleteGame() {
  return useGameMutation((id: number) => send<void>('DELETE', `/games/${id}`));
}

/** Sets the result of a game in play. */
export function useFinishGame() {
  return useGameMutation(({ id, result }: { id: number; result: FinishGameRequest }) =>
    send<Game>('POST', `/games/${id}/finish`, result),
  );
}

/** One more entry, paid in cash, in a tournament or Sit & Go in play. */
export function useAddReEntry() {
  return useGameMutation((id: number) => send<Game>('POST', `/games/${id}/re-entries`));
}

/** More money brought to the table of a cash game in play. */
export function useAddRebuy() {
  return useGameMutation(({ id, rebuy }: { id: number; rebuy: RebuyRequest }) =>
    send<Game>('POST', `/games/${id}/rebuys`, rebuy),
  );
}
