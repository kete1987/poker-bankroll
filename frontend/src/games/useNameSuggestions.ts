import type { UseFormReturnType } from '@mantine/form';
import { useDebouncedValue } from '@mantine/hooks';
import { useRef } from 'react';

import { MIN_NAME_SEARCH_LENGTH, useGameNames } from '../api/games';
import type { GameType, Modality } from '../api/types';
import type { Amount } from './amount';

/** How long typing must pause before names are asked for. */
const NAME_SEARCH_DELAY_MS = 250;

/** The fields that picking a suggested name fills in, unless the user has set them. */
export type FilledByName = 'buyIn' | 'variantId' | 'modality';

/** What a form must hold to have its name suggested and filled from the games recorded. */
export interface NameFields {
  name: string;
  buyIn: Amount;
  variantId: string | null;
  modality: Modality;
}

interface NameSuggestionOptions {
  gameType: GameType;
  /** Currency of the chosen room, if any. */
  currency: string | undefined;
  /** The variants the form offers: an inactive one is never filled in. */
  variantOptions: { value: string }[];
  /** The game being edited: its name is not searched, and picking a name fills nothing. */
  editing?: { name?: string | null };
  /**
   * The form starts with these fields already chosen (a copy of a game): a suggested name does
   * not replace them, and the buy-in, in `buyInCurrency`, is emptied if the room changes to
   * another currency.
   */
  chosen?: { buyInCurrency: string };
}

/**
 * Names of recorded games suggested while one is typed, for the type of the form. Picking one
 * fills the buy-in, variant and modality of its last game, except what the user has set by hand
 * (the fields given their props with `filledByName`). The buy-in is only taken in the currency of
 * the chosen room, and is emptied again if the room then changes to another currency.
 */
export function useNameSuggestions<V extends NameFields>(
  form: UseFormReturnType<V>,
  { gameType, currency, variantOptions, editing, chosen }: NameSuggestionOptions,
) {
  // What the user has set by hand in this form: a suggested name never replaces it.
  const setByHand = useRef(new Set<FilledByName>(chosen ? ['buyIn', 'variantId', 'modality'] : []));
  // Currency of a buy-in that did not come from the user, while it is still that one.
  const suggestedBuyInCurrency = useRef<string | null>(chosen?.buyInCurrency ?? null);

  // Names are suggested from the second character, once typing pauses; none for the name a game
  // being edited already has.
  const [nameSearch] = useDebouncedValue(form.values.name.trim(), NAME_SEARCH_DELAY_MS);
  const searchesName =
    nameSearch.length >= MIN_NAME_SEARCH_LENGTH && nameSearch !== (editing?.name ?? '');
  const names = useGameNames(searchesName ? nameSearch : '', gameType);
  // Those of the previous search stay while the next one loads, but not after the type changes.
  const suggestions = searchesName
    ? (names.data ?? []).filter((suggestion) => suggestion.gameType === gameType)
    : [];

  function setValues(values: Partial<NameFields>) {
    form.setValues(values as Partial<V>);
  }

  /** Input props of a field that a suggested name can fill in, noting when the user sets it. */
  function filledByName(field: FilledByName) {
    const props = form.getInputProps(field);
    return {
      ...props,
      onChange: (value: unknown) => {
        setByHand.current.add(field);
        if (field === 'buyIn') {
          suggestedBuyInCurrency.current = null;
        }
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
    if (!suggestion || editing) {
      return;
    }
    const filled: Partial<NameFields> = {};
    if (!setByHand.current.has('buyIn')) {
      if (suggestion.currencyCode === currency) {
        filled.buyIn = suggestion.buyIn;
        suggestedBuyInCurrency.current = suggestion.currencyCode;
        form.clearFieldError('buyIn');
      } else if (suggestedBuyInCurrency.current !== null) {
        // The buy-in of the name picked before does not belong to this one.
        filled.buyIn = '';
        suggestedBuyInCurrency.current = null;
      }
    }
    if (!setByHand.current.has('modality')) {
      filled.modality = suggestion.modality;
    }
    if (!setByHand.current.has('variantId')) {
      const variantId = suggestion.variant ? String(suggestion.variant.id) : null;
      // A variant that is no longer offered (inactive) is not chosen: the game is left without
      // one, not with the variant of a name picked before.
      const offered = variantOptions.some((option) => option.value === variantId);
      filled.variantId = offered ? variantId : null;
    }
    setValues(filled);
  }

  /**
   * The room changed: a buy-in that the user did not type is an amount of another currency in
   * a room of another currency, so it is emptied (and a name may fill it again).
   */
  function roomChanged(currencyCode: string | undefined) {
    const suggested = suggestedBuyInCurrency.current;
    if (suggested !== null && currencyCode !== undefined && currencyCode !== suggested) {
      suggestedBuyInCurrency.current = null;
      setByHand.current.delete('buyIn');
      setValues({ buyIn: '' });
    }
  }

  /** The user no longer has this field set: it changed for another reason (the type changed). */
  function forget(field: FilledByName) {
    setByHand.current.delete(field);
  }

  /** The form starts again from what it holds: a suggested name may replace any of it. */
  function reset() {
    setByHand.current.clear();
    suggestedBuyInCurrency.current = null;
  }

  /**
   * The fields were all filled at once from something chosen (a template), as `chosen` does when
   * the form starts: a suggested name does not replace them, and the buy-in, in `buyInCurrency`,
   * is emptied if the room changes to another currency.
   */
  function choose(buyInCurrency: string) {
    setByHand.current = new Set<FilledByName>(['buyIn', 'variantId', 'modality']);
    suggestedBuyInCurrency.current = buyInCurrency;
  }

  return { suggestions, filledByName, fillFromName, roomChanged, forget, reset, choose };
}
