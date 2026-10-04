import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from './client';
import { toQueryString } from './query';
import type {
  FinishGameRequest,
  Game,
  GameBatchRequest,
  GameBatchResponse,
  GameName,
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
  /** Games of any of these types (the same goes for rooms, variants and tags); empty is no filter. */
  gameType?: GameType[];
  roomId?: number[];
  variantId?: number[];
  tagId?: number[];
  status?: GameStatus;
  q?: string;
  page?: number;
  size?: number;
  /** `<field>,<asc|desc>` with field one of playedOn, net, buyIn, prize, won, createdAt. */
  sort?: string;
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

/** Names are suggested from this many characters on; the backend answers nothing for fewer. */
export const MIN_NAME_SEARCH_LENGTH = 2;

/**
 * Names of recorded games of a type that contain the text, most used first, each with what its
 * most recent game had. Nothing is asked for a shorter text; while another text loads, the
 * previous names stay.
 */
export function useGameNames(text: string, gameType: GameType) {
  const q = text.trim();
  return useQuery({
    queryKey: ['games', 'names', gameType, q],
    queryFn: () => apiFetch<GameName[]>(`/games/names?${toQueryString({ q, gameType })}`),
    enabled: q.length >= MIN_NAME_SEARCH_LENGTH,
    placeholderData: keepPreviousData,
  });
}

/** Mutations on games; every list and figure that depends on games is refreshed afterwards. */
function useGameMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TInput>({
    mutationFn,
    // Games change statistics and the bankroll too, and the tags they are given (new ones, counts).
    onSuccess: async () => {
      await Promise.all(
        ['games', 'stats', 'bankroll', 'tags'].map((key) =>
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

/** Several games at once, all of them or none; they come back in the order sent. */
export function useCreateGames() {
  return useGameMutation((batch: GameBatchRequest) =>
    send<GameBatchResponse>('POST', '/games/batch', batch),
  );
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
