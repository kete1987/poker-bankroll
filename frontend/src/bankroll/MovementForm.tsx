import {
  Alert,
  Button,
  Group,
  NumberInput,
  SegmentedControl,
  Select,
  SimpleGrid,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../api/client';
import type { Currency, Movement, MovementRequest, MovementType, Room } from '../api/types';
import { useFormat } from '../format/useFormat';
import { amountOrNull, type Amount } from '../games/amount';
import { todayIso } from '../games/gameDefaults';
import { MOVEMENT_TYPES } from './useBankrollFilters';

interface MovementFormValues {
  type: MovementType;
  roomId: string | null;
  currencyCode: string | null;
  occurredOn: string;
  amount: Amount;
  notes: string;
}

interface MovementFormProps {
  rooms: Room[];
  currencies: Currency[];
  /** The movement being edited; a new one is recorded when absent. */
  movement?: Movement;
  /** Currency offered first for a movement without a room. */
  defaultCurrency?: string;
  /** Saves the movement; rejects with an `ApiError` when the backend refuses it. */
  onSave: (movement: MovementRequest) => Promise<Movement>;
  onSaved: (movement: Movement) => void;
  onCancel: () => void;
}

/**
 * Form of a bankroll movement. It belongs to a room, in whose currency it is, or to no room and
 * then says its currency. Unlike games, movements are accepted in inactive rooms.
 */
export function MovementForm({
  rooms,
  currencies,
  movement,
  defaultCurrency,
  onSave,
  onSaved,
  onCancel,
}: MovementFormProps) {
  const { t } = useTranslation();
  const format = useFormat();
  // A ref, not the state: a second Enter can arrive before the state of the first is rendered.
  const submitting = useRef(false);
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const form = useForm<MovementFormValues>({
    initialValues: {
      type: movement?.type ?? 'DEPOSIT',
      roomId: movement?.room ? String(movement.room.id) : null,
      currencyCode: movement
        ? movement.currencyCode
        : (defaultCurrency ?? currencies[0]?.code ?? null),
      occurredOn: movement?.occurredOn ?? todayIso(),
      amount: movement?.amount ?? '',
      notes: movement?.notes ?? '',
    },
    validate: {
      occurredOn: (value) => (value ? null : t('gameForm.errors.required')),
      currencyCode: (value, values) =>
        values.roomId || value ? null : t('gameForm.errors.required'),
      amount: (value, values) => {
        const amount = amountOrNull(value);
        if (amount === null) {
          return t('gameForm.errors.required');
        }
        // The type gives the direction; only a correction can go either way.
        return amount > 0 || (amount < 0 && values.type === 'ADJUSTMENT')
          ? null
          : t('bankroll.form.errors.amount');
      },
    },
  });
  const values = form.values;
  const room = rooms.find((candidate) => String(candidate.id) === values.roomId);
  const currency = room?.currencyCode ?? values.currencyCode ?? undefined;

  async function submit() {
    if (submitting.current || form.validate().hasErrors) {
      return;
    }
    submitting.current = true;
    setSaving(true);
    setFailure(null);
    try {
      const saved = await onSave({
        occurredOn: values.occurredOn,
        type: values.type,
        // A room or, without one, a currency: never both.
        roomId: room ? room.id : null,
        currencyCode: room ? null : values.currencyCode,
        amount: amountOrNull(values.amount) ?? 0,
        notes: values.notes.trim() || null,
      });
      onSaved(saved);
    } catch (error) {
      if (error instanceof ApiError) {
        const fieldErrors = error.errors.filter(
          (violation) => violation.field && violation.field in form.values,
        );
        fieldErrors.forEach((violation) => form.setFieldError(violation.field!, violation.message));
        setFailure(fieldErrors.length > 0 ? null : error.message);
      } else {
        setFailure(t('errors.unexpected'));
      }
    } finally {
      submitting.current = false;
      setSaving(false);
    }
  }

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <Stack gap="md">
        {failure && (
          <Alert color="red" title={t('bankroll.form.errors.notSaved')}>
            {failure}
          </Alert>
        )}

        <Stack gap={4}>
          <SegmentedControl
            fullWidth
            aria-label={t('bankroll.form.type')}
            data={MOVEMENT_TYPES.map((type) => ({
              value: type,
              label: t(`movementTypes.${type}`),
            }))}
            {...form.getInputProps('type')}
          />
          <Text size="xs" c="dimmed">
            {t(`bankroll.form.typeHelp.${values.type}`)}
          </Text>
        </Stack>

        <SimpleGrid cols={{ base: 1, xs: 2 }}>
          <Select
            label={t('bankroll.form.room')}
            placeholder={t('bankroll.withoutRoom')}
            clearable
            data={rooms.map((candidate) => ({
              value: String(candidate.id),
              label: candidate.active
                ? `${candidate.name} (${candidate.currencyCode})`
                : t('bankroll.form.inactiveRoom', {
                    name: candidate.name,
                    currency: candidate.currencyCode,
                  }),
            }))}
            {...form.getInputProps('roomId')}
          />
          {room ? (
            // The currency of a movement in a room is the one of the room.
            <TextInput
              label={t('bankroll.form.currency')}
              value={room.currencyCode}
              readOnly
              disabled
            />
          ) : (
            <Select
              label={t('bankroll.form.currency')}
              required
              allowDeselect={false}
              data={currencies.map((candidate) => candidate.code)}
              {...form.getInputProps('currencyCode')}
            />
          )}
          <NumberInput
            data-autofocus
            label={t('bankroll.form.amount')}
            description={
              values.type === 'ADJUSTMENT' ? t('bankroll.form.adjustmentHelp') : undefined
            }
            required
            allowNegative={values.type === 'ADJUSTMENT'}
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
            {...form.getInputProps('amount')}
          />
          <TextInput
            type="date"
            label={t('bankroll.form.date')}
            required
            {...form.getInputProps('occurredOn')}
          />
        </SimpleGrid>

        <Textarea
          label={t('gameForm.notes')}
          autosize
          minRows={1}
          maxRows={4}
          maxLength={5000}
          {...form.getInputProps('notes')}
        />

        <Group justify="flex-end" gap="sm">
          <Button variant="subtle" color="gray" onClick={onCancel} disabled={saving}>
            {t('actions.cancel')}
          </Button>
          <Button type="submit" loading={saving}>
            {t('gameForm.save')}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
