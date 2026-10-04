import { Group, Table, Text } from '@mantine/core';
import { IconPencil, IconTrash } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

import type { Movement } from '../api/types';
import { IconButton } from '../components/IconButton';
import { RoomLabel } from '../components/RoomLabel';
import { useFormat } from '../format/useFormat';
import { describeMovement } from './labels';

interface MovementsTableProps {
  movements: Movement[];
  onEdit: (movement: Movement) => void;
  onDelete: (movement: Movement) => void;
}

/** The bankroll movements of a page, newest first, with what each one adds or takes. */
export function MovementsTable({ movements, onEdit, onDelete }: MovementsTableProps) {
  const { t } = useTranslation();
  const format = useFormat();

  return (
    <Table.ScrollContainer minWidth={680}>
      <Table verticalSpacing="xs" highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('bankroll.columns.date')}</Table.Th>
            <Table.Th>{t('bankroll.columns.type')}</Table.Th>
            <Table.Th>{t('bankroll.columns.room')}</Table.Th>
            <Table.Th ta="right">{t('bankroll.columns.amount')}</Table.Th>
            <Table.Th>{t('bankroll.columns.notes')}</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {movements.map((movement) => {
            const name = describeMovement(t, movement, format.date(movement.occurredOn));
            return (
              <Table.Tr key={movement.id}>
                <Table.Td style={{ whiteSpace: 'nowrap' }}>
                  {format.date(movement.occurredOn)}
                </Table.Td>
                <Table.Td style={{ whiteSpace: 'nowrap' }}>
                  {t(`movementTypes.${movement.type}`)}
                </Table.Td>
                <Table.Td>
                  {movement.room ? (
                    <RoomLabel room={movement.room} />
                  ) : (
                    <Text size="sm" c="dimmed">
                      {t('bankroll.withoutRoom')}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td ta="right" style={{ whiteSpace: 'nowrap' }}>
                  <Text
                    span
                    size="sm"
                    fw={500}
                    c={
                      movement.signedAmount > 0
                        ? 'teal'
                        : movement.signedAmount < 0
                          ? 'red'
                          : undefined
                    }
                  >
                    {format.signedMoney(movement.signedAmount, movement.currencyCode)}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed" lineClamp={2}>
                    {movement.notes}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Group gap={4} wrap="nowrap" justify="flex-end">
                    <IconButton
                      variant="subtle"
                      color="gray"
                      label={t('bankroll.actions.editMovement', { movement: name })}
                      onClick={() => onEdit(movement)}
                    >
                      <IconPencil size={16} stroke={1.5} />
                    </IconButton>
                    <IconButton
                      variant="subtle"
                      color="red"
                      label={t('bankroll.actions.deleteMovement', { movement: name })}
                      onClick={() => onDelete(movement)}
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
  );
}
