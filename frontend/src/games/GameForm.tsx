import {
  Alert,
  Autocomplete,
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
import { useDebouncedValue } from '@mantine/hooks';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../api/client';
import { MIN_NAME_SEARCH_LENGTH, useGameNames } from '../api/games';
import type {
  Game,
  GameName,
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

const GAME_TYPES: readonly GameType[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];
const MODALITIES: readonly Modality[] = ['NLHE', 'PLO'];
const STATUSES: readonly GameStatus[] = ['IN_PLAY', 'FINISHED'];

/** How long typing must pause before names are asked for. */
const NAME_SEARCH_DELAY_MS = 250;

/** The fields that picking a suggested name fills in, unless the user has set them. */
type FilledByName = 'buyIn' | 'variantId' | 'modality';

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
export function GameForm({ rooms, variants, game, onSave, onSaved, onCancel }: GameFormProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const buyInRef = useRef<HTMLInputElement>(null);
  const submitting = useRef(false);
  // What the user has set by hand in this form: a suggested name never replaces it.
  const setByHand = useRef(new Set<FilledByName>());
  const [saving, setSaving] = useState<'save' | 'another' | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  // Inactive rooms take no new games, but a game stays in the room it was recorded in.
  const activeRooms = rooms.filter((room) => room.active || room.id === game?.room.id);

  const form = useForm<GameFormValues>({
    initialValues: game ? valuesOf(game) : initialValues(activeRooms),
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

  // Names are suggested from the second character, for the type of the form, once typing pauses;
  // none for the name a game being edited already has.
  const [nameSearch] = useDebouncedValue(values.name.trim(), NAME_SEARCH_DELAY_MS);
  const searchesName =
    nameSearch.length >= MIN_NAME_SEARCH_LENGTH && nameSearch !== (game?.name ?? '');
  const names = useGameNames(searchesName ? nameSearch : '', values.gameType);
  // Those of the previous search stay while the next one loads, but not after the type changes.
  const suggestions = searchesName
    ? (names.data ?? []).filter((suggestion) => suggestion.gameType === values.gameType)
    : [];

  /** Input props of a field that a suggested name can fill in, noting when the user sets it. */
  function filledByName(field: FilledByName) {
    const props = form.getInputProps(field);
    return {
      ...props,
      onChange: (value: unknown) => {
        setByHand.current.add(field);
        props.onChange(value);
      },
    };
  }

  /**
   * A suggested name was picked: a new game takes the buy-in, variant and modality of the last
   * game with that name, except what the user has already set. An edited game only takes the name.
   * The buy-in is only taken when that game was in the currency of the chosen room: 50 dollars
   * are not 50 euros.
   */
  function fillFromName(name: string) {
    const suggestion = suggestions.find((candidate) => candidate.name === name);
    if (!suggestion || game) {
      return;
    }
    const filled: Partial<GameFormValues> = {};
    if (!setByHand.current.has('buyIn') && suggestion.currencyCode === currency) {
      filled.buyIn = suggestion.buyIn;
      form.clearFieldError('buyIn');
    }
    if (!setByHand.current.has('modality')) {
      filled.modality = suggestion.modality;
    }
    if (!setByHand.current.has('variantId')) {
      const variantId = suggestion.variant ? String(suggestion.variant.id) : null;
      // A variant that is no longer offered (inactive) is not chosen.
      if (variantId === null || variantOptions.some((option) => option.value === variantId)) {
        filled.variantId = variantId;
      }
    }
    form.setValues(filled);
  }

  /** What tells a suggested name apart: the buy-in and the variant of its last game. */
  function nameHint(suggestion: GameName): string {
    return [
      format.money(suggestion.buyIn, suggestion.currencyCode),
      suggestion.variant && variantLabel(t, suggestion.variant),
    ]
      .filter(Boolean)
      .join(' · ');
  }

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
        setByHand.current.clear();
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
            setByHand.current.delete('variantId');
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
          {/* Free text, with the names of earlier games to pick from: Enter on one picks it. */}
          <Autocomplete
            label={t('gameForm.name')}
            maxLength={150}
            data={suggestions.map((suggestion) => suggestion.name)}
            // The backend has already searched them.
            filter={({ options }) => options}
            renderOption={({ option }) => {
              const suggestion = suggestions.find((candidate) => candidate.name === option.value);
              return (
                <div>
                  <Text size="sm">{option.value}</Text>
                  {suggestion && (
                    <Text size="xs" c="dimmed">
                      {nameHint(suggestion)}
                    </Text>
                  )}
                </div>
              );
            }}
            onOptionSubmit={fillFromName}
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
