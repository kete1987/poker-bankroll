import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPencil, IconPlus, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../api/client';
import type { GameType, Variant } from '../api/types';
import { useCreateVariant, useDeleteVariant, useUpdateVariant } from '../api/variants';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { useSubmit } from '../components/useSubmit';
import { variantLabel } from '../games/labels';

const GAME_TYPES: readonly GameType[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];

type Dialog = { kind: 'add' } | { kind: 'edit' | 'delete'; variant: Variant };

/**
 * The variants of each game type. Built-in ones can only be activated or deactivated; the ones
 * the user adds can also be renamed, and deleted while no game uses them.
 */
export function VariantsSettings({ variants }: { variants: Variant[] }) {
  const { t } = useTranslation();
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const updateVariant = useUpdateVariant();
  const deleteVariant = useDeleteVariant();

  async function setActive(variant: Variant, active: boolean) {
    setFailure(null);
    try {
      await updateVariant.mutateAsync({
        id: variant.id,
        // The name of a built-in variant is not sent: it cannot change.
        variant: { name: variant.builtIn ? null : variant.name, active },
      });
    } catch (error) {
      setFailure(error instanceof ApiError ? error.message : t('errors.unexpected'));
    }
  }

  return (
    <Stack gap="md">
      <Group>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setDialog({ kind: 'add' })}>
          {t('settings.variants.add')}
        </Button>
      </Group>
      {failure && <Alert color="red">{failure}</Alert>}

      {GAME_TYPES.map((gameType) => {
        const ofType = variants.filter((variant) => variant.gameType === gameType);
        const headingId = `settings-variants-${gameType}`;
        return (
          <Stack key={gameType} gap="xs" component="section" aria-labelledby={headingId}>
            <Title order={3} size="h5" id={headingId}>
              {t(`gameTypes.${gameType}`)}
            </Title>
            {ofType.length === 0 ? (
              <Text size="sm" c="dimmed">
                {t('settings.variants.empty')}
              </Text>
            ) : (
              <Table verticalSpacing="xs" highlightOnHover>
                <Table.Tbody>
                  {ofType.map((variant) => {
                    const name = variantLabel(t, variant);
                    return (
                      <Table.Tr key={variant.id}>
                        <Table.Td>
                          <Group gap="xs">
                            <Text size="sm">{name}</Text>
                            {!variant.builtIn && (
                              <Badge size="xs" variant="light" color="gray">
                                {t('settings.variants.own')}
                              </Badge>
                            )}
                          </Group>
                        </Table.Td>
                        <Table.Td w={80}>
                          <Switch
                            aria-label={t('settings.variants.activeVariant', { variant: name })}
                            checked={variant.active}
                            disabled={updateVariant.isPending}
                            onChange={(event) =>
                              void setActive(variant, event.currentTarget.checked)
                            }
                          />
                        </Table.Td>
                        <Table.Td w={80}>
                          {!variant.builtIn && (
                            <Group gap={4} wrap="nowrap" justify="flex-end">
                              <ActionIcon
                                variant="subtle"
                                color="gray"
                                aria-label={t('settings.variants.editVariant', { variant: name })}
                                disabled={updateVariant.isPending}
                                onClick={() => setDialog({ kind: 'edit', variant })}
                              >
                                <IconPencil size={16} stroke={1.5} />
                              </ActionIcon>
                              <Tooltip
                                label={t('settings.variants.inUse')}
                                disabled={!variant.inUse}
                                withArrow
                              >
                                <ActionIcon
                                  variant="subtle"
                                  color="red"
                                  // Not `disabled`: a disabled button cannot show its tooltip.
                                  data-disabled={variant.inUse || undefined}
                                  aria-disabled={variant.inUse}
                                  aria-label={t('settings.variants.deleteVariant', {
                                    variant: name,
                                  })}
                                  onClick={() => {
                                    if (!variant.inUse) {
                                      setDialog({ kind: 'delete', variant });
                                    }
                                  }}
                                >
                                  <IconTrash size={16} stroke={1.5} />
                                </ActionIcon>
                              </Tooltip>
                            </Group>
                          )}
                        </Table.Td>
                      </Table.Tr>
                    );
                  })}
                </Table.Tbody>
              </Table>
            )}
          </Stack>
        );
      })}

      {(dialog?.kind === 'add' || dialog?.kind === 'edit') && (
        <VariantDialog
          variant={dialog.kind === 'edit' ? dialog.variant : undefined}
          onClose={() => setDialog(null)}
        />
      )}

      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title={t('settings.variants.delete.title')}
          confirmLabel={t('games.delete.confirmLabel')}
          destructive
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await deleteVariant.mutateAsync(dialog.variant.id);
            notifications.show({
              color: 'teal',
              title: t('settings.variants.delete.done'),
              message: variantLabel(t, dialog.variant),
            });
          }}
        >
          {t('settings.variants.delete.confirm', { variant: variantLabel(t, dialog.variant) })}
        </ConfirmDialog>
      )}
    </Stack>
  );
}

/** A variant of the user: its game type (only when it is created) and its name. */
function VariantDialog({ variant, onClose }: { variant?: Variant; onClose: () => void }) {
  const { t } = useTranslation();
  const save = useSubmit();
  const createVariant = useCreateVariant();
  const updateVariant = useUpdateVariant();
  const [gameType, setGameType] = useState<GameType>(variant?.gameType ?? 'TOURNAMENT');
  const [name, setName] = useState(variant?.name ?? '');
  const [nameError, setNameError] = useState<string | null>(null);

  function submit() {
    if (name.trim() === '') {
      setNameError(t('gameForm.errors.required'));
      return;
    }
    void save.run(
      async () => {
        const saved = variant
          ? await updateVariant.mutateAsync({
              id: variant.id,
              variant: { name: name.trim(), active: variant.active },
            })
          : await createVariant.mutateAsync({ gameType, name: name.trim() });
        notifications.show({
          color: 'teal',
          title: variant ? t('settings.variants.saved') : t('settings.variants.created'),
          message: saved.name ?? '',
        });
        onClose();
      },
      (violations) => {
        const ofName = violations.find((violation) => violation.field === 'name');
        setNameError(ofName?.message ?? null);
        return Boolean(ofName);
      },
    );
  }

  return (
    <Modal
      opened
      onClose={onClose}
      title={variant ? t('settings.variants.edit') : t('settings.variants.add')}
      size="sm"
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
          <Select
            label={t('settings.variants.gameType')}
            description={variant ? t('settings.variants.gameTypeLocked') : undefined}
            required
            allowDeselect={false}
            disabled={Boolean(variant)}
            data={GAME_TYPES.map((type) => ({ value: type, label: t(`gameTypes.${type}`) }))}
            value={gameType}
            onChange={(value) => setGameType((value as GameType | null) ?? gameType)}
          />
          <TextInput
            data-autofocus
            label={t('settings.variants.name')}
            required
            maxLength={100}
            value={name}
            error={nameError}
            onChange={(event) => {
              setName(event.currentTarget.value);
              setNameError(null);
            }}
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
    </Modal>
  );
}
