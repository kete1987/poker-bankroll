import { useMutation, useQueryClient } from '@tanstack/react-query';

import { apiFetch, apiFetchFile, type ApiFile } from './client';
import { toQueryString } from './query';
import type { BackupRestore } from './types';

/** Everything the installation holds, as one file to keep or to restore somewhere else. */
export function downloadBackup(): Promise<ApiFile> {
  return apiFetchFile('/backup');
}

interface RestoreOptions {
  dryRun: boolean;
  /** Delete what the installation holds: needed unless it is empty. */
  replace: boolean;
}

function sendBackup(file: Blob, options: RestoreOptions): Promise<BackupRestore> {
  return apiFetch<BackupRestore>(`/backup/restore?${toQueryString({ ...options })}`, {
    method: 'POST',
    // The file itself is the body, whatever type the browser gives it.
    headers: { 'Content-Type': 'application/json' },
    body: file,
  });
}

/**
 * Checks a backup file without changing anything: what it holds, what the installation holds and
 * would lose, and the errors of the file.
 */
export function useCheckBackup() {
  return useMutation<BackupRestore, Error, Blob>({
    mutationFn: (file) => sendBackup(file, { dryRun: true, replace: false }),
  });
}

/**
 * Restores a backup file, all of it or nothing (`restored` tells). It replaces everything the
 * installation holds, which must be asked for with `replace` unless it is empty; afterwards
 * nothing loaded before is true any more, so everything is loaded again.
 */
export function useRestoreBackup() {
  const queryClient = useQueryClient();
  return useMutation<BackupRestore, Error, { file: Blob; replace: boolean }>({
    mutationFn: ({ file, replace }) => sendBackup(file, { dryRun: false, replace }),
    onSuccess: async (result) => {
      if (result.restored) {
        await queryClient.invalidateQueries();
      }
    },
  });
}
