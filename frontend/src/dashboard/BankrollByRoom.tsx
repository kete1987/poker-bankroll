import { Table, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import type { BankrollFigures, CurrencyBankroll } from '../api/types';
import { RoomLabel } from '../components/RoomLabel';
import { NO_VALUE } from '../format/format';
import { useFormat } from '../format/useFormat';

interface BankrollByRoomProps {
  /** The bankroll as it is now. */
  now: CurrencyBankroll;
  /** Net of the finished games of the chosen period, by room id. */
  netOfPeriod: ReadonlyMap<number, number>;
  /** That net for every room listed. */
  totalNetOfPeriod: number;
}

function hasMovements(figures: BankrollFigures): boolean {
  return [figures.deposited, figures.withdrawn, figures.bonuses, figures.adjustments].some(
    (amount) => amount !== 0,
  );
}

/**
 * Each room with the net of its games in the period and its bankroll now, the movements that
 * belong to no room when there are any (they have no games), and the total.
 */
export function BankrollByRoom({ now, netOfPeriod, totalNetOfPeriod }: BankrollByRoomProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const currencyCode = now.currencyCode;

  const amount = (value: number, bold = false) => (
    <Text
      span
      size="sm"
      fw={bold ? 700 : 500}
      c={value > 0 ? 'teal' : value < 0 ? 'red' : undefined}
    >
      {format.signedMoney(value, currencyCode)}
    </Text>
  );

  return (
    <Table.ScrollContainer minWidth={420}>
      <Table verticalSpacing="xs" style={{ whiteSpace: 'nowrap' }}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('dashboard.bankroll.room')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.bankroll.periodNet')}</Table.Th>
            <Table.Th ta="right">{t('dashboard.bankroll.now')}</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {now.rooms.map((room) => (
            <Table.Tr key={room.room.id}>
              <Table.Td>
                <RoomLabel room={room.room} />
              </Table.Td>
              <Table.Td ta="right">{amount(netOfPeriod.get(room.room.id) ?? 0)}</Table.Td>
              <Table.Td ta="right">{amount(room.figures.bankroll)}</Table.Td>
            </Table.Tr>
          ))}
          {hasMovements(now.withoutRoom) && (
            <Table.Tr>
              <Table.Td>
                <Text size="sm" c="dimmed">
                  {t('dashboard.bankroll.withoutRoom')}
                </Text>
              </Table.Td>
              <Table.Td ta="right">{NO_VALUE}</Table.Td>
              <Table.Td ta="right">{amount(now.withoutRoom.bankroll)}</Table.Td>
            </Table.Tr>
          )}
        </Table.Tbody>
        <Table.Tfoot>
          <Table.Tr>
            <Table.Th scope="row">{t('dashboard.total')}</Table.Th>
            <Table.Td ta="right">{amount(totalNetOfPeriod, true)}</Table.Td>
            <Table.Td ta="right">{amount(now.total.bankroll, true)}</Table.Td>
          </Table.Tr>
        </Table.Tfoot>
      </Table>
    </Table.ScrollContainer>
  );
}
