import type { Game, GamePage, Room, Variant } from '../api/types';

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
    createdAt: '2026-01-19T20:00:00Z',
    updatedAt: '2026-01-19T20:00:00Z',
    ...overrides,
  };
}

export function page(items: Game[], overrides: Partial<GamePage> = {}): GamePage {
  return { items, page: 0, size: 25, totalItems: items.length, totalPages: 1, ...overrides };
}
