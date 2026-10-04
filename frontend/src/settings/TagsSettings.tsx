import {
  Alert,
  Button,
  Group,
  Modal,
  Stack,
  Table,
  Text,
  TextInput,
  VisuallyHidden,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPencil, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { MAX_TAG_LENGTH, useDeleteTag, useRenameTag } from '../api/tags';
import type { Tag } from '../api/types';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { IconButton } from '../components/IconButton';
import { useSubmit } from '../components/useSubmit';
import { useFormat } from '../format/useFormat';

type Dialog = { kind: 'rename' | 'delete'; tag: Tag };

/**
 * The tags of the games, with how many games have each. They are created from the form of a
 * game; here they are renamed (merged into another one when given its name) or deleted.
 */
export function TagsSettings({ tags }: { tags: Tag[] }) {
  const { t } = useTranslation();
  const format = useFormat();
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const deleteTag = useDeleteTag();

  if (tags.length === 0) {
    return (
      <Text size="sm" c="dimmed">
        {t('settings.tags.empty')}
      </Text>
    );
  }

  return (
    <Stack gap="md">
      <Text size="sm" c="dimmed">
        {t('settings.tags.help')}
      </Text>
      <Table verticalSpacing="xs" highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('settings.tags.name')}</Table.Th>
            <Table.Th ta="right">{t('settings.tags.games')}</Table.Th>
            <Table.Th w={80}>
              <VisuallyHidden>{t('settings.tags.actions')}</VisuallyHidden>
            </Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {tags.map((tag) => (
            <Table.Tr key={tag.id}>
              <Table.Td style={{ wordBreak: 'break-word' }}>
                <Text size="sm">{tag.name}</Text>
              </Table.Td>
              <Table.Td ta="right">{format.number(tag.games)}</Table.Td>
              <Table.Td>
                <Group gap={4} wrap="nowrap" justify="flex-end">
                  <IconButton
                    variant="subtle"
                    color="gray"
                    label={t('settings.tags.renameTag', { tag: tag.name })}
                    onClick={() => setDialog({ kind: 'rename', tag })}
                  >
                    <IconPencil size={16} stroke={1.5} />
                  </IconButton>
                  <IconButton
                    variant="subtle"
                    color="red"
                    label={t('settings.tags.deleteTag', { tag: tag.name })}
                    onClick={() => setDialog({ kind: 'delete', tag })}
                  >
                    <IconTrash size={16} stroke={1.5} />
                  </IconButton>
                </Group>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>

      {dialog?.kind === 'rename' && (
        <RenameDialog tag={dialog.tag} tags={tags} onClose={() => setDialog(null)} />
      )}

      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title={t('settings.tags.delete.title')}
          confirmLabel={t('games.delete.confirmLabel')}
          destructive
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await deleteTag.mutateAsync(dialog.tag.id);
            notifications.show({
              color: 'teal',
              title: t('settings.tags.delete.done'),
              message: dialog.tag.name,
            });
          }}
        >
          {t('settings.tags.delete.confirm', {
            tag: dialog.tag.name,
            count: dialog.tag.games,
            formatted: format.number(dialog.tag.games),
          })}
        </ConfirmDialog>
      )}
    </Stack>
  );
}

/**
 * A new name for a tag. When another tag already has it (ignoring case), saving merges this one
 * into it, which the dialog says before.
 */
function RenameDialog({ tag, tags, onClose }: { tag: Tag; tags: Tag[]; onClose: () => void }) {
  const { t } = useTranslation();
  const save = useSubmit();
  const renameTag = useRenameTag();
  const [name, setName] = useState(tag.name);
  const [nameError, setNameError] = useState<string | null>(null);

  const wanted = name.trim();
  const target = tags.find(
    (other) => other.id !== tag.id && other.name.toLowerCase() === wanted.toLowerCase(),
  );

  function submit() {
    if (wanted === '') {
      setNameError(t('gameForm.errors.required'));
      return;
    }
    if (wanted.includes(';')) {
      setNameError(t('settings.tags.noSemicolon'));
      return;
    }
    void save.run(
      async () => {
        const saved = await renameTag.mutateAsync({ id: tag.id, tag: { name: wanted } });
        notifications.show({
          color: 'teal',
          title: target ? t('settings.tags.merged') : t('settings.tags.saved'),
          message: saved.name,
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
      title={t('settings.tags.rename')}
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
          <TextInput
            data-autofocus
            label={t('settings.tags.name')}
            required
            maxLength={MAX_TAG_LENGTH}
            value={name}
            error={nameError}
            onChange={(event) => {
              setName(event.currentTarget.value);
              setNameError(null);
            }}
          />
          {target && (
            <Alert color="yellow">
              {t('settings.tags.willMerge', { tag: tag.name, target: target.name })}
            </Alert>
          )}
          <Group justify="flex-end" gap="sm">
            <Button variant="subtle" color="gray" onClick={onClose} disabled={save.busy}>
              {t('actions.cancel')}
            </Button>
            <Button type="submit" loading={save.busy}>
              {target ? t('settings.tags.merge') : t('gameForm.save')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
