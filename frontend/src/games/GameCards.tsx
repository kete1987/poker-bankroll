import { ActionIcon, Card, Group, Menu, Select, SimpleGrid, Stack, Text } from '@mantine/core';
import {
  IconDotsVertical,
  IconPencil,
  IconSortAscending,
  IconSortDescending,
  IconTrash,
} from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import type { Game } from '../api/types';
import { RoomLabel } from '../components/RoomLabel';
import { GameBuyIn, GameDate, GameName, GameNet, GameWinnings } from './GameCells';
import { describeGame } from './labels';
import { SORT_FIELDS, type SortField } from './useGameFilters';

interface GameCardsProps {
  games: Game[];
  sortField: SortField;
  sortDescending: boolean;
  onSort: (field: SortField, descending: boolean) => void;
  onEdit: (game: Game) => void;
  onDelete: (game: Game) => void;
}

/** A figure of a card with its name above it. */
export function CardFigure({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Stack gap={0} align="flex-end">
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      {children}
    </Stack>
  );
}

/** Edit and delete, behind one button: a card has no room for both. */
export function GameMenu({
  game,
  onEdit,
  onDelete,
}: {
  game: Game;
  onEdit: (game: Game) => void;
  onDelete: (game: Game) => void;
}) {
  const { t } = useTranslation();
  return (
    <Menu position="bottom-end" withinPortal>
      <Menu.Target>
        <ActionIcon
          variant="subtle"
          color="gray"
          aria-label={t('games.actions.moreFor', { game: describeGame(t, game) })}
        >
          <IconDotsVertical size={18} stroke={1.5} />
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Item leftSection={<IconPencil size={16} />} onClick={() => onEdit(game)}>
          {t('games.actions.edit')}
        </Menu.Item>
        <Menu.Item color="red" leftSection={<IconTrash size={16} />} onClick={() => onDelete(game)}>
          {t('games.actions.delete')}
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}

/**
 * The finished games of a page on a phone: one card each instead of a row of a table, so nothing
 * has to be scrolled sideways. The order is chosen above them, as there are no column headings.
 */
export function GameCards({
  games,
  sortField,
  sortDescending,
  onSort,
  onEdit,
  onDelete,
}: GameCardsProps) {
  const { t } = useTranslation();
  const columns: Record<SortField, string> = {
    playedOn: t('games.columns.date'),
    buyIn: t('games.columns.buyIn'),
    won: t('games.columns.prize'),
    net: t('games.columns.net'),
  };

  return (
    <Stack gap="xs">
      <Group gap="xs" align="flex-end" wrap="nowrap">
        <Select
          label={t('games.sort.label')}
          style={{ flex: 1 }}
          allowDeselect={false}
          data={SORT_FIELDS.map((field) => ({ value: field, label: columns[field] }))}
          value={sortField}
          onChange={(value) => onSort(value as SortField, true)}
        />
        <ActionIcon
          variant="default"
          size="input-sm"
          aria-label={sortDescending ? t('games.sort.descending') : t('games.sort.ascending')}
          onClick={() => onSort(sortField, !sortDescending)}
        >
          {sortDescending ? <IconSortDescending size={18} /> : <IconSortAscending size={18} />}
        </ActionIcon>
      </Group>

      <Stack gap="xs" component="ul" p={0} m={0} style={{ listStyle: 'none' }}>
        {games.map((game) => (
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
                  <GameMenu game={game} onEdit={onEdit} onDelete={onDelete} />
                </Group>
              </Group>
              <div>
                <GameName game={game} />
              </div>
              <SimpleGrid cols={3} spacing="xs">
                <CardFigure label={t('games.columns.buyIn')}>
                  <GameBuyIn game={game} />
                </CardFigure>
                <CardFigure label={t('games.columns.prize')}>
                  <GameWinnings game={game} />
                </CardFigure>
                <CardFigure label={t('games.columns.net')}>
                  <GameNet game={game} />
                </CardFigure>
              </SimpleGrid>
            </Stack>
          </Card>
        ))}
      </Stack>
    </Stack>
  );
}
