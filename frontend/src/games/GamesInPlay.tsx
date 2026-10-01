import { ActionIcon, Button, Card, Group, Stack, Table, Text, Title } from '@mantine/core';
import { IconFlag, IconPencil, IconPlus, IconTrash } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

import type { Game } from '../api/types';
import { RoomLabel } from '../components/RoomLabel';
import { useFormat } from '../format/useFormat';
import { GameBuyIn, GameDate, GameName } from './GameCells';
import { describeGame } from './labels';

interface GamesInPlayProps {
  games: Game[];
  onFinish: (game: Game) => void;
  onReEntry: (game: Game) => void;
  onRebuy: (game: Game) => void;
  onEdit: (game: Game) => void;
  onDelete: (game: Game) => void;
}

/** The games without a result yet, always on top: they are waiting for something to be done. */
export function GamesInPlay({
  games,
  onFinish,
  onReEntry,
  onRebuy,
  onEdit,
  onDelete,
}: GamesInPlayProps) {
  const { t } = useTranslation();
  const format = useFormat();

  return (
    <Card withBorder padding="sm" component="section" aria-labelledby="games-in-play-title">
      <Stack gap="xs">
        <Title order={3} size="h5" id="games-in-play-title">
          {t('games.inPlay.title', { count: games.length })}
        </Title>
        <Table.ScrollContainer minWidth={720}>
          <Table verticalSpacing="xs">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t('games.columns.date')}</Table.Th>
                <Table.Th>{t('games.columns.game')}</Table.Th>
                <Table.Th>{t('games.columns.room')}</Table.Th>
                <Table.Th ta="right">{t('games.columns.buyIn')}</Table.Th>
                <Table.Th ta="right">{t('games.columns.invested')}</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {games.map((game) => {
                const name = describeGame(t, game);
                return (
                  <Table.Tr key={game.id}>
                    <Table.Td>
                      <GameDate game={game} />
                    </Table.Td>
                    <Table.Td>
                      <GameName game={game} />
                    </Table.Td>
                    <Table.Td>
                      <RoomLabel name={game.room.name} />
                    </Table.Td>
                    <Table.Td ta="right">
                      <GameBuyIn game={game} />
                    </Table.Td>
                    <Table.Td ta="right">
                      <Text span size="sm" fw={500} style={{ whiteSpace: 'nowrap' }}>
                        {format.money(game.invested, game.currencyCode)}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Group gap="xs" wrap="nowrap" justify="flex-end">
                        {game.gameType === 'CASH' ? (
                          <Button
                            size="compact-sm"
                            variant="default"
                            leftSection={<IconPlus size={14} />}
                            aria-label={t('games.actions.rebuyGame', { game: name })}
                            onClick={() => onRebuy(game)}
                          >
                            {t('games.actions.rebuy')}
                          </Button>
                        ) : (
                          <Button
                            size="compact-sm"
                            variant="default"
                            leftSection={<IconPlus size={14} />}
                            aria-label={t('games.actions.reEntryGame', { game: name })}
                            onClick={() => onReEntry(game)}
                          >
                            {t('games.actions.reEntry')}
                          </Button>
                        )}
                        <Button
                          size="compact-sm"
                          leftSection={<IconFlag size={14} />}
                          aria-label={t('games.actions.finishGame', { game: name })}
                          onClick={() => onFinish(game)}
                        >
                          {t('games.actions.finish')}
                        </Button>
                        <ActionIcon
                          variant="subtle"
                          color="gray"
                          aria-label={t('games.actions.editGame', { game: name })}
                          onClick={() => onEdit(game)}
                        >
                          <IconPencil size={16} stroke={1.5} />
                        </ActionIcon>
                        <ActionIcon
                          variant="subtle"
                          color="red"
                          aria-label={t('games.actions.deleteGame', { game: name })}
                          onClick={() => onDelete(game)}
                        >
                          <IconTrash size={16} stroke={1.5} />
                        </ActionIcon>
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                );
              })}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      </Stack>
    </Card>
  );
}
