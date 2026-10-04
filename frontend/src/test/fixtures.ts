import type {
  BankrollEvolution,
  BankrollFigures,
  BankrollSummary,
  ConvertedBankroll,
  ConvertedEvolution,
  ConvertedGroups,
  ConvertedSummary,
  CurrencyBankroll,
  CurrencyEvolution,
  CurrencySummary,
  Game,
  GamePage,
  GameTemplate,
  Room,
  StatsFigures,
  StatsGroups,
  StatsSummary,
  Tag,
  TimePeriod,
  Variant,
} from '../api/types';

/** Sample data for the tests; every builder takes the fields that matter to the test. */
export const ROOMS: Room[] = [
  room({ id: 2, name: 'PokerStars', currencyCode: 'USD' }),
  room({ id: 3, name: 'Unibet', active: false }),
  room({ id: 1, name: 'Winamax' }),
];

export const VARIANTS: Variant[] = [
  variant({ id: 10, gameType: 'TOURNAMENT', code: 'KO' }),
  variant({ id: 11, gameType: 'TOURNAMENT', code: 'SPACE_KO', active: false }),
  variant({ id: 20, gameType: 'SIT_AND_GO', code: 'EXPRESSO' }),
  variant({ id: 21, gameType: 'SIT_AND_GO', code: null, name: 'Hyper Turbo', builtIn: false }),
];

export function room(overrides: Partial<Room>): Room {
  return {
    id: 1,
    name: 'Winamax',
    currencyCode: 'EUR',
    active: true,
    inUse: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

export function variant(overrides: Partial<Variant>): Variant {
  return {
    id: 10,
    gameType: 'TOURNAMENT',
    builtIn: true,
    active: true,
    inUse: false,
    ...overrides,
  };
}

export function game(overrides: Partial<Game>): Game {
  return {
    id: 100,
    playedOn: '2026-01-19',
    playedAt: null,
    room: { id: 1, name: 'Winamax' },
    gameType: 'TOURNAMENT',
    modality: 'NLHE',
    variant: null,
    status: 'FINISHED',
    name: null,
    currencyCode: 'EUR',
    buyIn: 5,
    entries: 1,
    prize: 0,
    bounty: 0,
    ticketPrizeValue: 0,
    ticketDescription: null,
    paidWithTicket: false,
    invested: 5,
    won: 0,
    net: -5,
    notes: null,
    tags: [],
    createdAt: '2026-01-19T20:00:00Z',
    updatedAt: '2026-01-19T20:00:00Z',
    ...overrides,
  };
}

export const TAGS: Tag[] = [
  { id: 30, name: 'Challenge', games: 12 },
  { id: 31, name: 'Friends', games: 1 },
];

export function page(items: Game[], overrides: Partial<GamePage> = {}): GamePage {
  return { items, page: 0, size: 25, totalItems: items.length, totalPages: 1, ...overrides };
}

export function template(overrides: Partial<GameTemplate>): GameTemplate {
  return {
    id: 500,
    label: null,
    room: { id: 1, name: 'Winamax' },
    gameType: 'SIT_AND_GO',
    modality: 'NLHE',
    variant: { id: 20, code: 'EXPRESSO', name: null },
    name: null,
    currencyCode: 'EUR',
    buyIn: 5,
    usable: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

const NO_STATS: StatsFigures = {
  games: 0,
  entries: 0,
  winningGames: 0,
  invested: 0,
  won: 0,
  bounties: 0,
  ticketsWon: 0,
  net: 0,
};

const NO_BANKROLL: BankrollFigures = {
  deposited: 0,
  withdrawn: 0,
  bonuses: 0,
  adjustments: 0,
  gamesNet: 0,
  result: 0,
  bankroll: 0,
  ticketsWon: 0,
  gamesInPlay: 0,
  investedInPlay: 0,
};

/**
 * The responses of the statistics and the bankroll, with their block converted to the base
 * currency: by default, the only currency there is as it is (or nothing in EUR); the test gives
 * it when it shows several currencies.
 */
export function statsSummary(
  currencies: CurrencySummary[],
  converted: Partial<ConvertedSummary> = {},
): StatsSummary {
  const only = currencies.length === 1 ? currencies[0] : undefined;
  return {
    currencies,
    converted: {
      currencyCode: only?.currencyCode ?? 'EUR',
      total: only?.total ?? NO_STATS,
      byGameType: only?.byGameType ?? [],
      inPlay: only?.inPlay ?? { games: 0, invested: 0 },
      missingRates: [],
      ...converted,
    },
  };
}

export function statsGroups(
  groupBy: StatsGroups['groupBy'],
  currencies: StatsGroups['currencies'],
  converted: Partial<ConvertedGroups> = {},
): StatsGroups {
  const only = currencies.length === 1 ? currencies[0] : undefined;
  return {
    groupBy,
    currencies,
    converted: {
      currencyCode: only?.currencyCode ?? 'EUR',
      groups: only?.groups ?? [],
      missingRates: [],
      ...converted,
    },
  };
}

export function bankrollSummary(
  currencies: CurrencyBankroll[],
  converted: Partial<ConvertedBankroll> = {},
): BankrollSummary {
  const only = currencies.length === 1 ? currencies[0] : undefined;
  return {
    currencies,
    converted: {
      currencyCode: only?.currencyCode ?? 'EUR',
      total: only?.total ?? NO_BANKROLL,
      withoutRoom: only?.withoutRoom ?? NO_BANKROLL,
      rooms: only?.rooms ?? [],
      balanceRatesOn: null,
      missingRates: [],
      ...converted,
    },
  };
}

export function bankrollEvolution(
  groupBy: TimePeriod,
  currencies: CurrencyEvolution[],
  converted: Partial<ConvertedEvolution> = {},
): BankrollEvolution {
  const only = currencies.length === 1 ? currencies[0] : undefined;
  return {
    groupBy,
    currencies,
    converted: {
      currencyCode: only?.currencyCode ?? 'EUR',
      total: only?.total ?? { startingBankroll: 0, periods: [] },
      rooms: only?.rooms ?? [],
      missingRates: [],
      ...converted,
    },
  };
}
