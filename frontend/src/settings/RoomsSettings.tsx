import {
  ActionIcon,
  Alert,
  Button,
  Group,
  Stack,
  Switch,
  Table,
  Text,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPencil, IconPlus, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../api/client';
import { useDeleteRoom, useUpdateRoom } from '../api/rooms';
import type { Currency, Room } from '../api/types';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { RoomLabel } from '../components/RoomLabel';
import { RoomDialog } from './RoomDialog';
import { useNarrowScreen } from '../components/useNarrowScreen';

type Dialog = { kind: 'add' } | { kind: 'edit' | 'delete'; room: Room };

interface RoomsSettingsProps {
  rooms: Room[];
  currencies: Currency[];
}

/** The rooms: add, rename, activate or deactivate, set their logo, and delete the unused ones. */
export function RoomsSettings({ rooms, currencies }: RoomsSettingsProps) {
  const { t } = useTranslation();
  const narrow = useNarrowScreen();
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const updateRoom = useUpdateRoom();
  const deleteRoom = useDeleteRoom();

  async function setActive(room: Room, active: boolean) {
    setFailure(null);
    try {
      await updateRoom.mutateAsync({
        id: room.id,
        room: { name: room.name, currencyCode: room.currencyCode, active },
      });
    } catch (error) {
      setFailure(error instanceof ApiError ? error.message : t('errors.unexpected'));
    }
  }

  return (
    <Stack gap="md">
      <Group>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setDialog({ kind: 'add' })}>
          {t('settings.rooms.add')}
        </Button>
      </Group>
      {failure && <Alert color="red">{failure}</Alert>}

      {rooms.length === 0 ? (
        <Text c="dimmed">{t('settings.rooms.empty')}</Text>
      ) : (
        // Four short columns fit a phone when the table is not forced to a width.
        <Table.ScrollContainer minWidth={narrow ? 0 : 480}>
          <Table verticalSpacing="xs" horizontalSpacing={narrow ? 6 : undefined} highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t('settings.rooms.room')}</Table.Th>
                <Table.Th>{t('settings.rooms.currency')}</Table.Th>
                <Table.Th>{t('settings.active')}</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {rooms.map((room) => (
                <Table.Tr key={room.id}>
                  <Table.Td>
                    <RoomLabel room={room} />
                  </Table.Td>
                  <Table.Td>{room.currencyCode}</Table.Td>
                  <Table.Td>
                    <Switch
                      aria-label={t('settings.rooms.activeRoom', { room: room.name })}
                      checked={room.active}
                      // Editing meanwhile would save the room as it was before this change.
                      disabled={updateRoom.isPending}
                      onChange={(event) => void setActive(room, event.currentTarget.checked)}
                    />
                  </Table.Td>
                  <Table.Td>
                    <Group gap={4} wrap="nowrap" justify="flex-end">
                      <ActionIcon
                        variant="subtle"
                        color="gray"
                        aria-label={t('settings.rooms.editRoom', { room: room.name })}
                        disabled={updateRoom.isPending}
                        onClick={() => setDialog({ kind: 'edit', room })}
                      >
                        <IconPencil size={16} stroke={1.5} />
                      </ActionIcon>
                      {/* A room with history is deactivated, not deleted: the button says why. */}
                      <Tooltip label={t('settings.rooms.inUse')} disabled={!room.inUse} withArrow>
                        <ActionIcon
                          variant="subtle"
                          color="red"
                          // Not `disabled`: a disabled button cannot show its tooltip.
                          data-disabled={room.inUse || undefined}
                          aria-disabled={room.inUse}
                          aria-label={t('settings.rooms.deleteRoom', { room: room.name })}
                          onClick={() => {
                            if (!room.inUse) {
                              setDialog({ kind: 'delete', room });
                            }
                          }}
                        >
                          <IconTrash size={16} stroke={1.5} />
                        </ActionIcon>
                      </Tooltip>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}

      {(dialog?.kind === 'add' || dialog?.kind === 'edit') && (
        <RoomDialog
          key={dialog.kind === 'edit' ? dialog.room.id : 'new'}
          room={dialog.kind === 'edit' ? dialog.room : undefined}
          currencies={currencies}
          onClose={() => setDialog(null)}
          onSaved={(room, created, logoFailure) => {
            notifications.show({
              color: 'teal',
              title: created ? t('settings.rooms.created') : t('settings.rooms.saved'),
              message: room.name,
            });
            if (logoFailure) {
              // The room is there; its logo can be set again from "Edit".
              notifications.show({
                color: 'red',
                title: t('settings.logo.notStored'),
                message: logoFailure,
              });
            }
            setDialog(null);
          }}
        />
      )}

      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title={t('settings.rooms.delete.title')}
          confirmLabel={t('games.delete.confirmLabel')}
          destructive
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await deleteRoom.mutateAsync(dialog.room.id);
            notifications.show({
              color: 'teal',
              title: t('settings.rooms.delete.done'),
              message: dialog.room.name,
            });
          }}
        >
          {t('settings.rooms.delete.confirm', { room: dialog.room.name })}
        </ConfirmDialog>
      )}
    </Stack>
  );
}
