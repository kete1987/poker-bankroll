import type {
  BankrollEvolution,
  BankrollSummary,
  CurrencyBankroll,
  CurrencyEvolution,
  CurrencySummary,
  MissingExchangeRate,
  StatsGroup,
  StatsGroups,
  StatsSummary,
} from '../api/types';

/**
 * The currency a screen shows its money in. What is shown in a single currency stays in it; what
 * mixes currencies is shown in the base currency, converted by the backend (amounts of different
 * currencies are never added up here).
 */
export interface MoneyView {
  currencyCode: string;
  /** The amounts come from the `converted` blocks of the responses. */
  converted: boolean;
}

/**
 * How to show the money of the currencies a screen holds: in the only one there is, or converted
 * to the base currency (`baseCurrencyCode`, the one of the converted blocks). Nothing to show
 * without currencies.
 */
export function moneyView(
  currencies: Iterable<string>,
  baseCurrencyCode: string,
): MoneyView | undefined {
  const distinct = [...new Set(currencies)];
  if (distinct.length === 0) {
    return undefined;
  }
  return distinct.length === 1
    ? { currencyCode: distinct[0]!, converted: false }
    : { currencyCode: baseCurrencyCode, converted: true };
}

/** The summary of the statistics in the currency of the view. */
export function summaryIn(summary: StatsSummary, view: MoneyView): CurrencySummary | undefined {
  return view.converted
    ? summary.converted
    : summary.currencies.find((currency) => currency.currencyCode === view.currencyCode);
}

/** The groups of the statistics in the currency of the view. */
export function groupsIn(groups: StatsGroups | undefined, view: MoneyView): StatsGroup[] {
  if (!groups) {
    return [];
  }
  return view.converted
    ? groups.converted.groups
    : (groups.currencies.find((currency) => currency.currencyCode === view.currencyCode)?.groups ??
        []);
}

/** The bankroll in the currency of the view. */
export function bankrollIn(
  bankroll: BankrollSummary,
  view: MoneyView,
): CurrencyBankroll | undefined {
  return view.converted
    ? bankroll.converted
    : bankroll.currencies.find((currency) => currency.currencyCode === view.currencyCode);
}

/**
 * The evolution of the bankroll in the currency of the view; nothing when there is nothing in the
 * range and no bankroll when it starts (as a currency without anything is left out).
 */
export function evolutionIn(
  evolution: BankrollEvolution,
  view: MoneyView,
): CurrencyEvolution | undefined {
  if (view.converted) {
    const { total } = evolution.converted;
    return total.periods.length === 0 && total.startingBankroll === 0
      ? undefined
      : evolution.converted;
  }
  return evolution.currencies.find((currency) => currency.currencyCode === view.currencyCode);
}

/**
 * What could not be converted, from several responses: per currency, from the first day to the
 * last one any of them names.
 */
export function mergeMissing(
  ...lists: (MissingExchangeRate[] | undefined)[]
): MissingExchangeRate[] {
  const byCurrency = new Map<string, MissingExchangeRate>();
  for (const missing of lists.flatMap((list) => list ?? [])) {
    const known = byCurrency.get(missing.currencyCode);
    byCurrency.set(
      missing.currencyCode,
      known
        ? {
            currencyCode: missing.currencyCode,
            from: missing.from < known.from ? missing.from : known.from,
            to: missing.to > known.to ? missing.to : known.to,
          }
        : missing,
    );
  }
  return [...byCurrency.values()].sort((a, b) => a.currencyCode.localeCompare(b.currencyCode));
}
