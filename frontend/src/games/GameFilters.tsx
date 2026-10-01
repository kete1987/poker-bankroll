import { Button, Group, MultiSelect, Select, TextInput } from '@mantine/core';
import { useDebouncedCallback } from '@mantine/hooks';
import { IconSearch, IconX } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { GameType, Room, Variant } from '../api/types';
import { variantLabel } from './labels';
import { PERIODS, periodOf, rangeOf, type Period } from './period';
import type { GameFilters as Filters } from './useGameFilters';

const GAME_TYPES: readonly GameType[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];

interface GameFiltersProps {
  filters: Filters;
  rooms: Room[];
  variants: Variant[];
  hasFilters: boolean;
  onChange: (changes: Partial<Filters>) => void;
  onClear: () => void;
}

/** Filters of the games table. Rooms and variants include the inactive ones: they have history. */
export function GameFilters({
  filters,
  rooms,
  variants,
  hasFilters,
  onChange,
  onClear,
}: GameFiltersProps) {
  const { t } = useTranslation();
  const period = periodOf(filters);
  // "Custom" is also a choice in itself: it shows the two dates before any is typed.
  const [customChosen, setCustomChosen] = useState(false);
  const showDates = period === 'custom' || customChosen;

  // The text is searched a moment after the last key, not on every one.
  const [text, setText] = useState(filters.q ?? '');
  const search = useDebouncedCallback((value: string) => onChange({ q: value.trim() }), 300);
  // When the filter changes from outside (cleared, back button), the box follows it.
  const [syncedText, setSyncedText] = useState(filters.q);
  if (filters.q !== syncedText) {
    setSyncedText(filters.q);
    if ((filters.q ?? '') !== text.trim()) {
      setText(filters.q ?? '');
    }
  }

  // Variants of the chosen types; of every type when none is chosen. The type goes in front
  // unless a single one is chosen, since variants of different types share names (Regular).
  const singleType = filters.gameTypes.length === 1;
  const variantOptions = variants
    .filter(
      (variant) => filters.gameTypes.length === 0 || filters.gameTypes.includes(variant.gameType),
    )
    .map((variant) => ({
      value: String(variant.id),
      label: singleType
        ? variantLabel(t, variant)
        : `${t(`gameTypes.${variant.gameType}`)} · ${variantLabel(t, variant)}`,
    }));

  return (
    <Group gap="sm" align="flex-end">
      <Select
        label={t('games.filters.period')}
        w={170}
        allowDeselect={false}
        data={PERIODS.map((value) => ({ value, label: t(`games.periods.${value}`) }))}
        value={showDates ? 'custom' : period}
        onChange={(value) => {
          const chosen = value as Period;
          setCustomChosen(chosen === 'custom');
          if (chosen !== 'custom') {
            const range = rangeOf(chosen);
            onChange({ from: range.from, to: range.to });
          }
        }}
      />
      {showDates && (
        <>
          <TextInput
            type="date"
            label={t('games.filters.from')}
            w={160}
            value={filters.from ?? ''}
            max={filters.to}
            onChange={(event) => onChange({ from: event.currentTarget.value || undefined })}
          />
          <TextInput
            type="date"
            label={t('games.filters.to')}
            w={160}
            value={filters.to ?? ''}
            min={filters.from}
            onChange={(event) => onChange({ to: event.currentTarget.value || undefined })}
          />
        </>
      )}
      <MultiSelect
        label={t('games.filters.gameType')}
        miw={180}
        maw={360}
        clearable
        placeholder={filters.gameTypes.length === 0 ? t('games.filters.any') : undefined}
        data={GAME_TYPES.map((type) => ({ value: type, label: t(`gameTypes.${type}`) }))}
        value={filters.gameTypes}
        onChange={(values) => {
          const gameTypes = values as GameType[];
          // A variant belongs to a type: those of types no longer chosen are dropped.
          const variantIds =
            gameTypes.length === 0
              ? filters.variantIds
              : filters.variantIds.filter((id) => {
                  const variant = variants.find((candidate) => candidate.id === id);
                  return variant !== undefined && gameTypes.includes(variant.gameType);
                });
          onChange({ gameTypes, variantIds });
        }}
      />
      <MultiSelect
        label={t('games.filters.room')}
        miw={180}
        maw={360}
        clearable
        placeholder={filters.roomIds.length === 0 ? t('games.filters.any') : undefined}
        data={rooms.map((room) => ({ value: String(room.id), label: room.name }))}
        value={filters.roomIds.map(String)}
        onChange={(values) => onChange({ roomIds: values.map(Number) })}
      />
      <MultiSelect
        label={t('games.filters.variant')}
        miw={220}
        maw={420}
        clearable
        searchable
        placeholder={filters.variantIds.length === 0 ? t('games.filters.any') : undefined}
        data={variantOptions}
        value={filters.variantIds.map(String)}
        onChange={(values) => onChange({ variantIds: values.map(Number) })}
      />
      <TextInput
        label={t('games.filters.text')}
        placeholder={t('games.filters.textPlaceholder')}
        w={220}
        leftSection={<IconSearch size={16} />}
        value={text}
        onChange={(event) => {
          setText(event.currentTarget.value);
          search(event.currentTarget.value);
        }}
      />
      {hasFilters && (
        <Button
          variant="subtle"
          color="gray"
          leftSection={<IconX size={16} />}
          onClick={() => {
            // A search still waiting to be sent would bring its text back after clearing.
            search.cancel();
            setText('');
            setCustomChosen(false);
            onClear();
          }}
        >
          {t('games.filters.clear')}
        </Button>
      )}
    </Group>
  );
}
