import { Button, Card, Group, Stack, Table, Text, Title } from '@mantine/core';
import {
  IconBookmarkPlus,
  IconCopy,
  IconFlag,
  IconPencil,
  IconPlus,
  IconTrash,
} from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

import type { Game } from '../api/types';
import { IconButton } from '../components/IconButton';
import { RoomLabel } from '../components/RoomLabel';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { useFormat } from '../format/useFormat';
import { CardFigure, GameMenu } from './GameCards';
import { GameBuyIn, GameDate, GameName } from './GameCells';
import { describeGame } from './labels';

interface GamesInPlayProps {
  games: Game[];
  /** How many there are in all, when more than the ones listed. */
  total: number;
  onFinish: (game: Game) => void;
  onReEntry: (game: Game) => void;
  onRebuy: (game: Game) => void;
  onEdit: (game: Game) => void;
  onDuplicate: (game: Game) => void;
  onSaveAsTemplate: (game: Game) => void;
  onDelete: (game: Game) => void;
}

/** The games without a result yet, always on top: they are waiting for something to be done. */
export function GamesInPlay({
  games,
  total,
  onFinish,
  onReEntry,
  onRebuy,
  onEdit,
  onDuplicate,
  onSaveAsTemplate,
  onDelete,
}: GamesInPlayProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const narrow = useNarrowScreen();

  /** What can be done with a game in play: one more entry (or money at the table) and finish it. */
  function mainActions(game: Game, name: string, grow: boolean) {
    const add =
      game.gameType === 'CASH'
        ? {
            label: t('games.actions.rebuy'),
            aria: t('games.actions.rebuyGame', { game: name }),
            run: onRebuy,
          }
        : {
            label: t('games.actions.reEntry'),
            aria: t('games.actions.reEntryGame', { game: name }),
            run: onReEntry,
          };
    return (
      <>
        <Button
          size="compact-sm"
          variant="default"
          style={grow ? { flex: 1 } : undefined}
          leftSection={<IconPlus size={14} />}
          aria-label={add.aria}
          onClick={() => add.run(game)}
        >
          {add.label}
        </Button>
        <Button
          size="compact-sm"
          style={grow ? { flex: 1 } : undefined}
          leftSection={<IconFlag size={14} />}
          aria-label={t('games.actions.finishGame', { game: name })}
          onClick={() => onFinish(game)}
        >
          {t('games.actions.finish')}
        </Button>
      </>
    );
  }

  return (
    <Card withBorder padding="sm" component="section" aria-labelledby="games-in-play-title">
      <Stack gap="xs">
        <Title order={3} size="h5" id="games-in-play-title">
          {t('games.inPlay.title', { count: total })}
        </Title>
        {total > games.length && (
          <Text size="xs" c="dimmed">
            {t('games.inPlay.truncated', { count: games.length })}
          </Text>
        )}
        {narrow ? (
          <Stack gap="xs" component="ul" p={0} m={0} style={{ listStyle: 'none' }}>
            {games.map((game) => {
              const name = describeGame(t, game);
              return (
                <Card
                  key={game.id}
                  withBorder
                  padding="sm"
                  component="li"
                  // What the user wrote may be one long word: it breaks instead of widening the page.
                  style={{ overflowWrap: 'anywhere' }}
                >
                  <Stack gap={6}>
                    <Group justify="space-between" wrap="nowrap" gap="xs">
                      <Group gap="xs" wrap="nowrap">
                        <GameDate game={game} inline />
                      </Group>
                      <Group gap={4} wrap="nowrap">
                        <RoomLabel room={game.room} />
                        <GameMenu
                          game={game}
                          onEdit={onEdit}
                          onDuplicate={onDuplicate}
                          onSaveAsTemplate={onSaveAsTemplate}
                          onDelete={onDelete}
                        />
                      </Group>
                    </Group>
                    <Group justify="space-between" wrap="nowrap" align="flex-start" gap="xs">
                      <div>
                        <GameName game={game} />
                      </div>
                      <Group gap="md" wrap="nowrap">
                        <CardFigure label={t('games.columns.buyIn')}>
                          <GameBuyIn game={game} />
                        </CardFigure>
                        <CardFigure label={t('games.columns.invested')}>
                          <Text span size="sm" fw={500} style={{ whiteSpace: 'nowrap' }}>
                            {format.money(game.invested, game.currencyCode)}
                          </Text>
                        </CardFigure>
                      </Group>
                    </Group>
                    <Group gap="xs" wrap="nowrap">
                      {mainActions(game, name, true)}
                    </Group>
                  </Stack>
                </Card>
              );
            })}
          </Stack>
        ) : (
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
                        <RoomLabel room={game.room} />
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
                          {mainActions(game, name, false)}
                          <IconButton
                            variant="subtle"
                            color="gray"
                            label={t('games.actions.editGame', { game: name })}
                            onClick={() => onEdit(game)}
                          >
                            <IconPencil size={16} stroke={1.5} />
                          </IconButton>
                          <IconButton
                            variant="subtle"
                            color="gray"
                            label={t('games.actions.duplicateGame', { game: name })}
                            onClick={() => onDuplicate(game)}
                          >
                            <IconCopy size={16} stroke={1.5} />
                          </IconButton>
                          <IconButton
                            variant="subtle"
                            color="gray"
                            label={t('games.actions.saveAsTemplateGame', { game: name })}
                            onClick={() => onSaveAsTemplate(game)}
                          >
                            <IconBookmarkPlus size={16} stroke={1.5} />
                          </IconButton>
                          <IconButton
                            variant="subtle"
                            color="red"
                            label={t('games.actions.deleteGame', { game: name })}
                            onClick={() => onDelete(game)}
                          >
                            <IconTrash size={16} stroke={1.5} />
                          </IconButton>
                        </Group>
                      </Table.Td>
                    </Table.Tr>
                  );
                })}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Stack>
    </Card>
  );
}
