import { ActionIcon, Center, Group, Table, Text, UnstyledButton } from '@mantine/core';
import {
  IconChevronDown,
  IconChevronUp,
  IconCopy,
  IconPencil,
  IconSelector,
  IconTrash,
} from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import type { Game } from '../api/types';
import { RoomLabel } from '../components/RoomLabel';
import { GameBuyIn, GameDate, GameName, GameNet, GameWinnings } from './GameCells';
import { describeGame } from './labels';
import type { SortField } from './useGameFilters';

interface GamesTableProps {
  games: Game[];
  sortField: SortField;
  sortDescending: boolean;
  onSort: (field: SortField, descending: boolean) => void;
  onEdit: (game: Game) => void;
  onDuplicate: (game: Game) => void;
  onDelete: (game: Game) => void;
}

/** The finished games of a page, ordered by the column the user chose. */
export function GamesTable({
  games,
  sortField,
  sortDescending,
  onSort,
  onEdit,
  onDuplicate,
  onDelete,
}: GamesTableProps) {
  const { t } = useTranslation();

  function sortableHeader(field: SortField, label: string, alignRight = false) {
    const sorted = sortField === field;
    const Icon = !sorted ? IconSelector : sortDescending ? IconChevronDown : IconChevronUp;
    return (
      <Table.Th
        ta={alignRight ? 'right' : undefined}
        aria-sort={!sorted ? 'none' : sortDescending ? 'descending' : 'ascending'}
      >
        <UnstyledButton
          // A new column starts with the largest or latest first; the same one is reversed.
          onClick={() => onSort(field, sorted ? !sortDescending : true)}
          fw={700}
          fz="sm"
        >
          <Group gap={4} wrap="nowrap" justify={alignRight ? 'flex-end' : undefined}>
            {label}
            <Center c={sorted ? undefined : 'dimmed'}>
              <Icon size={14} stroke={1.5} />
            </Center>
          </Group>
        </UnstyledButton>
      </Table.Th>
    );
  }

  return (
    <Table.ScrollContainer minWidth={760}>
      <Table verticalSpacing="xs" highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            {sortableHeader('playedOn', t('games.columns.date'))}
            <Table.Th>{t('games.columns.game')}</Table.Th>
            <Table.Th>{t('games.columns.room')}</Table.Th>
            {sortableHeader('buyIn', t('games.columns.buyIn'), true)}
            {sortableHeader('won', t('games.columns.prize'), true)}
            {sortableHeader('net', t('games.columns.net'), true)}
            <Table.Th>
              <Text span size="sm" fw={700} visibleFrom="xs" style={{ visibility: 'hidden' }}>
                {t('games.columns.actions')}
              </Text>
            </Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {games.map((game) => (
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
                <GameWinnings game={game} />
              </Table.Td>
              <Table.Td ta="right">
                <GameNet game={game} />
              </Table.Td>
              <Table.Td>
                <RowActions>
                  <ActionIcon
                    variant="subtle"
                    color="gray"
                    aria-label={t('games.actions.editGame', { game: describeGame(t, game) })}
                    onClick={() => onEdit(game)}
                  >
                    <IconPencil size={16} stroke={1.5} />
                  </ActionIcon>
                  <ActionIcon
                    variant="subtle"
                    color="gray"
                    aria-label={t('games.actions.duplicateGame', { game: describeGame(t, game) })}
                    onClick={() => onDuplicate(game)}
                  >
                    <IconCopy size={16} stroke={1.5} />
                  </ActionIcon>
                  <ActionIcon
                    variant="subtle"
                    color="red"
                    aria-label={t('games.actions.deleteGame', { game: describeGame(t, game) })}
                    onClick={() => onDelete(game)}
                  >
                    <IconTrash size={16} stroke={1.5} />
                  </ActionIcon>
                </RowActions>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Table.ScrollContainer>
  );
}

function RowActions({ children }: { children: ReactNode }) {
  return (
    <Group gap={4} wrap="nowrap" justify="flex-end">
      {children}
    </Group>
  );
}
