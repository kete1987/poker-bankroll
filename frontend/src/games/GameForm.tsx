import {
  Alert,
  Button,
  Checkbox,
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
import type {
  Game,
  GameRequest,
  GameStatus,
  GameType,
  Modality,
  Room,
  Variant,
} from '../api/types';
import { useFormat } from '../format/useFormat';
import { loadGameDefaults, saveGameDefaults, todayIso } from './gameDefaults';
import { variantLabel } from './labels';

const GAME_TYPES: readonly GameType[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];
const MODALITIES: readonly Modality[] = ['NLHE', 'PLO'];
const STATUSES: readonly GameStatus[] = ['IN_PLAY', 'FINISHED'];

/** Number inputs hold a number, or an empty string while nothing is typed. */
type Amount = number | '';

interface GameFormValues {
  gameType: GameType;
  roomId: string | null;
  playedOn: string;
  playedAt: string;
  variantId: string | null;
  modality: Modality;
  name: string;
  buyIn: Amount;
  entries: Amount;
  paidWithTicket: boolean;
  status: GameStatus;
  prize: Amount;
  bounty: Amount;
  wonTicket: boolean;
  ticketPrizeValue: Amount;
  ticketDescription: string;
  notes: string;
}

interface GameFormProps {
  rooms: Room[];
  variants: Variant[];
  /** Saves the game; rejects with an `ApiError` when the backend refuses it. */
  onSave: (game: GameRequest) => Promise<Game>;
  /** Called after a save; `addAnother` when the form stays open for the next game. */
  onSaved: (game: Game, addAnother: boolean) => void;
  onCancel: () => void;
}

/**
 * Form to record a game. It starts with today, the room and type used last, and the fields of
 * that type; a game is recorded in play unless it is marked as finished, which shows its result.
 */
export function GameForm({ rooms, variants, onSave, onSaved, onCancel }: GameFormProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const buyInRef = useRef<HTMLInputElement>(null);
  const [saving, setSaving] = useState<'save' | 'another' | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  const activeRooms = rooms.filter((room) => room.active);

  const form = useForm<GameFormValues>({
    initialValues: initialValues(activeRooms),
    validate: {
      roomId: (value) => (value ? null : t('gameForm.errors.required')),
      playedOn: (value) => (value ? null : t('gameForm.errors.required')),
      buyIn: (value) => (value === '' ? t('gameForm.errors.required') : null),
      ticketPrizeValue: (value, values) =>
        showsTicketWon(values) && !(Number(value) > 0) ? t('gameForm.errors.ticketValue') : null,
    },
  });
  const values = form.values;
  const isCash = values.gameType === 'CASH';
  const isFinished = values.status === 'FINISHED';
  const room = activeRooms.find((candidate) => String(candidate.id) === values.roomId);
  const currency = room?.currencyCode;

  const variantOptions = variants
    .filter((variant) => variant.gameType === values.gameType && variant.active)
    .map((variant) => ({ value: String(variant.id), label: variantLabel(t, variant) }));

  const amountProps = {
    min: 0,
    decimalScale: 2,
    decimalSeparator: format.decimalSeparator,
    // Both separators are accepted, whatever the language.
    allowedDecimalSeparators: [',', '.'],
    hideControls: true,
    rightSection: currency ? (
      <Text size="xs" c="dimmed">
        {currency}
      </Text>
    ) : undefined,
    rightSectionWidth: 48,
    rightSectionPointerEvents: 'none' as const,
  };

  async function submit(addAnother: boolean) {
    if (form.validate().hasErrors) {
      return;
    }
    setFailure(null);
    setSaving(addAnother ? 'another' : 'save');
    try {
      const game = await onSave(toRequest(form.values));
      saveGameDefaults({
        roomId: game.room.id,
        gameType: game.gameType,
        status: form.values.status,
      });
      if (addAnother) {
        // The next game is usually like this one: keep where and what, clear its result.
        form.setValues({
          playedAt: '',
          name: '',
          entries: 1,
          paidWithTicket: false,
          prize: '',
          bounty: '',
          wonTicket: false,
          ticketPrizeValue: '',
          ticketDescription: '',
          notes: '',
        });
        form.clearErrors();
        buyInRef.current?.focus();
        buyInRef.current?.select();
      }
      onSaved(game, addAnother);
    } catch (error) {
      if (error instanceof ApiError) {
        const fieldErrors = error.errors.filter(
          (violation) => violation.field && violation.field in form.values,
        );
        fieldErrors.forEach((violation) => form.setFieldError(violation.field!, violation.message));
        // Anything that cannot be shown next to a field goes on top.
        setFailure(fieldErrors.length > 0 ? null : error.message);
      } else {
        setFailure(t('gameForm.errors.unexpected'));
      }
    } finally {
      setSaving(null);
    }
  }

  if (activeRooms.length === 0) {
    return (
      <Stack>
        <Alert color="yellow" title={t('gameForm.noRooms.title')}>
          {t('gameForm.noRooms.description')}
        </Alert>
        <Group justify="flex-end">
          <Button variant="default" onClick={onCancel}>
            {t('gameForm.cancel')}
          </Button>
        </Group>
      </Stack>
    );
  }

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit(false);
      }}
      onKeyDown={(event) => {
        // Ctrl/Cmd + Enter: save and go on with the next game.
        if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
          event.preventDefault();
          void submit(true);
        }
      }}
    >
      <Stack gap="md">
        {failure && (
          <Alert color="red" title={t('gameForm.errors.notSaved')}>
            {failure}
          </Alert>
        )}

        <SegmentedControl
          fullWidth
          aria-label={t('gameForm.gameType')}
          data={GAME_TYPES.map((type) => ({ value: type, label: t(`gameTypes.${type}`) }))}
          value={values.gameType}
          onChange={(value) => {
            // Variants belong to a type, and a cash game has neither re-entries nor tickets.
            form.setValues({
              gameType: value as GameType,
              variantId: null,
              ...(value === 'CASH'
                ? {
                    entries: 1,
                    paidWithTicket: false,
                    bounty: '',
                    wonTicket: false,
                    ticketPrizeValue: '',
                    ticketDescription: '',
                  }
                : {}),
            });
          }}
        />

        <SimpleGrid cols={{ base: 1, xs: 2 }}>
          <Select
            label={t('gameForm.room')}
            required
            allowDeselect={false}
            data={activeRooms.map((candidate) => ({
              value: String(candidate.id),
              label: `${candidate.name} (${candidate.currencyCode})`,
            }))}
            {...form.getInputProps('roomId')}
          />
          <NumberInput
            ref={buyInRef}
            data-autofocus
            label={isCash ? t('gameForm.buyInCash') : t('gameForm.buyIn')}
            required
            {...amountProps}
            {...form.getInputProps('buyIn')}
          />
          <TextInput
            type="date"
            label={t('gameForm.playedOn')}
            required
            {...form.getInputProps('playedOn')}
          />
          <TextInput
            type="time"
            label={t('gameForm.playedAt')}
            {...form.getInputProps('playedAt')}
          />
          {variantOptions.length > 0 && (
            <Select
              label={t('gameForm.variant')}
              clearable
              data={variantOptions}
              {...form.getInputProps('variantId')}
            />
          )}
          <TextInput label={t('gameForm.name')} maxLength={150} {...form.getInputProps('name')} />
          {!isCash && (
            <NumberInput
              label={t('gameForm.entries')}
              min={1}
              allowDecimal={false}
              {...form.getInputProps('entries')}
            />
          )}
          <Stack gap={4}>
            <Text size="sm" fw={500} id="game-form-modality">
              {t('gameForm.modality')}
            </Text>
            <SegmentedControl
              aria-labelledby="game-form-modality"
              data={MODALITIES.map((modality) => ({
                value: modality,
                label: t(`modalities.${modality}`),
              }))}
              {...form.getInputProps('modality')}
            />
          </Stack>
        </SimpleGrid>

        {!isCash && (
          <Checkbox
            label={t('gameForm.paidWithTicket')}
            description={t('gameForm.paidWithTicketHelp')}
            {...form.getInputProps('paidWithTicket', { type: 'checkbox' })}
          />
        )}

        <Stack gap={4}>
          <Text size="sm" fw={500} id="game-form-status">
            {t('gameForm.status')}
          </Text>
          <SegmentedControl
            aria-labelledby="game-form-status"
            data={STATUSES.map((status) => ({ value: status, label: t(`gameStatus.${status}`) }))}
            {...form.getInputProps('status')}
          />
          <Text size="xs" c="dimmed">
            {isFinished ? t('gameForm.finishedHelp') : t('gameForm.inPlayHelp')}
          </Text>
        </Stack>

        {isFinished && (
          <SimpleGrid cols={{ base: 1, xs: 2 }}>
            <NumberInput
              label={isCash ? t('gameForm.prizeCash') : t('gameForm.prize')}
              placeholder="0"
              {...amountProps}
              {...form.getInputProps('prize')}
            />
            {!isCash && (
              <NumberInput
                label={t('gameForm.bounty')}
                placeholder="0"
                {...amountProps}
                {...form.getInputProps('bounty')}
              />
            )}
          </SimpleGrid>
        )}

        {isFinished && !isCash && (
          <Checkbox
            label={t('gameForm.wonTicket')}
            description={t('gameForm.wonTicketHelp')}
            {...form.getInputProps('wonTicket', { type: 'checkbox' })}
          />
        )}
        {showsTicketWon(values) && (
          <SimpleGrid cols={{ base: 1, xs: 2 }}>
            <NumberInput
              label={t('gameForm.ticketPrizeValue')}
              required
              {...amountProps}
              {...form.getInputProps('ticketPrizeValue')}
            />
            <TextInput
              label={t('gameForm.ticketDescription')}
              maxLength={150}
              {...form.getInputProps('ticketDescription')}
            />
          </SimpleGrid>
        )}

        <Textarea
          label={t('gameForm.notes')}
          autosize
          minRows={1}
          maxRows={4}
          maxLength={5000}
          {...form.getInputProps('notes')}
        />

        <Group justify="flex-end" gap="sm">
          <Button variant="subtle" color="gray" onClick={onCancel} disabled={saving !== null}>
            {t('gameForm.cancel')}
          </Button>
          <Button
            variant="default"
            onClick={() => void submit(true)}
            loading={saving === 'another'}
            disabled={saving === 'save'}
            title={t('gameForm.saveAndAddAnotherHint')}
          >
            {t('gameForm.saveAndAddAnother')}
          </Button>
          <Button type="submit" loading={saving === 'save'} disabled={saving === 'another'}>
            {t('gameForm.save')}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

