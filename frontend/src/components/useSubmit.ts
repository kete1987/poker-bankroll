import { useCallback, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError, type FieldViolation } from '../api/client';

/**
 * Runs the action of a dialog once at a time (a second click or Enter while it is running is
 * ignored) and keeps the message to show when it fails. Validation errors of the backend can go
 * to the fields they are about instead.
 */
export function useSubmit() {
  const { t } = useTranslation();
  // A ref, not the state: a second submit can arrive before the state of the first is rendered.
  const running = useRef(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const run = useCallback(
    async (
      action: () => Promise<unknown>,
      /**
       * Shows the validation errors of the backend next to their fields; returns whether any
       * was shown (then no message is kept for the top of the dialog).
       */
      showFieldErrors?: (violations: FieldViolation[]) => boolean,
    ) => {
      if (running.current) {
        return;
      }
      running.current = true;
      setBusy(true);
      setFailure(null);
      try {
        await action();
      } catch (error) {
        if (!(error instanceof ApiError)) {
          setFailure(t('errors.unexpected'));
        } else if (!showFieldErrors?.(error.errors.filter((violation) => violation.field))) {
          setFailure(error.message);
        }
      } finally {
        running.current = false;
        setBusy(false);
      }
    },
    [t],
  );

  return { run, busy, failure };
}
