import { Badge, Group, Table, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import { CompactTable, type CompactRow } from '../components/CompactTable';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { RoomLabel } from '../components/RoomLabel';
import { useFormat } from '../format/useFormat';
import type { FiguresIn, RoomsTableData } from './roomsTableData';

interface RoomsTableProps {
  data: RoomsTableData;
  /** Heading of the last column: the bankroll now, or what it changed in a period. */
  bankrollLabel: string;
}

/**
 * The bankroll room by room: what was put in and taken out, what was won or lost, and what is
 * left. Movements that belong to no room have their own row. Each row is in its own currency;
 * the total, in several, is converted to the base currency.
 */
export function RoomsTable({ data, bankrollLabel }: RoomsTableProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const narrow = useNarrowScreen();

  const plain = (value: number, currencyCode: string) => format.money(value, currencyCode);
  const signed = (value: number, currencyCode: string, bold = false) => (
    <Text
      span
      size="sm"
      fw={bold ? 700 : 500}
      c={value > 0 ? 'teal' : value < 0 ? 'red' : undefined}
    >
      {format.signedMoney(value, currencyCode)}
    </Text>
  );
  const withoutRoomLabel = (currencyCode: string) =>
    data.mixed
      ? t('bankroll.withoutRoomIn', { currency: currencyCode })
      : t('bankroll.withoutRoom');
  const totalLabel = data.mixed
    ? t('bankroll.totalIn', { currency: data.total.currencyCode })
    : t('dashboard.total');

  function cells({ figures, currencyCode }: FiguresIn, bold: boolean) {
    return (
      <>
        <Table.Td ta="right">{plain(figures.deposited, currencyCode)}</Table.Td>
        <Table.Td ta="right">{plain(figures.withdrawn, currencyCode)}</Table.Td>
        <Table.Td ta="right">{plain(figures.bonuses, currencyCode)}</Table.Td>
        <Table.Td ta="right">{signed(figures.adjustments, currencyCode, bold)}</Table.Td>
        <Table.Td ta="right">{signed(figures.gamesNet, currencyCode, bold)}</Table.Td>
        <Table.Td ta="right">{signed(figures.result, currencyCode, bold)}</Table.Td>
        <Table.Td ta="right">{signed(figures.bankroll, currencyCode, bold)}</Table.Td>
      </>
    );
  }

  const roomLabel = (room: RoomsTableData['rooms'][number]) => (
    <Group gap="xs" wrap={narrow ? undefined : 'nowrap'}>
      <RoomLabel room={room.room} />
      {!room.active && (
        <Badge size="xs" variant="light" color="gray">
          {t('bankroll.inactive')}
        </Badge>
      )}
    </Group>
  );

  if (narrow) {
    // On a phone: the result and the bankroll; what they are made of unfolds under the row.
    const compact = (
      key: string,
      label: CompactRow['label'],
      name: string,
      { figures, currencyCode }: FiguresIn,
      bold: boolean,
    ): CompactRow => ({
      key,
      label,
      name,
      cells: [
        signed(figures.result, currencyCode, bold),
        signed(figures.bankroll, currencyCode, bold),
      ],
      details: [
        {
          label: t('bankroll.columns.deposited'),
          value: plain(figures.deposited, currencyCode),
        },
        {
          label: t('bankroll.columns.withdrawn'),
          value: plain(figures.withdrawn, currencyCode),
        },
        { label: t('bankroll.columns.bonuses'), value: plain(figures.bonuses, currencyCode) },
        {
          label: t('bankroll.columns.adjustments'),
          value: signed(figures.adjustments, currencyCode),
        },
        {
          label: t('bankroll.columns.gamesNet'),
          value: signed(figures.gamesNet, currencyCode),
        },
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
          ...data.rooms.map((room) =>
            compact(String(room.room.id), roomLabel(room), room.room.name, room, false),
          ),
          ...data.withoutRoom.map((without) =>
            compact(
              `withoutRoom-${without.currencyCode}`,
              <Text size="sm" c="dimmed">
                {withoutRoomLabel(without.currencyCode)}
              </Text>,
              withoutRoomLabel(without.currencyCode),
              without,
              false,
            ),
          ),
        ]}
        foot={compact('total', totalLabel, totalLabel, data.total, true)}
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
          {data.rooms.map((room) => (
            <Table.Tr key={room.room.id}>
              <Table.Th scope="row" fw={400}>
                {roomLabel(room)}
              </Table.Th>
              {cells(room, false)}
            </Table.Tr>
          ))}
          {data.withoutRoom.map((without) => (
            <Table.Tr key={without.currencyCode}>
              <Table.Th scope="row" fw={400}>
                <Text size="sm" c="dimmed">
                  {withoutRoomLabel(without.currencyCode)}
                </Text>
              </Table.Th>
              {cells(without, false)}
            </Table.Tr>
          ))}
        </Table.Tbody>
        <Table.Tfoot>
          <Table.Tr fw={700}>
            <Table.Th scope="row">{totalLabel}</Table.Th>
            {cells(data.total, true)}
          </Table.Tr>
        </Table.Tfoot>
      </Table>
    </Table.ScrollContainer>
  );
}
