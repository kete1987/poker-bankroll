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
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
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
  /**
   * Called with the room after it is created or saved. `logoFailure` says why the logo chosen
   * for a new room could not be stored (the room itself was created).
   */
  onSaved: (room: Room, created: boolean, logoFailure?: string) => void;
  onClose: () => void;
}

/**
 * A room: its name, its currency, whether it is active and, optionally, its logo. The logo of a
 * new room is chosen here and stored with it; the one of an existing room is changed on the spot
 * (it is not part of what "Save" saves).
 */
export function RoomDialog({ room, currencies, onSaved, onClose }: RoomDialogProps) {
  const { t } = useTranslation();
  const save = useSubmit();
  const createRoom = useCreateRoom();
  const updateRoom = useUpdateRoom();
  const setLogo = useSetRoomLogo();

  const [name, setName] = useState(room?.name ?? '');
  const [currencyCode, setCurrencyCode] = useState(room?.currencyCode ?? currencies[0]?.code ?? '');
  const [active, setActive] = useState(room?.active ?? true);
  const [nameError, setNameError] = useState<string | null>(null);
  const [currencyError, setCurrencyError] = useState<string | null>(null);
  // The logo chosen for a room that does not exist yet, already resized.
  const [newLogo, setNewLogo] = useState<Blob | null>(null);
  // While the logo chosen is being resized the room cannot be saved: it would go without it.
  const [resizing, setResizing] = useState(false);

  function submit() {
    if (resizing) {
      return;
    }
    if (name.trim() === '') {
      setNameError(t('gameForm.errors.required'));
      return;
    }
    void save.run(submitRoom, (violations) => {
      const ofName = violations.find((violation) => violation.field === 'name');
      const ofCurrency = violations.find((violation) => violation.field === 'currencyCode');
      setNameError(ofName?.message ?? null);
      setCurrencyError(ofCurrency?.message ?? null);
      return Boolean(ofName || ofCurrency);
    });
  }

  async function submitRoom() {
    {
      const request = { name: name.trim(), currencyCode, active };
      if (room) {
        onSaved(await updateRoom.mutateAsync({ id: room.id, room: request }), false);
        return;
      }
      const created = await createRoom.mutateAsync(request);
      if (!newLogo) {
        onSaved(created, true);
        return;
      }
      // The room exists by now: a logo that fails is reported, not a reason to lose the room.
      try {
        onSaved(await setLogo.mutateAsync({ id: created.id, image: newLogo }), true);
      } catch (error) {
        onSaved(
          created,
          true,
          error instanceof ApiError ? error.message : t('settings.logo.unreadable'),
        );
      }
    }
  }

  return (
    <Modal
      opened
      onClose={onClose}
      title={room ? t('settings.rooms.edit') : t('settings.rooms.add')}
      closeButtonProps={{ 'aria-label': t('actions.close') }}
    >
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
            error={currencyError}
            onChange={(value) => {
              setCurrencyCode(value ?? currencyCode);
              setCurrencyError(null);
            }}
          />
          <Switch
            label={t('settings.active')}
            description={t('settings.rooms.activeHelp')}
            checked={active}
            onChange={(event) => setActive(event.currentTarget.checked)}
          />
          {room ? (
            <StoredLogo room={room} />
          ) : (
            <NewLogo name={name} logo={newLogo} onChange={setNewLogo} onBusy={setResizing} />
          )}
          <Group justify="flex-end" gap="sm">
            <Button variant="subtle" color="gray" onClick={onClose} disabled={save.busy}>
              {t('actions.cancel')}
            </Button>
            <Button type="submit" loading={save.busy} disabled={resizing}>
              {t('gameForm.save')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

interface LogoSectionProps {
  /** Name of the room, for the placeholder with its initial. */
  name: string;
  /** Address of the image shown, when there is one. */
  src: string | null;
  busy: boolean;
  failure: string | null;
  onFile: (file: File) => void;
  onRemove: () => void;
  /** Says when the logo takes effect. */
  help: ReactNode;
}

/** The logo with its buttons: upload or change, and remove when there is one. */
function LogoSection({ name, src, busy, failure, onFile, onRemove, help }: LogoSectionProps) {
  const { t } = useTranslation();
  const initial = name.trim().charAt(0).toUpperCase();
  const resetFile = useRef<() => void>(null);

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
          name={name || undefined}
          color="initials"
          src={src}
          alt={src ? t('settings.logo.current', { room: name }) : ''}
        >
          {initial}
        </Avatar>
        <FileButton
          resetRef={resetFile}
          accept="image/*"
          onChange={(file) => {
            if (file) {
              onFile(file);
            }
            // Otherwise choosing the same file again (after removing it) would do nothing.
            resetFile.current?.();
          }}
        >
          {(props) => (
            <Button variant="default" loading={busy} {...props}>
              {src ? t('settings.logo.change') : t('settings.logo.upload')}
            </Button>
          )}
        </FileButton>
        {src && (
          <Button variant="subtle" color="red" disabled={busy} onClick={onRemove}>
            {t('settings.logo.remove')}
          </Button>
        )}
      </Group>
      <Text size="xs" c="dimmed">
        {help}
      </Text>
    </Stack>
  );
}

/** The logo of a room being created: resized when chosen, stored when the room is saved. */
function NewLogo({
  name,
  logo,
  onChange,
  onBusy,
}: {
  name: string;
  logo: Blob | null;
  onChange: (logo: Blob | null) => void;
  /** Told while the image chosen is being resized. */
  onBusy: (busy: boolean) => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  // A temporary address to preview the image, released when it changes or the dialog closes.
  const preview = useMemo(() => (logo ? URL.createObjectURL(logo) : null), [logo]);
  useEffect(() => {
    return () => {
      if (preview) {
        URL.revokeObjectURL(preview);
      }
    };
  }, [preview]);

  async function choose(file: File) {
    setBusy(true);
    onBusy(true);
    setFailure(null);
    try {
      onChange(await resizeImage(file));
    } catch {
      setFailure(t('settings.logo.unreadable'));
    } finally {
      setBusy(false);
      onBusy(false);
    }
  }

  return (
    <LogoSection
      name={name}
      src={preview}
      busy={busy}
      failure={failure}
      onFile={(file) => void choose(file)}
      onRemove={() => onChange(null)}
      help={t('settings.logo.helpNew')}
    />
  );
}

/** The logo of an existing room: changed or removed right away. */
function StoredLogo({ room }: { room: Room }) {
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

  return (
    <LogoSection
      name={room.name}
      src={logoVersion ? roomLogoUrl(room.id, logoVersion) : null}
      busy={busy}
      failure={failure}
      onFile={(file) =>
        void run(async () => {
          // Shrunk here, so any image can be chosen whatever its size.
          const image = await resizeImage(file);
          const updated = await setLogo.mutateAsync({ id: room.id, image });
          return updated.logoVersion ?? null;
        })
      }
      onRemove={() =>
        void run(async () => {
          await deleteLogo.mutateAsync(room.id);
          return null;
        })
      }
      help={t('settings.logo.help')}
    />
  );
}
