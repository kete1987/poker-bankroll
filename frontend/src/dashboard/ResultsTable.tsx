import { Table, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import type { StatsFigures } from '../api/types';
import { CompactTable, type CompactRow } from '../components/CompactTable';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { NO_VALUE } from '../format/format';
import { useFormat } from '../format/useFormat';

export interface ResultsRow {
  key: string;
  label: string;
  figures: StatsFigures;
}

interface ResultsTableProps {
  rows: ResultsRow[];
  total: StatsFigures;
  currencyCode: string;
}

/** Results per game type or variant, with a total: the "Totales" tab of the spreadsheet. */
export function ResultsTable({ rows, total, currencyCode }: ResultsTableProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const narrow = useNarrowScreen();
  const count = (value: number | null | undefined) =>
    value == null ? NO_VALUE : format.number(value);
  const money = (value: number | null | undefined) =>
    value == null ? NO_VALUE : format.money(value, currencyCode);

  function cells(figures: StatsFigures, bold: boolean) {
    const weight = bold ? 700 : undefined;
    return (
      <>
        <Table.Td ta="right">{count(figures.games)}</Table.Td>
        <Table.Td ta="right">{count(figures.gamesWithPrize)}</Table.Td>
        <Table.Td ta="right">{format.percent(figures.withPrizeRate)}</Table.Td>
        <Table.Td ta="right">{money(figures.averageBuyIn)}</Table.Td>
        <Table.Td ta="right">{count(figures.gamesInTheMoney)}</Table.Td>
        <Table.Td ta="right">{format.percent(figures.inTheMoneyRate)}</Table.Td>
        <Table.Td ta="right">{money(figures.invested)}</Table.Td>
        <Table.Td ta="right">{money(figures.won)}</Table.Td>
        <Table.Td ta="right">
          <Text
            span
            size="sm"
            fw={weight ?? 500}
            c={figures.net > 0 ? 'teal' : figures.net < 0 ? 'red' : undefined}
          >
            {format.signedMoney(figures.net, currencyCode)}
          </Text>
        </Table.Td>
        <Table.Td ta="right">{format.percent(figures.roi)}</Table.Td>
      </>
    );
  }

  if (narrow) {
    // On a phone: played, net and ROI; the rest of each row unfolds under it.
    const compact = (
      key: string,
      label: string,
      figures: StatsFigures,
      bold: boolean,
    ): CompactRow => ({
      key,
      label,
      name: label,
      cells: [
        count(figures.games),
        <Text
          key="net"
          span
          size="sm"
          fw={bold ? 700 : 500}
          c={figures.net > 0 ? 'teal' : figures.net < 0 ? 'red' : undefined}
        >
          {format.signedMoney(figures.net, currencyCode)}
        </Text>,
        format.percent(figures.roi),
      ],
      details: [
        { label: t('dashboard.columns.withPrize'), value: count(figures.gamesWithPrize) },
        {
          label: t('dashboard.columns.withPrizeRate'),
          value: format.percent(figures.withPrizeRate),
        },
        { label: t('dashboard.columns.inTheMoney'), value: count(figures.gamesInTheMoney) },
        {
          label: t('dashboard.columns.inTheMoneyRate'),
          value: format.percent(figures.inTheMoneyRate),
        },
        { label: t('dashboard.columns.averageBuyIn'), value: money(figures.averageBuyIn) },
        { label: t('dashboard.columns.invested'), value: money(figures.invested) },
        { label: t('dashboard.columns.won'), value: money(figures.won) },
      ],
    });
    return (
      <CompactTable
        head={
          <>
            <Table.Th />
            <Table.Th ta="right">{t('dashboard.columns.games')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.net')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.roi')}</Table.Th>
          </>
        }
        rows={rows.map((row) => compact(row.key, row.label, row.figures, false))}
        foot={compact('total', t('dashboard.total'), total, true)}
      />
    );
  }

  return (
    <Table.ScrollContainer minWidth={900}>
      <Table verticalSpacing="xs" highlightOnHover style={{ whiteSpace: 'nowrap' }}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th />
            <Table.Th ta="right">{t('dashboard.columns.games')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.withPrize')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.withPrizeRate')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.averageBuyIn')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.inTheMoney')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.inTheMoneyRate')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.invested')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.won')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.net')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.columns.roi')}</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((row) => (
            <Table.Tr key={row.key}>
              <Table.Th scope="row" fw={500}>
                {row.label}
              </Table.Th>
              {cells(row.figures, false)}
            </Table.Tr>
          ))}
        </Table.Tbody>
        <Table.Tfoot>
          <Table.Tr fw={700}>
            <Table.Th scope="row">{t('dashboard.total')}</Table.Th>
            {cells(total, true)}
          </Table.Tr>
        </Table.Tfoot>
      </Table>
    </Table.ScrollContainer>
  );
}
