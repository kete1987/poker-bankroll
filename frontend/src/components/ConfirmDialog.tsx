import { Alert, Button, Group, Modal, Stack, Text } from '@mantine/core';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { useSubmit } from './useSubmit';

interface ConfirmDialogProps {
  title: string;
  /** What is about to happen, and to what. */
  children: ReactNode;
  confirmLabel: string;
  /** Red confirm button, for what cannot be undone. */
  destructive?: boolean;
  /** Something in the dialog must be done first (a box ticked): the confirm button waits for it. */
  confirmDisabled?: boolean;
  /** Does it; the dialog closes when it resolves and shows the error when it rejects. */
  onConfirm: () => Promise<unknown>;
  onClose: () => void;
}

/** Asks before doing something. Render it only while the question is open. */
export function ConfirmDialog({
  title,
  children,
  confirmLabel,
  destructive = false,
  confirmDisabled = false,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  const { t } = useTranslation();
  const { run, busy, failure } = useSubmit();

  return (
    <Modal
      opened
      onClose={onClose}
      title={title}
      size="sm"
      closeButtonProps={{ 'aria-label': t('actions.close') }}
    >
      <Stack gap="md">
        {failure && <Alert color="red">{failure}</Alert>}
        <Text size="sm" component="div">
          {children}
        </Text>
        <Group justify="flex-end" gap="sm">
          <Button variant="subtle" color="gray" onClick={onClose} disabled={busy}>
            {t('actions.cancel')}
          </Button>
          <Button
            data-autofocus
            color={destructive ? 'red' : undefined}
            loading={busy}
            disabled={confirmDisabled}
            onClick={() =>
              void run(async () => {
                await onConfirm();
                onClose();
              })
            }
          >
            {confirmLabel}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
