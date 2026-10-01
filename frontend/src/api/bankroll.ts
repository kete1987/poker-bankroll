import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from './client';
import { toQueryString } from './query';
import type {
  BankrollSummary,
  Movement,
  MovementPage,
  MovementRequest,
  MovementType,
} from './types';

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

/** Which movements a list is asked for; everything is optional. */
export interface MovementQuery {
  from?: string;
  to?: string;
  type?: MovementType;
  /** Movements of any of these rooms; empty is every one, those without a room included. */
  roomId?: number[];
  /** Currency of the amount: the one of the room, or the own one of a movement without a room. */
  currency?: string;
  page?: number;
  size?: number;
}

/** One page of movements, newest first. While another one loads, the previous stays on screen. */
export function useMovements(query: MovementQuery) {
  return useQuery({
    queryKey: ['bankroll', 'movements', query],
    queryFn: () => apiFetch<MovementPage>(`/bankroll/movements?${toQueryString(query)}`),
    placeholderData: keepPreviousData,
  });
}

/** Mutations on movements; the bankroll and the rooms (in use or not) are refreshed afterwards. */
function useMovementMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TInput>({
    mutationFn,
    onSuccess: async () => {
      await Promise.all(
        ['bankroll', 'rooms'].map((key) => queryClient.invalidateQueries({ queryKey: [key] })),
      );
    },
  });
}

export function useCreateMovement() {
  return useMovementMutation((movement: MovementRequest) =>
    apiFetch<Movement>('/bankroll/movements', { method: 'POST', body: JSON.stringify(movement) }),
  );
}

export function useUpdateMovement() {
  return useMovementMutation(({ id, movement }: { id: number; movement: MovementRequest }) =>
    apiFetch<Movement>(`/bankroll/movements/${id}`, {
      method: 'PUT',
      body: JSON.stringify(movement),
    }),
  );
}

export function useDeleteMovement() {
  return useMovementMutation((id: number) =>
    apiFetch<void>(`/bankroll/movements/${id}`, { method: 'DELETE' }),
  );
}
