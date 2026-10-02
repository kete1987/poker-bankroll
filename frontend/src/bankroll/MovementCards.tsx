import { ActionIcon, Card, Group, Menu, Stack, Text } from '@mantine/core';
import { IconDotsVertical, IconPencil, IconTrash } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

import type { Movement } from '../api/types';
import { RoomLabel } from '../components/RoomLabel';
import { useFormat } from '../format/useFormat';
import { describeMovement } from './labels';

interface MovementCardsProps {
  movements: Movement[];
  onEdit: (movement: Movement) => void;
  onDelete: (movement: Movement) => void;
}

/**
 * The bankroll movements of a page on a phone: one card each instead of a row of a table, so
 * nothing has to be scrolled sideways.
 */
export function MovementCards({ movements, onEdit, onDelete }: MovementCardsProps) {
  const { t } = useTranslation();
  const format = useFormat();

  return (
    <Stack gap="xs" component="ul" p={0} m={0} style={{ listStyle: 'none' }}>
      {movements.map((movement) => {
        const name = describeMovement(t, movement, format.date(movement.occurredOn));
        return (
          <Card key={movement.id} withBorder padding="sm" component="li">
            <Stack gap={6}>
              <Group justify="space-between" wrap="nowrap" gap="xs">
                <Text span size="sm" style={{ whiteSpace: 'nowrap' }}>
                  {format.date(movement.occurredOn)}
                </Text>
                <Group gap={4} wrap="nowrap">
                  {movement.room ? (
                    <RoomLabel room={movement.room} />
                  ) : (
                    <Text size="sm" c="dimmed">
                      {t('bankroll.withoutRoom')}
                    </Text>
                  )}
                  <Menu position="bottom-end" withinPortal>
                    <Menu.Target>
                      <ActionIcon
                        variant="subtle"
                        color="gray"
                        aria-label={t('bankroll.actions.moreFor', { movement: name })}
                      >
                        <IconDotsVertical size={18} stroke={1.5} />
                      </ActionIcon>
                    </Menu.Target>
                    <Menu.Dropdown>
                      <Menu.Item
                        leftSection={<IconPencil size={16} />}
                        onClick={() => onEdit(movement)}
                      >
                        {t('bankroll.actions.edit')}
                      </Menu.Item>
                      <Menu.Item
                        color="red"
                        leftSection={<IconTrash size={16} />}
                        onClick={() => onDelete(movement)}
                      >
                        {t('bankroll.actions.delete')}
                      </Menu.Item>
                    </Menu.Dropdown>
                  </Menu>
                </Group>
              </Group>
              <Group justify="space-between" wrap="nowrap" gap="xs" align="baseline">
                <Text size="sm">{t(`movementTypes.${movement.type}`)}</Text>
                <Text
                  span
                  size="sm"
                  fw={500}
                  style={{ whiteSpace: 'nowrap' }}
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
              </Group>
              {movement.notes && (
                <Text size="xs" c="dimmed">
                  {movement.notes}
                </Text>
              )}
            </Stack>
          </Card>
        );
      })}
    </Stack>
  );
}
