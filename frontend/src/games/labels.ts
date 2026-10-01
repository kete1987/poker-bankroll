import type { TFunction } from 'i18next';

import type { Game } from '../api/types';

/** Built-in variant codes that have a translation (`variants.<CODE>` in the locale files). */
const TRANSLATED_VARIANTS = [
  'REGULAR',
  'KO',
  'SPACE_KO',
  'MYSTERY_KO',
  'EXPRESSO',
  'EXPRESSO_NITRO',
  'DOUBLE_OR_NOTHING',
  'DOUBLE_OR_NOTHING_DEMENTE',
  'TRIPLE_OR_NOTHING',
  'TRIPLE_OR_NOTHING_DEMENTE',
  'HEADS_UP',
] as const;

type TranslatedVariant = (typeof TRANSLATED_VARIANTS)[number];

function isTranslated(code: string): code is TranslatedVariant {
  return (TRANSLATED_VARIANTS as readonly string[]).includes(code);
}

/**
 * Name of a variant: built-in ones are translated from their code (the raw code when a newer
 * backend sends one this build does not know), user-defined ones show the name they were given.
 */
export function variantLabel(
  t: TFunction,
  variant: { code?: string | null; name?: string | null },
): string {
  if (variant.code) {
    return isTranslated(variant.code) ? t(`variants.${variant.code}`) : variant.code;
  }
  return variant.name ?? '';
}

/** One line that says which game it is, e.g. to confirm an action on it. */
export function describeGame(t: TFunction, game: Game): string {
  return (
    game.name ??
    [t(`gameTypes.${game.gameType}`), game.variant && variantLabel(t, game.variant)]
      .filter(Boolean)
      .join(' · ')
  );
}
