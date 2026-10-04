import {
  Alert,
  Button,
  Divider,
  Group,
  NumberInput,
  SegmentedControl,
  Select,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../api/client';
import type { Game, GameRequest, GameStatus, GameType, Room, Variant } from '../api/types';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { useFormat } from '../format/useFormat';
import { amountOrNull, type Amount } from './amount';
import { loadGameDefaults, saveGameDefaults, todayIso } from './gameDefaults';
import { variantLabel } from './labels';
import { NameInput } from './NameInput';
import { TagsField } from './TagsField';
import { useNameSuggestions, type NameFields } from './useNameSuggestions';

/** Several games of one sitting: tournaments or Sit & Go (a cash game is one sitting already). */
type BulkGameType = Extract<GameType, 'TOURNAMENT' | 'SIT_AND_GO'>;

const GAME_TYPES: readonly BulkGameType[] = ['TOURNAMENT', 'SIT_AND_GO'];
const MODALITIES = ['NLHE', 'PLO'] as const;
const STATUSES: readonly GameStatus[] = ['IN_PLAY', 'FINISHED'];

/** Games added at once: at least two (one is the add game form), at most what the API takes. */
export const MIN_GAMES = 2;
export const MAX_GAMES = 50;

/** What changes from one game to the next. */
interface BulkRow {
  prize: Amount;
  bounty: Amount;
  notes: string;
}

/** The fields of a row that the backend can find wrong. */
const ROW_FIELDS: readonly string[] = ['prize', 'bounty', 'notes'];

interface BulkAddValues extends NameFields {
  gameType: BulkGameType;
  roomId: string | null;
  playedOn: string;
  count: Amount;
  status: GameStatus;
  /** Given to every game. */
  tags: string[];
  /** Always {@link MAX_GAMES} rows: those past `count` keep what was typed but are not sent. */
  rows: BulkRow[];
}

interface BulkAddFormProps {
  rooms: Room[];
  variants: Variant[];
  /** Saves the games, all of them or none; rejects with an `ApiError` when the backend refuses. */
  onSave: (games: GameRequest[]) => Promise<Game[]>;
  onSaved: (games: Game[]) => void;
  onCancel: () => void;
}

/**
 * Form to record several games alike at once (ten Expressos of an evening): what they share is
 * written once, then the result of each one, in rows. In play, the rows only take notes.
 */
export function BulkAddForm({ rooms, variants, onSave, onSaved, onCancel }: BulkAddFormProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const narrow = useNarrowScreen();
  const submitting = useRef(false);
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const activeRooms = rooms.filter((room) => room.active);

  const form = useForm<BulkAddValues>({
    initialValues: initialValues(activeRooms),
    validate: {
      roomId: (value) => (value ? null : t('gameForm.errors.required')),
      playedOn: (value) => (value ? null : t('gameForm.errors.required')),
      buyIn: (value) => (amountOrNull(value) === null ? t('gameForm.errors.required') : null),
      count: (value) =>
        countOf(value) === null
          ? t('bulkAdd.errors.count', { min: MIN_GAMES, max: MAX_GAMES })
          : null,
    },
  });
  const values = form.values;
  const isFinished = values.status === 'FINISHED';
  // Bounties are for tournaments (KO); a Sit & Go rarely has them.
  const withBounty = isFinished && values.gameType === 'TOURNAMENT';
  const room = activeRooms.find((candidate) => String(candidate.id) === values.roomId);
  const currency = room?.currencyCode;
  // While the number is being typed ("1" on the way to "10"), the rows of the last valid one stay.
  const [count, setCount] = useState(() => countOf(values.count) ?? MIN_GAMES);
  const rows = values.rows.slice(0, count);

  const variantOptions = variants
    .filter((variant) => variant.gameType === values.gameType && variant.active)
    .map((variant) => ({ value: String(variant.id), label: variantLabel(t, variant) }));

  const names = useNameSuggestions(form, { gameType: values.gameType, currency, variantOptions });
  const { filledByName } = names;

  const amountProps = {
    min: 0,
    decimalScale: 2,
    decimalSeparator: format.decimalSeparator,
    allowedDecimalSeparators: [',', '.'],
    hideControls: true,
  };
  const currencySection = currency
    ? {
        rightSection: (
          <Text size="xs" c="dimmed">
            {currency}
          </Text>
        ),
        rightSectionWidth: 48,
        rightSectionPointerEvents: 'none' as const,
      }
    : {};

  // Figures of what is about to be recorded, in cents so that sums are exact.
  const buyInCents = toCents(values.buyIn);
  const rowWonCents = (row: BulkRow) =>
    isFinished ? toCents(row.prize) + (withBounty ? toCents(row.bounty) : 0) : 0;
  const investedCents = buyInCents * count;
  const wonCents = rows.reduce((sum, row) => sum + rowWonCents(row), 0);

  function toRequests(): GameRequest[] {
    return rows.map((row) => ({
      playedOn: values.playedOn,
      playedAt: null,
      roomId: Number(values.roomId),
      gameType: values.gameType,
      modality: values.modality,
      variantId: values.variantId ? Number(values.variantId) : null,
      status: values.status,
      name: values.name.trim() || null,
      buyIn: amountOrNull(values.buyIn) ?? 0,
      entries: 1,
      paidWithTicket: false,
      // An empty prize is nothing won.
      prize: isFinished ? (amountOrNull(row.prize) ?? 0) : null,
      bounty: withBounty ? (amountOrNull(row.bounty) ?? 0) : null,
      ticketPrizeValue: null,
      ticketDescription: null,
      notes: row.notes.trim() || null,
      tags: values.tags,
    }));
  }

  /** Puts each validation error of the backend on its field: `games[3].prize` is row 4. */
  function showFieldErrors(error: ApiError): boolean {
    let shown = false;
    for (const violation of error.errors) {
      // A tag is named by its position too (`games[3].tags[0]`): it goes on the field of the tags.
      const match = /^games\[(\d+)\]\.(\w+)(?:\[\d+\])?$/.exec(violation.field ?? '');
      if (!match) {
        continue;
      }
      const [, index = '', field = ''] = match;
      if (ROW_FIELDS.includes(field)) {
        form.setFieldError(`rows.${index}.${field}`, violation.message);
        shown = true;
      } else if (field in values && field !== 'rows') {
        // What the games share is wrong for all of them: shown once, on the field.
        form.setFieldError(field, violation.message);
        shown = true;
      }
    }
    return shown;
  }

  async function submit() {
    // A ref, not the state: a second Enter can arrive before the state of the first is rendered.
    if (submitting.current || form.validate().hasErrors) {
      return;
    }
    submitting.current = true;
    setFailure(null);
    setSaving(true);
    try {
      const saved = await onSave(toRequests());
      saveGameDefaults({
        roomId: Number(values.roomId),
        gameType: values.gameType,
        status: values.status,
      });
      onSaved(saved);
    } catch (error) {
      if (error instanceof ApiError) {
        // Anything that cannot be shown next to a field goes on top, saying which game it was.
        const shown = showFieldErrors(error);
        setFailure(
          shown
            ? null
            : error.index === null
              ? error.message
              : t('bulkAdd.errors.ofGame', { number: error.index + 1, message: error.message }),
        );
      } else {
        setFailure(t('gameForm.errors.unexpected'));
      }
    } finally {
      submitting.current = false;
      setSaving(false);
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

  const money = (cents: number, signed = false) =>
    currency === undefined
      ? '—'
      : signed
        ? format.signedMoney(cents / 100, currency)
        : format.money(cents / 100, currency);

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
          <Alert color="red" title={t('bulkAdd.errors.notSaved')}>
            {failure}
          </Alert>
        )}

        <SegmentedControl
          fullWidth
          aria-label={t('gameForm.gameType')}
          data={GAME_TYPES.map((type) => ({ value: type, label: t(`gameTypes.${type}`) }))}
          value={values.gameType}
          onChange={(value) => {
            // Variants belong to a type.
            names.forget('variantId');
            form.setValues({ gameType: value as BulkGameType, variantId: null });
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
          <TextInput
            type="date"
            label={t('gameForm.playedOn')}
            required
            {...form.getInputProps('playedOn')}
          />
          <NumberInput
            data-autofocus
            label={t('gameForm.buyIn')}
            required
            {...amountProps}
            {...currencySection}
            {...filledByName('buyIn')}
          />
          <NumberInput
            label={t('bulkAdd.count')}
            required
            min={MIN_GAMES}
            max={MAX_GAMES}
            allowDecimal={false}
            allowNegative={false}
            {...form.getInputProps('count')}
            onChange={(value) => {
              form.setFieldValue('count', value);
              const valid = countOf(value);
              if (valid !== null) {
                setCount(valid);
              }
            }}
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
            <Text size="sm" fw={500} id="bulk-add-modality">
              {t('gameForm.modality')}
            </Text>
            <SegmentedControl
              aria-labelledby="bulk-add-modality"
              data={MODALITIES.map((modality) => ({
                value: modality,
                label: t(`modalities.${modality}`),
              }))}
              {...filledByName('modality')}
            />
          </Stack>
          <Stack gap={4}>
            <Text size="sm" fw={500} id="bulk-add-status">
              {t('gameForm.status')}
            </Text>
            <SegmentedControl
              aria-labelledby="bulk-add-status"
              data={STATUSES.map((status) => ({
                value: status,
                label: t(`gameStatus.${status}`),
              }))}
              {...form.getInputProps('status')}
            />
          </Stack>
        </SimpleGrid>

        <TagsField {...form.getInputProps('tags')} />

        <Divider />

        <Stack gap={4}>
          <Text size="sm" fw={500}>
            {t('bulkAdd.rows.title')}
          </Text>
          <Text size="xs" c="dimmed">
            {withBounty
              ? t('bulkAdd.rows.finishedHelp')
              : isFinished
                ? t('bulkAdd.rows.finishedNoBountyHelp')
                : t('bulkAdd.rows.inPlayHelp')}
          </Text>
        </Stack>

        <Stack gap={narrow ? 'sm' : 'xs'} role="list" aria-label={t('bulkAdd.rows.label')}>
          {!narrow && isFinished && (
            // Column headings: the inputs of each row are labelled for screen readers already.
            <Group gap="xs" wrap="nowrap" aria-hidden>
              <Text size="xs" c="dimmed" w={28}>
                #
              </Text>
              <Text size="xs" c="dimmed" w={140}>
                {t('gameForm.prize')}
              </Text>
              {withBounty && (
                <Text size="xs" c="dimmed" w={140}>
                  {t('gameForm.bounty')}
                </Text>
              )}
              <Text size="xs" c="dimmed" style={{ flex: 1 }}>
                {t('gameForm.notes')}
              </Text>
              <Text size="xs" c="dimmed" w={96} ta="right">
                {t('games.columns.net')}
              </Text>
            </Group>
          )}
          {rows.map((row, index) => {
            const number = index + 1;
            const prize = isFinished && (
              <NumberInput
                aria-label={t('bulkAdd.rows.prize', { number })}
                placeholder={narrow ? t('gameForm.prize') : '0'}
                w={narrow ? undefined : 140}
                style={narrow ? { flex: 1 } : undefined}
                {...amountProps}
                {...form.getInputProps(`rows.${index}.prize`)}
              />
            );
            const bounty = withBounty && (
              <NumberInput
                aria-label={t('bulkAdd.rows.bounty', { number })}
                placeholder={narrow ? t('gameForm.bounty') : '0'}
                w={narrow ? undefined : 140}
                style={narrow ? { flex: 1 } : undefined}
                {...amountProps}
                {...form.getInputProps(`rows.${index}.bounty`)}
              />
            );
            const notes = (
              <TextInput
                aria-label={t('bulkAdd.rows.notes', { number })}
                placeholder={t('gameForm.notes')}
                maxLength={5000}
                style={{ flex: 1 }}
                {...form.getInputProps(`rows.${index}.notes`)}
              />
            );
            const label = (
              <Text size="sm" c="dimmed" w={28} pt={6} style={{ flexShrink: 0 }}>
                {number}
              </Text>
            );
            return (
              <div role="listitem" key={index}>
                {narrow && withBounty ? (
                  // A phone has no room for three inputs: the result first, the notes below it.
                  <Stack gap={4}>
                    <Group gap="xs" wrap="nowrap" align="flex-start">
                      {label}
                      {prize}
                      {bounty}
                    </Group>
                    <Group gap="xs" wrap="nowrap">
                      <div style={{ width: 28, flexShrink: 0 }} />
                      {notes}
                    </Group>
                  </Stack>
                ) : (
                  <Group gap="xs" wrap="nowrap" align="flex-start">
                    {label}
                    {prize}
                    {bounty}
                    {notes}
                    {!narrow && isFinished && (
                      <Text size="sm" w={96} ta="right" pt={6} style={{ flexShrink: 0 }}>
                        {money(rowWonCents(row) - buyInCents, true)}
                      </Text>
                    )}
                  </Group>
                )}
              </div>
            );
          })}
        </Stack>

        <SimpleGrid cols={3} spacing="xs" aria-label={t('bulkAdd.totals.label')} role="group">
          <Figure label={t('bulkAdd.totals.invested')} value={money(investedCents)} />
          <Figure label={t('bulkAdd.totals.won')} value={money(wonCents)} />
          <Figure label={t('bulkAdd.totals.net')} value={money(wonCents - investedCents, true)} />
        </SimpleGrid>

        <Group justify="flex-end" gap="sm">
          <Button variant="subtle" color="gray" onClick={onCancel} disabled={saving}>
            {t('gameForm.cancel')}
          </Button>
          <Button type="submit" loading={saving}>
            {t('bulkAdd.save', { count })}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <Stack gap={0}>
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      <Text size="sm" fw={600} style={{ whiteSpace: 'nowrap' }}>
        {value}
      </Text>
    </Stack>
  );
}

/** The number of games asked for, when it is one that can be added. */
function countOf(value: Amount): number | null {
  const count = amountOrNull(value);
  return count !== null && Number.isInteger(count) && count >= MIN_GAMES && count <= MAX_GAMES
    ? count
    : null;
}

function toCents(value: Amount): number {
  return Math.round((amountOrNull(value) ?? 0) * 100);
}

function initialValues(activeRooms: Room[]): BulkAddValues {
  const defaults = loadGameDefaults();
  // The room used last when it is still offered; the only room when there is just one.
  const room =
    activeRooms.find((candidate) => candidate.id === defaults.roomId) ??
    (activeRooms.length === 1 ? activeRooms[0] : undefined);
  return {
    gameType: defaults.gameType === 'SIT_AND_GO' ? 'SIT_AND_GO' : 'TOURNAMENT',
    roomId: room ? String(room.id) : null,
    playedOn: todayIso(),
    variantId: null,
    modality: 'NLHE',
    name: '',
    buyIn: '',
    count: MIN_GAMES,
    status: defaults.status ?? 'IN_PLAY',
    tags: [],
    rows: Array.from({ length: MAX_GAMES }, () => ({ prize: '', bounty: '', notes: '' })),
  };
}
