import {
  Alert,
  Group,
  Loader,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  UnstyledButton,
  useMantineTheme,
} from '@mantine/core';
import { IconArrowDown, IconArrowUp, IconSearch } from '@tabler/icons-react';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useStatsGroups, type StatsQuery } from '../api/stats';
import type { StatsFigures, StatsGroup } from '../api/types';
import { Chart } from '../components/Chart';
import { RoomLabel } from '../components/RoomLabel';
import { NO_VALUE } from '../format/format';
import { useFormat } from '../format/useFormat';
import { variantLabel } from '../games/labels';
import { DIMENSIONS, type BreakdownSort, type Dimension, type SortColumn } from './useStatsFilters';

/** Names listed before any is searched for, and bars drawn. */
export const NAMES_SHOWN = 50;
export const BARS_SHOWN = 15;

/**
 * Groups that come in an order of their own, kept until a column is chosen: ranges from the
 * lowest, days from Monday, names by games with those without a name last.
 */
const ORDERED: readonly Dimension[] = ['BUY_IN_RANGE', 'WEEKDAY', 'NAME'];

interface Row {
  key: string;
  label: string;
  /** Set when the group is a room, which is shown with its logo. */
  room?: { id: number; name: string };
  figures: StatsFigures;
}

interface BreakdownProps {
  /** The games the statistics are about. */
  query: StatsQuery;
  currencyCode: string;
  dimension: Dimension;
  /** The column chosen by the user; without one, the order the groups come in. */
  sort?: BreakdownSort;
  onChange: (changes: { dimension?: Dimension; sort?: BreakdownSort }) => void;
}

/** The figure of a row a column sorts by; `null` when it has none (it then goes last). */
function valueOf(row: Row, column: SortColumn): number | string | null {
  switch (column) {
    case 'label':
      return row.label.toLowerCase();
    case 'itm':
      return row.figures.inTheMoneyRate ?? null;
    case 'roi':
      return row.figures.roi ?? null;
    case 'averageBuyIn':
      return row.figures.averageBuyIn ?? null;
    default:
      return row.figures[column];
  }
}

function sorted(rows: Row[], sort: BreakdownSort): Row[] {
  return [...rows].sort((a, b) => {
    const [x, y] = [valueOf(a, sort.column), valueOf(b, sort.column)];
    if (x === null || y === null) {
      return x === y ? 0 : x === null ? 1 : -1;
    }
    const order = typeof x === 'string' ? x.localeCompare(String(y)) : x - Number(y);
    return sort.descending ? -order : order;
  });
}

/**
 * The results of the filtered games broken down by something other than time (room, variant,
 * buy-in range, tournament name, day of the week...): the net of each group as bars and a table
 * that can be sorted by any column. Every figure comes from the backend; here they are only
 * ordered and shown.
 */
