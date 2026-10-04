import {
  Alert,
  Button,
  Group,
  Modal,
  NumberInput,
  SegmentedControl,
  Select,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { useTranslation } from 'react-i18next';

import { useCreateTemplate, useUpdateTemplate } from '../api/templates';
import type { GameTemplate, GameTemplateRequest, GameType, Room, Variant } from '../api/types';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { useSubmit } from '../components/useSubmit';
import { useFormat } from '../format/useFormat';
import { amountOrNull } from '../games/amount';
import { variantLabel } from '../games/labels';
import { NameInput } from '../games/NameInput';
import { templateLabel } from '../games/templates';
import { useNameSuggestions, type NameFields } from '../games/useNameSuggestions';

const GAME_TYPES: readonly GameType[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];
const MODALITIES = ['NLHE', 'PLO'] as const;

interface TemplateValues extends NameFields {
  label: string;
  roomId: string | null;
  gameType: GameType;
}

interface TemplateDialogProps {
  rooms: Room[];
  variants: Variant[];
  /** The template being edited; a new one is created when absent. */
  template?: GameTemplate;
  onClose: () => void;
}

/** A template: what the games started from it are (room, type, variant, name, buy-in) and its label. */
export function TemplateDialog({ rooms, variants, template, onClose }: TemplateDialogProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const narrow = useNarrowScreen();
  const save = useSubmit();
  const createTemplate = useCreateTemplate();
  const updateTemplate = useUpdateTemplate();

  // As for games: inactive rooms and variants are not offered, but a template keeps the ones it has.
  const offeredRooms = rooms.filter((room) => room.active || room.id === template?.room.id);

  const form = useForm<TemplateValues>({
    initialValues: template
      ? {
          label: template.label ?? '',
          roomId: String(template.room.id),
          gameType: template.gameType,
          variantId: template.variant ? String(template.variant.id) : null,
          modality: template.modality,
          name: template.name ?? '',
          buyIn: template.buyIn,
        }
      : {
          label: '',
          roomId: offeredRooms.length === 1 ? String(offeredRooms[0]!.id) : null,
          gameType: 'TOURNAMENT',
          variantId: null,
          modality: 'NLHE',
          name: '',
          buyIn: '',
        },
    validate: {
      roomId: (value) => (value ? null : t('gameForm.errors.required')),
      buyIn: (value) => (amountOrNull(value) === null ? t('gameForm.errors.required') : null),
    },
  });
  const values = form.values;
  const room = offeredRooms.find((candidate) => String(candidate.id) === values.roomId);
  const currency = room?.currencyCode;

  const variantOptions = variants
    .filter(
      (variant) =>
        variant.gameType === values.gameType &&
        (variant.active || variant.id === template?.variant?.id),
    )
    .map((variant) => ({ value: String(variant.id), label: variantLabel(t, variant) }));

  const names = useNameSuggestions(form, {
    gameType: values.gameType,
    currency,
    variantOptions,
    editing: template,
  });
  const { filledByName } = names;

  function toRequest(): GameTemplateRequest {
    return {
      label: values.label.trim() || null,
      roomId: Number(values.roomId),
      gameType: values.gameType,
      modality: values.modality,
      variantId: values.variantId ? Number(values.variantId) : null,
      name: values.name.trim() || null,
      buyIn: amountOrNull(values.buyIn) ?? 0,
    };
  }

  function submit() {
    if (form.validate().hasErrors) {
      return;
    }
    void save.run(
      async () => {
        const saved = template
          ? await updateTemplate.mutateAsync({ id: template.id, template: toRequest() })
          : await createTemplate.mutateAsync(toRequest());
        notifications.show({
          color: 'teal',
          title: template ? t('templates.saved') : t('templates.created'),
          message: templateLabel(t, format, saved),
        });
        onClose();
      },
      (violations) => {
        const ofFields = violations.filter(
          (violation) => violation.field && violation.field in values,
        );
        ofFields.forEach((violation) => form.setFieldError(violation.field!, violation.message));
        return ofFields.length > 0;
      },
    );
  }

  return (
    <Modal
      opened
      onClose={onClose}
      title={template ? t('templates.edit') : t('templates.add')}
      size="lg"
      fullScreen={narrow}
      closeButtonProps={{ 'aria-label': t('actions.close') }}
    >
      {offeredRooms.length === 0 ? (
        <Alert color="yellow" title={t('gameForm.noRooms.title')}>
          {t('gameForm.noRooms.description')}
        </Alert>
      ) : (
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          <Stack gap="md">
            {save.failure && <Alert color="red">{save.failure}</Alert>}

            <SegmentedControl
              fullWidth
              aria-label={t('gameForm.gameType')}
              data={GAME_TYPES.map((type) => ({ value: type, label: t(`gameTypes.${type}`) }))}
              value={values.gameType}
              onChange={(value) => {
                // Variants belong to a type.
                names.forget('variantId');
                form.setValues({ gameType: value as GameType, variantId: null });
              }}
            />

            <SimpleGrid cols={{ base: 1, xs: 2 }}>
              <Select
                label={t('gameForm.room')}
                required
                allowDeselect={false}
                data={offeredRooms.map((candidate) => ({
                  value: String(candidate.id),
                  label: `${candidate.name} (${candidate.currencyCode})`,
                }))}
                {...form.getInputProps('roomId')}
                onChange={(value) => {
                  const chosen = offeredRooms.find((candidate) => String(candidate.id) === value);
                  names.roomChanged(chosen?.currencyCode);
                  form.setFieldValue('roomId', value);
                }}
              />
              <NumberInput
                data-autofocus
                label={values.gameType === 'CASH' ? t('gameForm.buyInCash') : t('gameForm.buyIn')}
                required
                min={0}
                decimalScale={2}
                decimalSeparator={format.decimalSeparator}
                allowedDecimalSeparators={[',', '.']}
                hideControls
                rightSection={
                  currency ? (
                    <Text size="xs" c="dimmed">
                      {currency}
                    </Text>
                  ) : undefined
                }
                rightSectionWidth={48}
                rightSectionPointerEvents="none"
                {...filledByName('buyIn')}
              />
              <NameInput
                label={t('gameForm.name')}
                suggestions={names.suggestions}
                onOptionSubmit={names.fillFromName}
                {...form.getInputProps('name')}
              />
              {variantOptions.length > 0 && (
                <Select
                  label={t('gameForm.variant')}
                  clearable
                  data={variantOptions}
                  {...filledByName('variantId')}
                />
              )}
              <Stack gap={4}>
                <Text size="sm" fw={500} id="template-modality">
                  {t('gameForm.modality')}
                </Text>
                <SegmentedControl
                  aria-labelledby="template-modality"
                  data={MODALITIES.map((modality) => ({
                    value: modality,
                    label: t(`modalities.${modality}`),
                  }))}
                  {...filledByName('modality')}
                />
              </Stack>
              <TextInput
                label={t('templates.fields.label')}
                description={t('templates.fields.labelHelp')}
                maxLength={80}
                {...form.getInputProps('label')}
              />
            </SimpleGrid>

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
      )}
    </Modal>
  );
}
