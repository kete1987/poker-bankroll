import { Group, Pagination, Stack, Table, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import type { GameType, StatsFigures } from '../api/types';
import { NO_VALUE } from '../format/format';
import { useFormat } from '../format/useFormat';

/** One period (a day, a week or a month) with its figures and the net of each game type. */
export interface PeriodRow {
  key: string;
  label: string;
  figures: StatsFigures;
  netByGameType: Partial<Record<GameType, number>>;
}

interface PeriodTableProps {
  /** Newest first. */
  rows: PeriodRow[];
  total: StatsFigures;
  totalNetByGameType: Partial<Record<GameType, number>>;
  currencyCode: string;
  /** Heading of the first column: what a row is. */
  periodLabel: string;
  /** Zero-based; a page past the end shows the last one. */
  page: number;
  onPageChange: (page: number) => void;
}

const GAME_TYPES: readonly GameType[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];
/** A month of days fits in a page. */
const PAGE_SIZE = 31;

/**
 * Results per period, with the net of each game type next to the totals: the "Totales por día"
 * and "Totales por mes" tabs of the spreadsheet in one table.
 */
export function PeriodTable({
  rows,
  total,
  totalNetByGameType,
  currencyCode,
  periodLabel,
  page,
  onPageChange,
}: PeriodTableProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const pageCount = Math.ceil(rows.length / PAGE_SIZE);
  // The page comes from the URL and the rows can shrink under it: stay within them.
  const current = Math.min(page + 1, Math.max(pageCount, 1));
  const shown = rows.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE);

  // Only the game types that were played, so a filter by type leaves no empty columns.
  const gameTypes = GAME_TYPES.filter((type) => totalNetByGameType[type] !== undefined);

  const net = (value: number | undefined, bold = false) =>
    value === undefined ? (
      <Text span size="sm" c="dimmed">
        {NO_VALUE}
      </Text>
    ) : (
      <Text
        span
        size="sm"
        fw={bold ? 700 : 500}
        c={value > 0 ? 'teal' : value < 0 ? 'red' : undefined}
      >
        {format.signedMoney(value, currencyCode)}
      </Text>
    );

  function cells(
    figures: StatsFigures,
    netByGameType: Partial<Record<GameType, number>>,
    bold: boolean,
  ) {
    return (
      <>
        <Table.Td ta="right">{format.number(figures.games)}</Table.Td>
        {gameTypes.map((type) => (
          <Table.Td key={type} ta="right">
            {net(netByGameType[type], bold)}
          </Table.Td>
        ))}
        <Table.Td ta="right">{format.money(figures.invested, currencyCode)}</Table.Td>
        <Table.Td ta="right">{format.money(figures.won, currencyCode)}</Table.Td>
        <Table.Td ta="right">{net(figures.net, bold)}</Table.Td>
        <Table.Td ta="right">{format.percent(figures.roi)}</Table.Td>
      </>
    );
  }

  return (
    <Stack gap="sm">
      <Table.ScrollContainer minWidth={760}>
        <Table verticalSpacing="xs" highlightOnHover style={{ whiteSpace: 'nowrap' }}>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{periodLabel}</Table.Th>
              <Table.Th ta="right">{t('dashboard.columns.games')}</Table.Th>
              {gameTypes.map((type) => (
                <Table.Th key={type} ta="right">
                  {t(`gameTypes.${type}`)}
                </Table.Th>
              ))}
              <Table.Th ta="right">{t('dashboard.columns.invested')}</Table.Th>
              <Table.Th ta="right">{t('dashboard.columns.won')}</Table.Th>
              <Table.Th ta="right">{t('dashboard.columns.net')}</Table.Th>
              <Table.Th ta="right">{t('dashboard.columns.roi')}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {shown.map((row) => (
              <Table.Tr key={row.key}>
                <Table.Th scope="row" fw={500}>
                  {row.label}
                </Table.Th>
                {cells(row.figures, row.netByGameType, false)}
              </Table.Tr>
            ))}
          </Table.Tbody>
          <Table.Tfoot>
            <Table.Tr fw={700}>
              <Table.Th scope="row">{t('dashboard.total')}</Table.Th>
              {cells(total, totalNetByGameType, true)}
            </Table.Tr>
          </Table.Tfoot>
        </Table>
      </Table.ScrollContainer>
      {pageCount > 1 && (
        <Group justify="flex-end">
          <Pagination
            total={pageCount}
            value={current}
            onChange={(chosen) => onPageChange(chosen - 1)}
            getControlProps={(control) => ({ 'aria-label': t(`games.list.pages.${control}`) })}
            getItemProps={(item) => ({ 'aria-label': t('games.list.pages.page', { page: item }) })}
          />
        </Group>
      )}
    </Stack>
  );
}