function showsTicketWon(values: GameFormValues): boolean {
  return values.status === 'FINISHED' && values.gameType !== 'CASH' && values.wonTicket;
}

function initialValues(activeRooms: Room[]): GameFormValues {
  const defaults = loadGameDefaults();
  // The room used last when it is still offered; the only room when there is just one.
  const room =
    activeRooms.find((candidate) => candidate.id === defaults.roomId) ??
    (activeRooms.length === 1 ? activeRooms[0] : undefined);
  return {
    gameType: defaults.gameType ?? 'TOURNAMENT',
    roomId: room ? String(room.id) : null,
    playedOn: todayIso(),
    playedAt: '',
    variantId: null,
    modality: 'NLHE',
    name: '',
    buyIn: '',
    entries: 1,
    paidWithTicket: false,
    status: defaults.status ?? 'IN_PLAY',
    prize: '',
    bounty: '',
    wonTicket: false,
    ticketPrizeValue: '',
    ticketDescription: '',
    notes: '',
  };
}

/** Only what applies to the type and status is sent; the backend fills in the defaults. */
function toRequest(values: GameFormValues): GameRequest {
  const isCash = values.gameType === 'CASH';
  const isFinished = values.status === 'FINISHED';
  const wonTicket = showsTicketWon(values);
  return {
    playedOn: values.playedOn,
    playedAt: values.playedAt || null,
    roomId: Number(values.roomId),
    gameType: values.gameType,
    modality: values.modality,
    variantId: values.variantId ? Number(values.variantId) : null,
    status: values.status,
    name: values.name.trim() || null,
    buyIn: Number(values.buyIn),
    entries: isCash || values.entries === '' ? null : values.entries,
    paidWithTicket: !isCash && values.paidWithTicket,
    prize: isFinished ? amountOrNull(values.prize) : null,
    bounty: isFinished && !isCash ? amountOrNull(values.bounty) : null,
    ticketPrizeValue: wonTicket ? amountOrNull(values.ticketPrizeValue) : null,
    ticketDescription: wonTicket ? values.ticketDescription.trim() || null : null,
    notes: values.notes.trim() || null,
  };
}

function amountOrNull(value: Amount): number | null {
  return value === '' ? null : value;
}
