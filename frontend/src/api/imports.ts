import { useMutation, useQueryClient } from '@tanstack/react-query';

import { apiFetch } from './client';
import type { GameImport } from './types';

function sendGames(file: Blob, dryRun: boolean): Promise<GameImport> {
  return apiFetch<GameImport>(`/imports/games?dryRun=${dryRun}`, {
    method: 'POST',
    // The file itself is the body, whatever type the browser gives it.
    headers: { 'Content-Type': 'text/csv' },
    body: file,
  });
}

/** Checks a CSV file of games without storing anything: what it would import and its errors. */
export function useCheckImport() {
  return useMutation<GameImport, Error, Blob>({ mutationFn: (file) => sendGames(file, true) });
}

/**
 * Imports a CSV file of games, all of them or none (`imported` tells). Rooms and variants can be
 * created with them, and games change every figure: everything is loaded again afterwards.
 */
export function useImportGames() {
  const queryClient = useQueryClient();
  return useMutation<GameImport, Error, Blob>({
    mutationFn: (file) => sendGames(file, false),
    onSuccess: async (result) => {
      if (result.imported) {
        await queryClient.invalidateQueries();
      }
    },
  });
}
