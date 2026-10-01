import { useCallback, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../api/client';

/**
 * Runs the action of a dialog once at a time (a second click or Enter while it is running is
 * ignored) and keeps the message to show when it fails.
 */
export function useSubmit() {
  const { t } = useTranslation();
  // A ref, not the state: a second submit can arrive before the state of the first is rendered.
  const running = useRef(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const run = useCallback(
    async (action: () => Promise<unknown>) => {
      if (running.current) {
        return;
      }
      running.current = true;
      setBusy(true);
      setFailure(null);
      try {
        await action();
      } catch (error) {
        setFailure(error instanceof ApiError ? error.message : t('errors.unexpected'));
      } finally {
        running.current = false;
        setBusy(false);
      }
    },
    [t],
  );

  return { run, busy, failure };
}
