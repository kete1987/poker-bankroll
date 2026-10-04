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
import { amountOrNull, type Amount } from './amount';
import { loadGameDefaults, saveGameDefaults, todayIso } from './gameDefaults';
import { variantLabel } from './labels';
import { NameInput } from './NameInput';
import { useNameSuggestions } from './useNameSuggestions';

const GAME_TYPES: readonly GameType[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];
const MODALITIES: readonly Modality[] = ['NLHE', 'PLO'];
const STATUSES: readonly GameStatus[] = ['IN_PLAY', 'FINISHED'];

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
  /** The game being edited; a new one is recorded when absent. */
  game?: Game;
  /**
   * A new game like this one (duplicate): same room, type, variant, modality, name and buy-in,
   * but today and without its result, entries or notes.
   */
  copyOf?: Game;
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
export function GameForm({
  rooms,
  variants,
  game,
  copyOf,
  onSave,
  onSaved,
  onCancel,
}: GameFormProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const buyInRef = useRef<HTMLInputElement>(null);
  const submitting = useRef(false);
  const [saving, setSaving] = useState<'save' | 'another' | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  // Inactive rooms take no new games, but a game stays in the room it was recorded in.
  const activeRooms = rooms.filter((room) => room.active || room.id === game?.room.id);

  const form = useForm<GameFormValues>({
    initialValues: game
      ? valuesOf(game)
      : copyOf
        ? copyValues(copyOf, activeRooms, variants)
        : initialValues(activeRooms),
    validate: {
      roomId: (value) => (value ? null : t('gameForm.errors.required')),
      playedOn: (value) => (value ? null : t('gameForm.errors.required')),
      buyIn: (value) => (amountOrNull(value) === null ? t('gameForm.errors.required') : null),
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
    .filter(
      (variant) =>
        variant.gameType === values.gameType &&
        (variant.active || variant.id === game?.variant?.id),
    )
    .map((variant) => ({ value: String(variant.id), label: variantLabel(t, variant) }));

  const names = useNameSuggestions(form, {
    gameType: values.gameType,
    currency,
    variantOptions,
    editing: game,
    // What a copy brings counts as chosen: a name picked afterwards does not replace it.
    chosen: copyOf && { buyInCurrency: copyOf.currencyCode },
  });
  const { filledByName } = names;

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
    // A ref, not the state: a second Enter can arrive before the state of the first is rendered.
    if (submitting.current || form.validate().hasErrors) {
      return;
    }
    submitting.current = true;
    setFailure(null);
    setSaving(addAnother ? 'another' : 'save');
    try {
      const saved = await onSave(toRequest(form.values));
      if (!game) {
        saveGameDefaults({
          roomId: saved.room.id,
          gameType: saved.gameType,
          status: form.values.status,
        });
      }
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
        // What is kept comes from the game just saved: a suggested name may replace it.
        names.reset();
        buyInRef.current?.focus();
        buyInRef.current?.select();
      }
      onSaved(saved, addAnother);
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
      submitting.current = false;
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
        // Ctrl/Cmd + Enter: save and go on with the next game. Not when the key picked an option
        // of a list (default prevented): that never saves.
        if (
          !game &&
          event.key === 'Enter' &&
          (event.ctrlKey || event.metaKey) &&
          !event.defaultPrevented
        ) {
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
            names.forget('variantId');
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
            onChange={(value) => {
              const chosen = activeRooms.find((candidate) => String(candidate.id) === value);
              names.roomChanged(chosen?.currencyCode);
              form.setFieldValue('roomId', value);
            }}
          />
          <NumberInput
            ref={buyInRef}
            data-autofocus
            label={isCash ? t('gameForm.buyInCash') : t('gameForm.buyIn')}
            required
            {...amountProps}
            {...filledByName('buyIn')}
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
              {...filledByName('variantId')}
            />
          )}
          <NameInput
            label={t('gameForm.name')}
            suggestions={names.suggestions}
            onOptionSubmit={names.fillFromName}
            {...form.getInputProps('name')}
          />
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
              {...filledByName('modality')}
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
          {!game && (
            <Button
              variant="default"
              onClick={() => void submit(true)}
              loading={saving === 'another'}
              disabled={saving === 'save'}
              title={t('gameForm.saveAndAddAnotherHint')}
            >
              {t('gameForm.saveAndAddAnother')}
            </Button>
          )}
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

/**
 * A new game like the one given: where and what it was, today and in the status a new game gets.
 * A room or variant that no longer takes games (inactive) is left to choose.
 */
function copyValues(original: Game, activeRooms: Room[], variants: Variant[]): GameFormValues {
  const offered = (id: number | undefined, candidates: { id: number; active: boolean }[]) =>
    candidates.some((candidate) => candidate.id === id && candidate.active);
  return {
    ...initialValues(activeRooms),
    gameType: original.gameType,
    roomId: offered(original.room.id, activeRooms) ? String(original.room.id) : null,
    variantId: offered(original.variant?.id, variants) ? String(original.variant!.id) : null,
    modality: original.modality,
    name: original.name ?? '',
    buyIn: original.buyIn,
  };
}

function valuesOf(game: Game): GameFormValues {
  return {
    gameType: game.gameType,
    roomId: String(game.room.id),
    playedOn: game.playedOn,
    playedAt: game.playedAt?.slice(0, 5) ?? '',
    variantId: game.variant ? String(game.variant.id) : null,
    modality: game.modality,
    name: game.name ?? '',
    buyIn: game.buyIn,
    entries: game.entries,
    paidWithTicket: game.paidWithTicket,
    status: game.status,
    // Nothing won is shown as an empty field, like when it is typed.
    prize: game.prize === 0 ? '' : game.prize,
    bounty: game.bounty === 0 ? '' : game.bounty,
    wonTicket: game.ticketPrizeValue > 0,
    ticketPrizeValue: game.ticketPrizeValue === 0 ? '' : game.ticketPrizeValue,
    ticketDescription: game.ticketDescription ?? '',
    notes: game.notes ?? '',
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
    buyIn: amountOrNull(values.buyIn) ?? 0,
    entries: isCash ? null : amountOrNull(values.entries),
    paidWithTicket: !isCash && values.paidWithTicket,
    prize: isFinished ? amountOrNull(values.prize) : null,
    bounty: isFinished && !isCash ? amountOrNull(values.bounty) : null,
    ticketPrizeValue: wonTicket ? amountOrNull(values.ticketPrizeValue) : null,
    ticketDescription: wonTicket ? values.ticketDescription.trim() || null : null,
    notes: values.notes.trim() || null,
  };
}
