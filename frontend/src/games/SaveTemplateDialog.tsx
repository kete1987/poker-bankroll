import { Alert, Button, Group, Modal, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useCreateTemplate } from '../api/templates';
import type { Game } from '../api/types';
import { RoomLabel } from '../components/RoomLabel';
import { useSubmit } from '../components/useSubmit';
import { useFormat } from '../format/useFormat';
import { describeGame } from './labels';
import { templateLabel, templateOfGame } from './templates';

/**
 * Keeps a game as a template: its room, type, variant, modality, name and buy-in, to start games
 * like it in one click. The label is optional.
 */
export function SaveTemplateDialog({ game, onClose }: { game: Game; onClose: () => void }) {
  const { t } = useTranslation();
  const format = useFormat();
  const save = useSubmit();
  const createTemplate = useCreateTemplate();
  const [label, setLabel] = useState('');
  const [labelError, setLabelError] = useState<string | null>(null);
  const automatic = templateLabel(t, format, { ...game, label: null });

  function submit() {
    void save.run(
      async () => {
        const saved = await createTemplate.mutateAsync(templateOfGame(game, label));
        notifications.show({
          color: 'teal',
          title: t('templates.save.done'),
          message: templateLabel(t, format, saved),
        });
        onClose();
      },
      (violations) => {
        const ofLabel = violations.find((violation) => violation.field === 'label');
        setLabelError(ofLabel?.message ?? null);
        return Boolean(ofLabel);
      },
    );
  }

  return (
    <Modal
      opened
      onClose={onClose}
      title={t('templates.save.title')}
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
          <Text size="sm">{t('templates.save.intro')}</Text>
          <Group justify="space-between" wrap="nowrap" gap="xs">
            <Stack gap={2} style={{ minWidth: 0, overflowWrap: 'anywhere' }}>
              <Text size="sm" fw={500}>
                {describeGame(t, game)}
              </Text>
              <RoomLabel room={game.room} />
            </Stack>
            <Text size="sm" fw={500} style={{ whiteSpace: 'nowrap' }}>
              {format.money(game.buyIn, game.currencyCode)}
            </Text>
          </Group>
          <TextInput
            data-autofocus
            label={t('templates.fields.label')}
            description={t('templates.fields.labelHelp')}
            placeholder={automatic}
            maxLength={80}
            value={label}
            error={labelError}
            onChange={(event) => {
              setLabel(event.currentTarget.value);
              setLabelError(null);
            }}
          />
          <Group justify="flex-end" gap="sm">
            <Button variant="subtle" color="gray" onClick={onClose} disabled={save.busy}>
              {t('actions.cancel')}
            </Button>
            <Button type="submit" loading={save.busy}>
              {t('templates.save.submit')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
