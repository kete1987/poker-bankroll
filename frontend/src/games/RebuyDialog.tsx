import { Alert, Button, Group, Modal, NumberInput, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { Game, RebuyRequest } from '../api/types';
import { useSubmit } from '../components/useSubmit';
import { useFormat } from '../format/useFormat';
import { amountOrNull, type Amount } from './amount';

interface RebuyDialogProps {
  game: Game;
  onRebuy: (rebuy: RebuyRequest) => Promise<unknown>;
  onClose: () => void;
}

/** More money brought to the table of a cash game in play: it is added to its buy-in. */
export function RebuyDialog({ game, onRebuy, onClose }: RebuyDialogProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const { run, busy, failure } = useSubmit();
  const [amount, setAmount] = useState<Amount>('');
  const [error, setError] = useState<string | null>(null);

  function submit() {
    const rebuy = amountOrNull(amount);
    if (rebuy === null || rebuy <= 0) {
      setError(t('games.rebuy.amountRequired'));
      return;
    }
    void run(async () => {
      await onRebuy({ amount: rebuy });
      onClose();
    });
  }

  return (
    <Modal
      opened
      onClose={onClose}
      title={t('games.rebuy.title')}
      size="sm"
      closeButtonProps={{ 'aria-label': t('actions.close') }}
    >
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
      >
        <Stack gap="md">
          {failure && <Alert color="red">{failure}</Alert>}
          <Text size="sm">
            {t('games.rebuy.description', {
              amount: format.money(game.buyIn, game.currencyCode),
            })}
          </Text>
          <NumberInput
            data-autofocus
            label={t('games.rebuy.amount')}
            required
            min={0}
            decimalScale={2}
            decimalSeparator={format.decimalSeparator}
            allowedDecimalSeparators={[',', '.']}
            hideControls
            rightSection={
              <Text size="xs" c="dimmed">
                {game.currencyCode}
              </Text>
            }
            rightSectionWidth={48}
            rightSectionPointerEvents="none"
            value={amount}
            error={error}
            onChange={(value) => {
              setAmount(value);
              setError(null);
            }}
          />
          <Group justify="flex-end" gap="sm">
            <Button variant="subtle" color="gray" onClick={onClose} disabled={busy}>
              {t('actions.cancel')}
            </Button>
            <Button type="submit" loading={busy}>
              {t('games.actions.rebuy')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
