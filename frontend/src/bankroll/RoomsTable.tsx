import { Badge, Group, Table, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import type { BankrollFigures, CurrencyBankroll } from '../api/types';
import { CompactTable, type CompactRow } from '../components/CompactTable';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { RoomLabel } from '../components/RoomLabel';
import { useFormat } from '../format/useFormat';

interface RoomsTableProps {
  bankroll: CurrencyBankroll;
  /** Heading of the last column: the bankroll now, or what it changed in a period. */
  bankrollLabel: string;
}

function hasMovements(figures: BankrollFigures): boolean {
  return [figures.deposited, figures.withdrawn, figures.bonuses, figures.adjustments].some(
    (amount) => amount !== 0,
  );
}

/**
 * The bankroll room by room: what was put in and taken out, what was won or lost, and what is
 * left. Movements that belong to no room have their own row.
 */
export function RoomsTable({ bankroll, bankrollLabel }: RoomsTableProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const narrow = useNarrowScreen();
  const currencyCode = bankroll.currencyCode;

  const plain = (value: number) => format.money(value, currencyCode);
  const signed = (value: number, bold = false) => (
    <Text
      span
      size="sm"
      fw={bold ? 700 : 500}
      c={value > 0 ? 'teal' : value < 0 ? 'red' : undefined}
    >
      {format.signedMoney(value, currencyCode)}
    </Text>
  );

  function cells(figures: BankrollFigures, bold: boolean) {
    return (
      <>
        <Table.Td ta="right">{plain(figures.deposited)}</Table.Td>
        <Table.Td ta="right">{plain(figures.withdrawn)}</Table.Td>
        <Table.Td ta="right">{plain(figures.bonuses)}</Table.Td>
        <Table.Td ta="right">{signed(figures.adjustments, bold)}</Table.Td>
        <Table.Td ta="right">{signed(figures.gamesNet, bold)}</Table.Td>
        <Table.Td ta="right">{signed(figures.result, bold)}</Table.Td>
        <Table.Td ta="right">{signed(figures.bankroll, bold)}</Table.Td>
      </>
    );
  }

  if (narrow) {
    // On a phone: the result and the bankroll; what they are made of unfolds under the row.
    const compact = (
      key: string,
      label: CompactRow['label'],
      name: string,
      figures: BankrollFigures,
      bold: boolean,
    ): CompactRow => ({
      key,
      label,
      name,
      cells: [signed(figures.result, bold), signed(figures.bankroll, bold)],
      details: [
        { label: t('bankroll.columns.deposited'), value: plain(figures.deposited) },
        { label: t('bankroll.columns.withdrawn'), value: plain(figures.withdrawn) },
        { label: t('bankroll.columns.bonuses'), value: plain(figures.bonuses) },
        { label: t('bankroll.columns.adjustments'), value: signed(figures.adjustments) },
        { label: t('bankroll.columns.gamesNet'), value: signed(figures.gamesNet) },
      ],
    });
    return (
      <CompactTable
        head={
          <>
            <Table.Th>{t('bankroll.columns.room')}</Table.Th>
            <Table.Th ta="right">{t('bankroll.columns.result')}</Table.Th>
            <Table.Th ta="right">{bankrollLabel}</Table.Th>
          </>
        }
        rows={[
          ...bankroll.rooms.map((room) =>
            compact(
              String(room.room.id),
              <Group gap="xs">
                <RoomLabel room={room.room} />
                {!room.active && (
                  <Badge size="xs" variant="light" color="gray">
                    {t('bankroll.inactive')}
                  </Badge>
                )}
              </Group>,
              room.room.name,
              room.figures,
              false,
            ),
          ),
          ...(hasMovements(bankroll.withoutRoom)
            ? [
                compact(
                  'withoutRoom',
                  <Text size="sm" c="dimmed">
                    {t('bankroll.withoutRoom')}
                  </Text>,
                  t('bankroll.withoutRoom'),
                  bankroll.withoutRoom,
                  false,
                ),
              ]
            : []),
        ]}
        foot={compact('total', t('dashboard.total'), t('dashboard.total'), bankroll.total, true)}
      />
    );
  }

  return (
    <Table.ScrollContainer minWidth={860}>
      <Table verticalSpacing="xs" highlightOnHover style={{ whiteSpace: 'nowrap' }}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('bankroll.columns.room')}</Table.Th>
            <Table.Th ta="right">{t('bankroll.columns.deposited')}</Table.Th>
            <Table.Th ta="right">{t('bankroll.columns.withdrawn')}</Table.Th>
            <Table.Th ta="right">{t('bankroll.columns.bonuses')}</Table.Th>
            <Table.Th ta="right">{t('bankroll.columns.adjustments')}</Table.Th>
            <Table.Th ta="right">{t('bankroll.columns.gamesNet')}</Table.Th>
            <Table.Th ta="right">{t('bankroll.columns.result')}</Table.Th>
            <Table.Th ta="right">{bankrollLabel}</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {bankroll.rooms.map((room) => (
            <Table.Tr key={room.room.id}>
              <Table.Th scope="row" fw={400}>
                <Group gap="xs" wrap="nowrap">
                  <RoomLabel room={room.room} />
                  {!room.active && (
                    <Badge size="xs" variant="light" color="gray">
                      {t('bankroll.inactive')}
                    </Badge>
                  )}
                </Group>
              </Table.Th>
              {cells(room.figures, false)}
            </Table.Tr>
          ))}
          {hasMovements(bankroll.withoutRoom) && (
            <Table.Tr>
              <Table.Th scope="row" fw={400}>
                <Text size="sm" c="dimmed">
                  {t('bankroll.withoutRoom')}
                </Text>
              </Table.Th>
              {cells(bankroll.withoutRoom, false)}
            </Table.Tr>
          )}
        </Table.Tbody>
        <Table.Tfoot>
          <Table.Tr fw={700}>
            <Table.Th scope="row">{t('dashboard.total')}</Table.Th>
            {cells(bankroll.total, true)}
          </Table.Tr>
        </Table.Tfoot>
      </Table>
    </Table.ScrollContainer>
  );
}
