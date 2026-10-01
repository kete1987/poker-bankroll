import {
  Alert,
  Avatar,
  Button,
  FileButton,
  Group,
  Modal,
  Select,
  Stack,
  Switch,
  Text,
  TextInput,
} from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../api/client';
import {
  roomLogoUrl,
  useCreateRoom,
  useDeleteRoomLogo,
  useSetRoomLogo,
  useUpdateRoom,
} from '../api/rooms';
import type { Currency, Room } from '../api/types';
import { useSubmit } from '../components/useSubmit';
import { resizeImage } from './resizeImage';

interface RoomDialogProps {
  /** The room being edited; a new one is created when absent. */
  room?: Room;
  currencies: Currency[];
  /** Called with the room after it is created or saved. */
  onSaved: (room: Room, created: boolean) => void;
  onClose: () => void;
}

/**
 * A room: its name, its currency and whether it is active. Once it exists it can also have a
 * logo, which is changed on the spot (it is not part of what "Save" saves).
 */
export function RoomDialog({ room, currencies, onSaved, onClose }: RoomDialogProps) {
  const { t } = useTranslation();
  const save = useSubmit();
  const createRoom = useCreateRoom();
  const updateRoom = useUpdateRoom();

  const [name, setName] = useState(room?.name ?? '');
  const [currencyCode, setCurrencyCode] = useState(room?.currencyCode ?? currencies[0]?.code ?? '');
  const [active, setActive] = useState(room?.active ?? true);
  const [nameError, setNameError] = useState<string | null>(null);

  function submit() {
    if (name.trim() === '') {
      setNameError(t('gameForm.errors.required'));
      return;
    }
    void save.run(async () => {
      const request = { name: name.trim(), currencyCode, active };
      const saved = room
        ? await updateRoom.mutateAsync({ id: room.id, room: request })
        : await createRoom.mutateAsync(request);
      onSaved(saved, !room);
    });
  }

  return (
    <Modal
      opened
      onClose={onClose}
      title={room ? t('settings.rooms.edit') : t('settings.rooms.add')}
      closeButtonProps={{ 'aria-label': t('actions.close') }}
    >
      <Stack gap="md">
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          <Stack gap="md">
            {save.failure && <Alert color="red">{save.failure}</Alert>}
            <TextInput
              data-autofocus
              label={t('settings.rooms.name')}
              required
              maxLength={100}
              value={name}
              error={nameError}
              onChange={(event) => {
                setName(event.currentTarget.value);
                setNameError(null);
              }}
            />
            <Select
              label={t('settings.rooms.currency')}
              description={room?.inUse ? t('settings.rooms.currencyLocked') : undefined}
              required
              allowDeselect={false}
              disabled={room?.inUse}
              data={currencies.map((currency) => currency.code)}
              value={currencyCode}
              onChange={(value) => setCurrencyCode(value ?? currencyCode)}
            />
            <Switch
              label={t('settings.active')}
              description={t('settings.rooms.activeHelp')}
              checked={active}
              onChange={(event) => setActive(event.currentTarget.checked)}
            />
            <Group justify="flex-end" gap="sm">
              <Button variant="subtle" color="gray" onClick={onClose} disabled={save.busy}>
                {t('actions.cancel')}
              </Button>
              <Button type="submit" loading={save.busy}>
                {t('gameForm.save')}
              </Button>
            </Group>
          </Stack>
        </form>

        {room && <RoomLogo room={room} />}
      </Stack>
    </Modal>
  );
}

/** The logo of an existing room: shown, and changed or removed right away. */
function RoomLogo({ room }: { room: Room }) {
  const { t } = useTranslation();
  const setLogo = useSetRoomLogo();
  const deleteLogo = useDeleteRoomLogo();
  // The room comes from the list when the dialog opens: the version follows what is done here.
  const [logoVersion, setLogoVersion] = useState(room.logoVersion ?? null);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  async function run(action: () => Promise<string | null>) {
    setBusy(true);
    setFailure(null);
    try {
      setLogoVersion(await action());
    } catch (error) {
      setFailure(error instanceof ApiError ? error.message : t('settings.logo.unreadable'));
    } finally {
      setBusy(false);
    }
  }

  function upload(file: File | null) {
    if (!file) {
      return;
    }
    void run(async () => {
      // Shrunk here, so any image can be chosen whatever its size.
      const image = await resizeImage(file);
      const updated = await setLogo.mutateAsync({ id: room.id, image });
      return updated.logoVersion ?? null;
    });
  }

  return (
    <Stack gap="xs" component="section" aria-label={t('settings.logo.title')}>
      <Text size="sm" fw={500}>
        {t('settings.logo.title')}
      </Text>
      {failure && <Alert color="red">{failure}</Alert>}
      <Group gap="md">
        <Avatar
          size="lg"
          radius="sm"
          name={room.name}
          color="initials"
          src={logoVersion ? roomLogoUrl(room.id, logoVersion) : null}
          alt={logoVersion ? t('settings.logo.current', { room: room.name }) : ''}
        >
          {room.name.trim().charAt(0).toUpperCase()}
        </Avatar>
        <FileButton onChange={upload} accept="image/*">
          {(props) => (
            <Button variant="default" loading={busy} {...props}>
              {logoVersion ? t('settings.logo.change') : t('settings.logo.upload')}
            </Button>
          )}
        </FileButton>
        {logoVersion && (
          <Button
            variant="subtle"
            color="red"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await deleteLogo.mutateAsync(room.id);
                return null;
              })
            }
          >
            {t('settings.logo.remove')}
          </Button>
        )}
      </Group>
      <Text size="xs" c="dimmed">
        {t('settings.logo.help')}
      </Text>
    </Stack>
  );
}
