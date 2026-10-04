import { Autocomplete, Text, type AutocompleteProps } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import type { GameName } from '../api/types';
import { useFormat } from '../format/useFormat';
import { variantLabel } from './labels';

interface NameInputProps extends Omit<AutocompleteProps, 'data' | 'filter' | 'renderOption'> {
  suggestions: GameName[];
}

/** Free text for the name of a game, with the names of earlier games to pick from. */
export function NameInput({ suggestions, ...props }: NameInputProps) {
  const { t } = useTranslation();
  const format = useFormat();

  /** What tells a suggested name apart: the buy-in and the variant of its last game. */
  function nameHint(suggestion: GameName): string {
    return [
      format.money(suggestion.buyIn, suggestion.currencyCode),
      suggestion.variant && variantLabel(t, suggestion.variant),
    ]
      .filter(Boolean)
      .join(' · ');
  }

  return (
    // Enter on a suggestion picks it.
    <Autocomplete
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
      {...props}
    />
  );
}
