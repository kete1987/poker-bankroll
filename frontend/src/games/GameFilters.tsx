import { Button, TextInput } from '@mantine/core';
import { useDebouncedCallback } from '@mantine/hooks';
import { IconSearch, IconX } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { Room, Tag, Variant } from '../api/types';
import { FilterBar } from '../components/FilterBar';
import { PeriodFilter } from '../components/PeriodFilter';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { scopeFilterCount } from './scope';
import { ScopeFilters } from './ScopeFilters';
import type { GameFilters as Filters } from './useGameFilters';

interface GameFiltersProps {
  filters: Filters;
  rooms: Room[];
  variants: Variant[];
  tags: Tag[];
  hasFilters: boolean;
  onChange: (changes: Partial<Filters>) => void;
  onClear: () => void;
}

/**
 * Filters of the games table. Rooms and variants include the inactive ones: they have history.
 */
export function GameFilters({
  filters,
  rooms,
  variants,
  tags,
  hasFilters,
  onChange,
  onClear,
}: GameFiltersProps) {
  const { t } = useTranslation();
  const narrow = useNarrowScreen();
  const [cleared, setCleared] = useState(0);

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

  return (
    <FilterBar
      activeCount={scopeFilterCount(filters) + (filters.q ? 1 : 0)}
      primary={
        // The key resets "Custom" when the filters are cleared.
        <PeriodFilter
          key={cleared}
          range={{ from: filters.from, to: filters.to }}
          onChange={(range) => onChange({ from: range.from, to: range.to })}
        />
      }
    >
      <ScopeFilters
        gameTypes={filters.gameTypes}
        roomIds={filters.roomIds}
        variantIds={filters.variantIds}
        tagIds={filters.tagIds}
        rooms={rooms}
        variants={variants}
        tags={tags}
        onChange={onChange}
      />
      <TextInput
        label={t('filters.text')}
        placeholder={t('filters.textPlaceholder')}
        w={narrow ? undefined : 220}
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
            setCleared((count) => count + 1);
            onClear();
          }}
        >
          {t('filters.clear')}
        </Button>
      )}
    </FilterBar>
  );
}
