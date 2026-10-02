import { Group, Text, Tooltip } from '@mantine/core';
import { IconNote, IconTicket } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

import type { Game } from '../api/types';
import { useFormat } from '../format/useFormat';
import { variantLabel } from './labels';

/** Date of a game, with its start time when it has one: below it, or next to it (`inline`). */
export function GameDate({ game, inline = false }: { game: Game; inline?: boolean }) {
  const format = useFormat();
  return (
    <>
      <Text span size="sm" style={{ whiteSpace: 'nowrap' }}>
        {format.date(game.playedOn)}
      </Text>
      {game.playedAt && (
        <Text size="xs" c="dimmed" span={inline}>
          {format.time(game.playedAt)}
        </Text>
      )}
    </>
  );
}

/** Name of a game (its type when it has none) and, below, type, variant and modality. */
export function GameName({ game }: { game: Game }) {
  const { t } = useTranslation();
  const type = t(`gameTypes.${game.gameType}`);
  const details = [
    game.name ? type : null,
    game.variant ? variantLabel(t, game.variant) : null,
    // Hold'em is what is played by default: only the other modalities are worth showing.
    game.modality === 'NLHE' ? null : t(`modalities.${game.modality}`),
  ].filter(Boolean);

  return (
    <>
      <Group gap={6} wrap="nowrap">
        <Text span size="sm">
          {game.name ?? type}
        </Text>
        {game.notes && (
          <Tooltip label={game.notes} multiline maw={320} withArrow>
            <IconNote
              size={15}
              stroke={1.5}
              role="img"
              aria-label={t('games.notes', { notes: game.notes })}
            />
          </Tooltip>
        )}
      </Group>
      {details.length > 0 && (
        <Text size="xs" c="dimmed">
          {details.join(' · ')}
        </Text>
      )}
    </>
  );
}

/** Buy-in of a game, with its entries when there are several and whether a ticket paid one. */
export function GameBuyIn({ game }: { game: Game }) {
  const { t } = useTranslation();
  const format = useFormat();
  return (
    <Group gap={6} wrap="nowrap" justify="flex-end">
      {game.paidWithTicket && (
        <Tooltip label={t('games.paidWithTicket')} withArrow>
          <IconTicket size={15} stroke={1.5} role="img" aria-label={t('games.paidWithTicket')} />
        </Tooltip>
      )}
      <Text span size="sm" style={{ whiteSpace: 'nowrap' }}>
        {format.money(game.buyIn, game.currencyCode)}
        {game.entries > 1 && (
          <Text span size="xs" c="dimmed">
            {' '}
            ×{game.entries}
          </Text>
        )}
      </Text>
    </Group>
  );
}

/**
 * What a game won. With bounties, the prize, the bounties and their total, to see how much came
 * from each; otherwise just the prize. A ticket won is shown apart: it is not money.
 */
export function GameWinnings({ game }: { game: Game }) {
  const { t } = useTranslation();
  const format = useFormat();
  const money = (amount: number) => format.money(amount, game.currencyCode);
  const ticket = game.ticketPrizeValue > 0 && (
    <Tooltip
      label={[t('games.ticketWon', { value: money(game.ticketPrizeValue) }), game.ticketDescription]
        .filter(Boolean)
        .join(' · ')}
      withArrow
    >
      <IconTicket
        size={15}
        stroke={1.5}
        role="img"
        aria-label={t('games.ticketWon', { value: money(game.ticketPrizeValue) })}
      />
    </Tooltip>
  );

  if (game.bounty > 0) {
    return (
      <div style={{ whiteSpace: 'nowrap' }}>
        <Text size="xs" c="dimmed">
          {t('games.prizePart', { amount: money(game.prize) })}
        </Text>
        <Text size="xs" c="dimmed">
          {t('games.bountyPart', { amount: money(game.bounty) })}
        </Text>
        <Group gap={6} wrap="nowrap" justify="flex-end">
          {ticket}
          <Text span size="sm" fw={500}>
            {money(game.won)}
          </Text>
        </Group>
      </div>
    );
  }
  return (
    <Group gap={6} wrap="nowrap" justify="flex-end">
      {ticket}
      <Text span size="sm" style={{ whiteSpace: 'nowrap' }}>
        {money(game.won)}
      </Text>
    </Group>
  );
}

/** Net of a game: green when it won money, red when it lost. */
export function GameNet({ game }: { game: Game }) {
  const format = useFormat();
  return (
    <Text
      span
      size="sm"
      fw={500}
      c={game.net > 0 ? 'teal' : game.net < 0 ? 'red' : undefined}
      style={{ whiteSpace: 'nowrap' }}
    >
      {format.signedMoney(game.net, game.currencyCode)}
    </Text>
  );
}
