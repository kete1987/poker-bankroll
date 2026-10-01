import {
  Alert,
  Button,
  Checkbox,
  Group,
  Modal,
  NumberInput,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { FinishGameRequest, Game } from '../api/types';
import { useSubmit } from '../components/useSubmit';
import { useFormat } from '../format/useFormat';
import { amountOrNull, type Amount } from './amount';
import { describeGame } from './labels';

interface FinishGameDialogProps {
  game: Game;
  onFinish: (result: FinishGameRequest) => Promise<unknown>;
  onClose: () => void;
}

/**
 * The result of a game in play. A cash game ends with what was taken from the table, nothing
 * else; a tournament or Sit & Go with its prize, bounties and maybe a ticket. Left empty,
 * nothing was won.
 */
export function FinishGameDialog({ game, onFinish, onClose }: FinishGameDialogProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const { run, busy, failure } = useSubmit();
  const isCash = game.gameType === 'CASH';

  const [prize, setPrize] = useState<Amount>('');
  const [bounty, setBounty] = useState<Amount>('');
  const [wonTicket, setWonTicket] = useState(false);
  const [ticketValue, setTicketValue] = useState<Amount>('');
  const [ticketDescription, setTicketDescription] = useState('');
  const [ticketError, setTicketError] = useState<string | null>(null);

  const amountProps = {
    min: 0,
    decimalScale: 2,
    decimalSeparator: format.decimalSeparator,
    allowedDecimalSeparators: [',', '.'],
    hideControls: true,
    placeholder: '0',
    rightSection: (
      <Text size="xs" c="dimmed">
        {game.currencyCode}
      </Text>
    ),
    rightSectionWidth: 48,
    rightSectionPointerEvents: 'none' as const,
  };

  function submit() {
    if (wonTicket && !(Number(ticketValue) > 0)) {
      setTicketError(t('gameForm.errors.ticketValue'));
      return;
    }
    void run(async () => {
      await onFinish(
        isCash
          ? { prize: amountOrNull(prize) }
          : {
              prize: amountOrNull(prize),
              bounty: amountOrNull(bounty),
              ticketPrizeValue: wonTicket ? amountOrNull(ticketValue) : null,
              ticketDescription: wonTicket ? ticketDescription.trim() || null : null,
            },
      );
      onClose();
    });
  }

  return (
    <Modal
      opened
      onClose={onClose}
      title={t('games.finish.title')}
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
            {t('games.finish.description', {
              game: describeGame(t, game),
              invested: format.money(game.invested, game.currencyCode),
            })}
          </Text>
          <SimpleGrid cols={{ base: 1, xs: isCash ? 1 : 2 }}>
            <NumberInput
              data-autofocus
              label={isCash ? t('gameForm.prizeCash') : t('gameForm.prize')}
              description={isCash ? t('games.finish.cashHelp') : undefined}
              {...amountProps}
              value={prize}
              onChange={setPrize}
            />
            {!isCash && (
              <NumberInput
                label={t('gameForm.bounty')}
                {...amountProps}
                value={bounty}
                onChange={setBounty}
              />
            )}
          </SimpleGrid>
          {!isCash && (
            <Checkbox
              label={t('gameForm.wonTicket')}
              description={t('gameForm.wonTicketHelp')}
              checked={wonTicket}
              onChange={(event) => {
                setWonTicket(event.currentTarget.checked);
                setTicketError(null);
              }}
            />
          )}
          {!isCash && wonTicket && (
            <SimpleGrid cols={{ base: 1, xs: 2 }}>
              <NumberInput
                label={t('gameForm.ticketPrizeValue')}
                required
                {...amountProps}
                placeholder={undefined}
                value={ticketValue}
                error={ticketError}
                onChange={(value) => {
                  setTicketValue(value);
                  setTicketError(null);
                }}
              />
              <TextInput
                label={t('gameForm.ticketDescription')}
                maxLength={150}
                value={ticketDescription}
                onChange={(event) => setTicketDescription(event.currentTarget.value)}
              />
            </SimpleGrid>
          )}
          <Text size="xs" c="dimmed">
            {t('gameForm.finishedHelp')}
          </Text>
          <Group justify="flex-end" gap="sm">
            <Button variant="subtle" color="gray" onClick={onClose} disabled={busy}>
              {t('actions.cancel')}
            </Button>
            <Button type="submit" loading={busy}>
              {t('games.actions.finish')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
