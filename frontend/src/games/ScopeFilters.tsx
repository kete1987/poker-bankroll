import { MultiSelect } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import type { GameType, Room, Tag, Variant } from '../api/types';
import { variantLabel } from './labels';
import type { GameScope } from './scope';

export type { GameScope } from './scope';

const GAME_TYPES: readonly GameType[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];

interface ScopeFiltersProps extends GameScope {
  rooms: Room[];
  variants: Variant[];
  tags: Tag[];
  onChange: (changes: Partial<GameScope>) => void;
}

/**
 * Multi-select filters by game type, room, variant and tag, side by side, to be placed in a row
 * of filters. Rooms and variants include the inactive ones: they have history. The filter of tags
 * only shows when there are tags.
 */
export function ScopeFilters({
  gameTypes,
  roomIds,
  variantIds,
  tagIds,
  rooms,
  variants,
  tags,
  onChange,
}: ScopeFiltersProps) {
  const { t } = useTranslation();

  // Variants of the chosen types; of every type when none is chosen. The type goes in front
  // unless a single one is chosen, since variants of different types share names (Regular).
  const singleType = gameTypes.length === 1;
  const variantOptions = variants
    .filter((variant) => gameTypes.length === 0 || gameTypes.includes(variant.gameType))
    .map((variant) => ({
      value: String(variant.id),
      label: singleType
        ? variantLabel(t, variant)
        : `${t(`gameTypes.${variant.gameType}`)} · ${variantLabel(t, variant)}`,
    }));

  return (
    <>
      <MultiSelect
        label={t('filters.gameType')}
        miw={180}
        maw={360}
        clearable
        placeholder={gameTypes.length === 0 ? t('filters.any') : undefined}
        data={GAME_TYPES.map((type) => ({ value: type, label: t(`gameTypes.${type}`) }))}
        value={gameTypes}
        onChange={(values) => {
          const chosen = values as GameType[];
          // A variant belongs to a type: those of types no longer chosen are dropped.
          const kept =
            chosen.length === 0
              ? variantIds
              : variantIds.filter((id) => {
                  const variant = variants.find((candidate) => candidate.id === id);
                  return variant !== undefined && chosen.includes(variant.gameType);
                });
          onChange({ gameTypes: chosen, variantIds: kept });
        }}
      />
      <MultiSelect
        label={t('filters.room')}
        miw={180}
        maw={360}
        clearable
        placeholder={roomIds.length === 0 ? t('filters.any') : undefined}
        data={rooms.map((room) => ({ value: String(room.id), label: room.name }))}
        value={roomIds.map(String)}
        onChange={(values) => onChange({ roomIds: values.map(Number) })}
      />
      <MultiSelect
        label={t('filters.variant')}
        miw={220}
        maw={420}
        clearable
        searchable
        placeholder={variantIds.length === 0 ? t('filters.any') : undefined}
        data={variantOptions}
        value={variantIds.map(String)}
        onChange={(values) => onChange({ variantIds: values.map(Number) })}
      />
      {(tags.length > 0 || tagIds.length > 0) && (
        <MultiSelect
          label={t('filters.tag')}
          miw={180}
          maw={360}
          clearable
          searchable
          placeholder={tagIds.length === 0 ? t('filters.any') : undefined}
          data={tags.map((tag) => ({ value: String(tag.id), label: tag.name }))}
          value={tagIds.map(String)}
          onChange={(values) => onChange({ tagIds: values.map(Number) })}
        />
      )}
    </>
  );
}