export function Breakdown({ query, currencyCode, dimension, sort, onChange }: BreakdownProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const theme = useMantineTheme();
  const [search, setSearch] = useState('');
  // Names are those of tournaments, which is where games have one, unless the filter asks for
  // other types: the breakdown never shows games the filter leaves out.
  const groups = useStatsGroups(
    dimension,
    dimension === 'NAME' && !query.gameType?.length
      ? { ...query, gameType: ['TOURNAMENT'] }
      : query,
  );
  // While another breakdown loads, the previous one stays on screen under its own name.
  const drawn = DIMENSIONS.find((value) => value === groups.data?.groupBy) ?? dimension;

  const all = useMemo(() => {
    function labelOf({ key }: StatsGroup): string {
      switch (drawn) {
        case 'GAME_TYPE':
          return key.gameType ? t(`gameTypes.${key.gameType}`) : '';
        case 'VARIANT': {
          // The same variant exists in several types (a regular tournament, a regular Sit & Go).
          const type = key.gameType ? t(`gameTypes.${key.gameType}`) : '';
          const variant = key.variant
            ? variantLabel(t, key.variant)
            : t('stats.breakdown.noVariant');
          return `${variant} · ${type}`;
        }
        case 'ROOM':
          return key.room?.name ?? '';
        case 'MODALITY':
          return key.modality ? t(`modalities.${key.modality}`) : '';
        case 'BUY_IN_RANGE': {
          const range = key.buyInRange;
          if (!range) {
            return '';
          }
          if (range.to === 0) {
            return t('stats.breakdown.free');
          }
          if (range.to == null) {
            return t('stats.breakdown.above', { amount: format.money(range.from, currencyCode) });
          }
          if (range.from === 0) {
            return t('stats.breakdown.below', { amount: format.money(range.to, currencyCode) });
          }
          // A range ends just before the next one starts.
          return t('stats.breakdown.between', {
            from: format.money(range.from, currencyCode),
            to: format.money(range.to - 0.01, currencyCode),
          });
        }
        case 'NAME':
          return key.name ?? t('stats.breakdown.noName');
        case 'WEEKDAY':
          return key.weekday ? format.weekday(key.weekday) : '';
      }
    }
    const ofCurrency =
      groups.data?.currencies.find((currency) => currency.currencyCode === currencyCode)?.groups ??
      [];
    return ofCurrency.map((group): Row => ({
      // What the group is, not how it reads: two groups can have the same label.
      key: JSON.stringify(group.key),
      label: labelOf(group),
      room: group.key.room ?? undefined,
      figures: group.figures,
    }));
  }, [groups.data, drawn, currencyCode, format, t]);

  const wanted = search.trim().toLowerCase();
  const rows = useMemo(() => {
    const ordered = sort ? sorted(all, sort) : all;
    if (drawn !== 'NAME') {
      return ordered;
    }
    return wanted
      ? ordered.filter((row) => row.label.toLowerCase().includes(wanted))
      : ordered.slice(0, NAMES_SHOWN);
  }, [all, sort, drawn, wanted]);
  const namesLeftOut = drawn === 'NAME' && !wanted ? Math.max(all.length - NAMES_SHOWN, 0) : 0;

  const option = useMemo(() => {
    // The axis grows upwards: reversed, the bars read from the top down like the table.
    const bars = rows.slice(0, BARS_SHOWN).reverse();
    return {
      grid: { left: 8, right: 24, top: 8, bottom: 8, containLabel: true },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        valueFormatter: (value: number) => format.signedMoney(value, currencyCode),
      },
      xAxis: {
        type: 'value',
        axisLabel: { formatter: (value: number) => format.number(value) },
      },
      yAxis: {
        type: 'category',
        data: bars.map((row) => row.label),
        axisLabel: { width: 220, overflow: 'truncate' },
      },
      series: [
        {
          type: 'bar',
          data: bars.map((row) => ({
            value: row.figures.net,
            itemStyle: { color: row.figures.net < 0 ? theme.colors.red[6] : theme.colors.teal[6] },
          })),
        },
      ],
    };
  }, [rows, currencyCode, format, theme]);

  // Without a column chosen, ranges and days come in their own order and the rest by games.
  const shownSort: BreakdownSort | undefined =
    sort ?? (ORDERED.includes(drawn) ? undefined : { column: 'games', descending: true });

  function header(column: SortColumn, label: string, align: 'left' | 'right' = 'right') {
    const active = shownSort?.column === column;
    const descending = active && shownSort.descending;
    return (
      <Table.Th ta={align} aria-sort={active ? (descending ? 'descending' : 'ascending') : 'none'}>
        <UnstyledButton
          fw={700}
          fz="sm"
          onClick={() =>
            onChange({
              // Figures start from the highest, names from the first; again, the other way.
              sort: { column, descending: active ? !descending : column !== 'label' },
            })
          }
        >
          <Group gap={4} wrap="nowrap" justify={align === 'right' ? 'flex-end' : 'flex-start'}>
            {label}
            {active && (descending ? <IconArrowDown size={14} /> : <IconArrowUp size={14} />)}
          </Group>
        </UnstyledButton>
      </Table.Th>
    );
  }

  const net = (value: number) => (
    <Text span size="sm" fw={500} c={value > 0 ? 'teal' : value < 0 ? 'red' : undefined}>
      {format.signedMoney(value, currencyCode)}
    </Text>
  );

  return (
    <Stack gap="sm">
      <Group gap="sm" align="flex-end">
        <Select
          label={t('stats.breakdown.by')}
          w={220}
          allowDeselect={false}
          data={DIMENSIONS.map((value) => ({
            value,
            label: t(`stats.breakdown.dimensions.${value}`),
          }))}
          value={dimension}
          // Another breakdown starts in its own order.
          onChange={(value) => onChange({ dimension: value as Dimension, sort: undefined })}
        />
        {drawn === 'NAME' && (
          <TextInput
            label={t('stats.breakdown.search')}
            w={260}
            leftSection={<IconSearch size={16} />}
            value={search}
            onChange={(event) => setSearch(event.currentTarget.value)}
          />
        )}
        {groups.isFetching && <Loader size="sm" aria-label={t('stats.breakdown.loading')} />}
      </Group>

      {groups.isError ? (
        <Alert color="red">{t('games.loadError')}</Alert>
      ) : !groups.data ? null : rows.length === 0 ? (
        <Text c="dimmed">{wanted ? t('stats.breakdown.noMatch') : t('dashboard.noGames')}</Text>
      ) : (
        <>
          <Chart
            option={option}
            height={Math.max(Math.min(rows.length, BARS_SHOWN) * 30 + 40, 140)}
          />
          <Table.ScrollContainer minWidth={760}>
            <Table verticalSpacing="xs" highlightOnHover style={{ whiteSpace: 'nowrap' }}>
              <Table.Thead>
                <Table.Tr>
                  {header('label', t(`stats.breakdown.dimensions.${drawn}`), 'left')}
                  {header('games', t('dashboard.columns.games'))}
                  {header('averageBuyIn', t('dashboard.columns.averageBuyIn'))}
                  {header('itm', t('dashboard.columns.inTheMoneyRate'))}
                  {header('invested', t('dashboard.columns.invested'))}
                  {header('won', t('dashboard.columns.won'))}
                  {header('net', t('dashboard.columns.net'))}
                  {header('roi', t('dashboard.columns.roi'))}
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {rows.map((row) => (
                  <Table.Tr key={row.key}>
                    <Table.Th scope="row" fw={500}>
                      {row.room ? <RoomLabel room={row.room} /> : row.label}
                    </Table.Th>
                    <Table.Td ta="right">{format.number(row.figures.games)}</Table.Td>
                    <Table.Td ta="right">
                      {row.figures.averageBuyIn == null
                        ? NO_VALUE
                        : format.money(row.figures.averageBuyIn, currencyCode)}
                    </Table.Td>
                    <Table.Td ta="right">{format.percent(row.figures.inTheMoneyRate)}</Table.Td>
                    <Table.Td ta="right">
                      {format.money(row.figures.invested, currencyCode)}
                    </Table.Td>
                    <Table.Td ta="right">{format.money(row.figures.won, currencyCode)}</Table.Td>
                    <Table.Td ta="right">{net(row.figures.net)}</Table.Td>
                    <Table.Td ta="right">{format.percent(row.figures.roi)}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
          {namesLeftOut > 0 && (
            <Text size="sm" c="dimmed">
              {t('stats.breakdown.more', { count: namesLeftOut })}
            </Text>
          )}
        </>
      )}
    </Stack>
  );
}
