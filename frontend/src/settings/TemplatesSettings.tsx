import { Badge, Button, Card, Group, Stack, Table, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPencil, IconPlus, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useDeleteTemplate } from '../api/templates';
import type { GameTemplate, Room, Variant } from '../api/types';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { IconButton } from '../components/IconButton';
import { RoomLabel } from '../components/RoomLabel';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { useFormat } from '../format/useFormat';
import { variantLabel } from '../games/labels';
import { templateLabel } from '../games/templates';
import { TemplateDialog } from './TemplateDialog';

type Dialog = { kind: 'add' } | { kind: 'edit' | 'delete'; template: GameTemplate };

interface TemplatesSettingsProps {
  templates: GameTemplate[];
  rooms: Room[];
  variants: Variant[];
}

/**
 * The templates of games played often, to start one in a click from the games page. Those whose
 * room or variant is inactive stay, marked: they start no games until it is active again.
 */
export function TemplatesSettings({ templates, rooms, variants }: TemplatesSettingsProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const narrow = useNarrowScreen();
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const deleteTemplate = useDeleteTemplate();

  /** What the games started from it are: type, variant, name and modality. */
  function whatOf(template: GameTemplate): string {
    return [
      t(`gameTypes.${template.gameType}`),
      template.variant && variantLabel(t, template.variant),
      template.modality === 'NLHE' ? null : t(`modalities.${template.modality}`),
      template.name,
    ]
      .filter(Boolean)
      .join(' · ');
  }

  function actions(template: GameTemplate, label: string) {
    return (
      <Group gap={4} wrap="nowrap" justify="flex-end">
        <IconButton
          variant="subtle"
          color="gray"
          label={t('templates.editTemplate', { template: label })}
          onClick={() => setDialog({ kind: 'edit', template })}
        >
          <IconPencil size={16} stroke={1.5} />
        </IconButton>
        <IconButton
          variant="subtle"
          color="red"
          label={t('templates.deleteTemplate', { template: label })}
          onClick={() => setDialog({ kind: 'delete', template })}
        >
          <IconTrash size={16} stroke={1.5} />
        </IconButton>
      </Group>
    );
  }

  const unusable = (
    <Badge size="xs" variant="light" color="gray">
      {t('templates.unusable')}
    </Badge>
  );

  return (
    <Stack gap="md">
      <Text size="sm" c="dimmed" maw={720}>
        {t('templates.intro')}
      </Text>
      <Group>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setDialog({ kind: 'add' })}>
          {t('templates.add')}
        </Button>
      </Group>

      {templates.length === 0 ? (
        <Text c="dimmed">{t('templates.empty')}</Text>
      ) : narrow ? (
        <Stack gap="xs" component="ul" p={0} m={0} style={{ listStyle: 'none' }}>
          {templates.map((template) => {
            const label = templateLabel(t, format, template);
            return (
              <Card
                key={template.id}
                withBorder
                padding="sm"
                component="li"
                style={{ overflowWrap: 'anywhere' }}
              >
                <Stack gap={4}>
                  <Group justify="space-between" wrap="nowrap" gap="xs" align="flex-start">
                    <Text size="sm" fw={500}>
                      {label}
                    </Text>
                    {actions(template, label)}
                  </Group>
                  <Group gap="xs" justify="space-between" wrap="nowrap">
                    <RoomLabel room={template.room} />
                    <Text size="sm" fw={500} style={{ whiteSpace: 'nowrap' }}>
                      {format.money(template.buyIn, template.currencyCode)}
                    </Text>
                  </Group>
                  <Text size="xs" c="dimmed">
                    {whatOf(template)}
                  </Text>
                  {!template.usable && <div>{unusable}</div>}
                </Stack>
              </Card>
            );
          })}
        </Stack>
      ) : (
        <Table.ScrollContainer minWidth={640}>
          <Table verticalSpacing="xs" highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t('templates.columns.template')}</Table.Th>
                <Table.Th>{t('games.columns.room')}</Table.Th>
                <Table.Th>{t('games.columns.game')}</Table.Th>
                <Table.Th ta="right">{t('games.columns.buyIn')}</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {templates.map((template) => {
                const label = templateLabel(t, format, template);
                return (
                  <Table.Tr key={template.id}>
                    <Table.Td>
                      <Group gap="xs">
                        <Text size="sm">{label}</Text>
                        {!template.usable && unusable}
                      </Group>
                    </Table.Td>
                    <Table.Td>
                      <RoomLabel room={template.room} />
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{whatOf(template)}</Text>
                    </Table.Td>
                    <Table.Td ta="right" style={{ whiteSpace: 'nowrap' }}>
                      {format.money(template.buyIn, template.currencyCode)}
                    </Table.Td>
                    <Table.Td>{actions(template, label)}</Table.Td>
                  </Table.Tr>
                );
              })}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}

      {(dialog?.kind === 'add' || dialog?.kind === 'edit') && (
        <TemplateDialog
          key={dialog.kind === 'edit' ? dialog.template.id : 'new'}
          rooms={rooms}
          variants={variants}
          template={dialog.kind === 'edit' ? dialog.template : undefined}
          onClose={() => setDialog(null)}
        />
      )}

      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title={t('templates.delete.title')}
          confirmLabel={t('games.delete.confirmLabel')}
          destructive
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await deleteTemplate.mutateAsync(dialog.template.id);
            notifications.show({
              color: 'teal',
              title: t('templates.delete.done'),
              message: templateLabel(t, format, dialog.template),
            });
          }}
        >
          {t('templates.delete.confirm', { template: templateLabel(t, format, dialog.template) })}
        </ConfirmDialog>
      )}
    </Stack>
  );
}
