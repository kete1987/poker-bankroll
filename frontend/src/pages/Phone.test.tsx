import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type {
  BankrollFigures,
  BankrollSummary,
  Movement,
  StatsFigures,
  StatsSummary,
} from '../api/types';
import { game, page, ROOMS, VARIANTS } from '../test/fixtures';
import { onANarrowScreen } from '../test/narrowScreen';
import { renderApp, stubApi, type ApiCall } from '../test/renderApp';

// Canvas rendering is not available in jsdom: the chart is replaced by what it was asked to draw.
vi.mock('../components/Chart', () => ({
  Chart: ({ option }: { option: unknown }) => (
    <div data-testid="chart" data-option={JSON.stringify(option)} />
  ),
}));

function figures(overrides: Partial<StatsFigures> = {}): StatsFigures {
  return {
    games: 0,
    entries: 0,
    winningGames: 0,
    invested: 0,
    won: 0,
    bounties: 0,
    ticketsWon: 0,
    net: 0,
    ...overrides,
  };
}

function bankroll(overrides: Partial<BankrollFigures> = {}): BankrollFigures {
  return {
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
    ...overrides,
  };
}

const TOURNAMENTS = figures({
  games: 9,
  net: 30,
  invested: 45,
  won: 75,
  roi: 0.6667,
  averageBuyIn: 5,
  gamesWithPrize: 4,
  withPrizeRate: 0.4444,
  gamesInTheMoney: 3,
  inTheMoneyRate: 0.3333,
});

const SUMMARY: StatsSummary = {
  currencies: [
    {
      currencyCode: 'EUR',
      total: TOURNAMENTS,
      byGameType: [{ gameType: 'TOURNAMENT', figures: TOURNAMENTS }],
      inPlay: { games: 0, invested: 0 },
    },
  ],
};

const WINAMAX = { id: 1, name: 'Winamax' };

const BANKROLL: BankrollSummary = {
  currencies: [
    {
      currencyCode: 'EUR',
      total: bankroll({ deposited: 200, withdrawn: 50, gamesNet: 30, result: 30, bankroll: 180 }),
      withoutRoom: bankroll(),
      rooms: [
        {
          room: WINAMAX,
          active: true,
          figures: bankroll({
            deposited: 200,
            withdrawn: 50,
            gamesNet: 30,
            result: 30,
            bankroll: 180,
          }),
        },
      ],
    },
  ],
};

const MOVEMENT: Movement = {
  id: 1,
  occurredOn: '2026-01-19',
  type: 'WITHDRAWAL',
  room: WINAMAX,
  currencyCode: 'EUR',
  amount: 50,
  signedAmount: -50,
  notes: 'To the bank',
  createdAt: '2026-01-19T20:00:00Z',
  updatedAt: '2026-01-19T20:00:00Z',
};

function stubEverything(handlers: Record<string, unknown> = {}) {
  return stubApi({
    'GET /rooms': ROOMS,
    'GET /variants': VARIANTS,
    'GET /stats/summary': SUMMARY,
    'GET /stats/groups': (call: ApiCall) => ({
      groupBy: call.query.get('groupBy'),
      currencies: [
        {
          currencyCode: 'EUR',
          groups:
            call.query.get('groupBy') === 'ROOM'
              ? [
                  { key: { room: WINAMAX }, figures: TOURNAMENTS },
                  {
                    key: { room: { id: 2, name: 'PokerStars' } },
                    figures: figures({ games: 2, net: 50, invested: 10, won: 60, roi: 5 }),
                  },
                ]
              : [{ key: { period: '2026-01' }, figures: TOURNAMENTS, cumulativeNet: 30 }],
        },
      ],
    }),
    'GET /bankroll/summary': BANKROLL,
    'GET /bankroll/movements': {
      items: [MOVEMENT],
      page: 0,
      size: 25,
      totalItems: 1,
      totalPages: 1,
    },
    ...handlers,
  });
}

const FINISHED = [
  game({
    id: 1,
    name: 'Kill The Fish',
    variant: { id: 10, code: 'KO', name: null },
    buyIn: 5,
    prize: 12,
    bounty: 3,
    won: 15,
    net: 10,
  }),
  game({ id: 2, gameType: 'CASH', buyIn: 20, invested: 20, prize: 5, won: 5, net: -15 }),
];
const IN_PLAY = game({ id: 9, status: 'IN_PLAY', name: 'Sunday', buyIn: 10, invested: 10 });

function stubGames() {
  return stubEverything({
    'GET /games': (call: ApiCall) =>
      call.query.get('status') === 'IN_PLAY' ? page([IN_PLAY]) : page(FINISHED),
  });
}

function finishedQueries(calls: ApiCall[]) {
  return calls.filter((call) => call.path === '/games' && call.query.get('status') === 'FINISHED');
}

/** The card of a game or a movement: the list item that names it. */
function card(text: string | RegExp) {
  const item = screen
    .getAllByRole('listitem')
    .find((candidate) => within(candidate).queryByText(text) !== null);
  if (!item) {
    throw new Error(`No card with ${String(text)}`);
  }
  return within(item);
}

describe('On a phone', () => {
  onANarrowScreen();

  describe('games', () => {
    it('lists the finished games as cards instead of a table', async () => {
      stubGames();
      renderApp('/games');

      const fish = card(await screen.findByText('Kill The Fish').then(() => 'Kill The Fish'));
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      expect(fish.getByText('Tournament · KO')).toBeInTheDocument();
      expect(fish.getByText('Winamax')).toBeInTheDocument();
      // Every figure has its name above it: there are no column headings to read it from.
      expect(fish.getByText('Buy-in')).toBeInTheDocument();
      expect(fish.getByText('€5.00')).toBeInTheDocument();
      expect(fish.getByText('Won')).toBeInTheDocument();
      expect(fish.getByText('€15.00')).toBeInTheDocument();
      expect(fish.getByText('Net')).toBeInTheDocument();
      expect(fish.getByText('+€10.00')).toBeInTheDocument();
    });

    it('edits and deletes a game from the menu of its card', async () => {
      stubGames();
      renderApp('/games');
      await screen.findByText('Kill The Fish');

      await userEvent.click(screen.getByRole('button', { name: /^Actions for Kill The Fish/ }));
      await userEvent.click(await screen.findByRole('menuitem', { name: 'Edit', hidden: true }));
      expect(await screen.findByRole('dialog', { name: 'Edit game' })).toBeInTheDocument();
    });

    it('chooses the order above the cards, as there are no headings to click', async () => {
      const calls = stubGames();
      renderApp('/games');
      await screen.findByText('Kill The Fish');
      expect(finishedQueries(calls).at(-1)?.query.get('sort')).toBe('playedOn,desc');

      await userEvent.click(screen.getByRole('combobox', { name: 'Sort by' }));
      await userEvent.click(await screen.findByRole('option', { name: 'Net', hidden: true }));
      await waitFor(() =>
        expect(finishedQueries(calls).at(-1)?.query.get('sort')).toBe('net,desc'),
      );

      await userEvent.click(
        screen.getByRole('button', { name: 'Highest or latest first: reverse the order' }),
      );
      await waitFor(() => expect(finishedQueries(calls).at(-1)?.query.get('sort')).toBe('net,asc'));
      expect(
        screen.getByRole('button', { name: 'Lowest or earliest first: reverse the order' }),
      ).toBeInTheDocument();
    });

    it('shows the games in play as cards with their actions', async () => {
      stubGames();
      renderApp('/games');

      const inPlay = within(await screen.findByRole('region', { name: /in play/ }));
      expect(inPlay.queryByRole('table')).not.toBeInTheDocument();
      expect(inPlay.getByText('Sunday')).toBeInTheDocument();
      expect(inPlay.getByText('Invested')).toBeInTheDocument();
      expect(inPlay.getByRole('button', { name: /Add a re-entry to Sunday/ })).toBeInTheDocument();

      await userEvent.click(inPlay.getByRole('button', { name: /Finish Sunday/ }));
      expect(await screen.findByRole('dialog', { name: /Finish/ })).toBeInTheDocument();
    });

    it('folds the filters away, with the period in sight and how many are set', async () => {
      const calls = stubGames();
      renderApp('/games?type=CASH&room=1');
      await screen.findByText('Kill The Fish');

      expect(screen.getByRole('combobox', { name: 'Period' })).toBeVisible();
      const filters = screen.getByRole('button', { name: /Filters/ });
      expect(filters).toHaveAttribute('aria-expanded', 'false');
      // Two filters have a value: the type and the room.
      expect(within(filters).getByText('2')).toBeInTheDocument();

      await userEvent.click(filters);
      expect(filters).toHaveAttribute('aria-expanded', 'true');
      // The fold opens with a transition, which jsdom may still count as hidden.
      await userEvent.type(
        await screen.findByRole('textbox', { name: 'Search', hidden: true }),
        'fish',
      );

      await waitFor(() => expect(finishedQueries(calls).at(-1)?.query.get('q')).toBe('fish'));
      expect(within(filters).getByText('3')).toBeInTheDocument();
    });
  });

  describe('bankroll', () => {
    it('lists the movements as cards', async () => {
      stubEverything();
      renderApp('/bankroll');

      const withdrawal = card(await screen.findByText('To the bank').then(() => 'To the bank'));
      expect(withdrawal.getByText('Withdrawal')).toBeInTheDocument();
      expect(withdrawal.getByText('-€50.00')).toBeInTheDocument();
      expect(withdrawal.getByText('Winamax')).toBeInTheDocument();

      await userEvent.click(withdrawal.getByRole('button', { name: /^Actions for Withdrawal/ }));
      await userEvent.click(await screen.findByRole('menuitem', { name: 'Edit', hidden: true }));
      expect(await screen.findByRole('dialog', { name: 'Edit movement' })).toBeInTheDocument();
    });

    it('shows the result and the bankroll of each room, and the rest on demand', async () => {
      stubEverything();
      renderApp('/bankroll');

      const table = within(await screen.findByRole('table'));
      expect(table.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
        'Room',
        'Result',
        'Bankroll',
        '',
      ]);
      const winamax = within(table.getByRole('row', { name: /Winamax/ }));
      expect(winamax.getByText('+€30.00')).toBeInTheDocument();
      expect(winamax.getByText('+€180.00')).toBeInTheDocument();
      expect(table.queryByText('Deposited')).not.toBeInTheDocument();

      const details = winamax.getByRole('button', { name: 'Details of Winamax' });
      await userEvent.click(details);
      expect(details).toHaveAttribute('aria-expanded', 'true');
      expect(table.getByText('Deposited')).toBeInTheDocument();
      expect(table.getByText('€200.00')).toBeInTheDocument();
      expect(table.getByText('Withdrawn')).toBeInTheDocument();

      await userEvent.click(details);
      expect(table.queryByText('Deposited')).not.toBeInTheDocument();
    });
  });

  describe('figures', () => {
    it('keeps played, net and ROI of the results of the dashboard, and unfolds the rest', async () => {
      stubEverything();
      renderApp('/');

      const results = within((await screen.findAllByRole('table'))[0]!);
      expect(results.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
        '',
        'Played',
        'Net',
        'ROI',
        '',
      ]);
      const tournaments = within(results.getByRole('row', { name: /^Tournament/ }));
      expect(
        tournaments
          .getAllByRole('cell')
          .slice(0, 3)
          .map((cell) => cell.textContent),
      ).toEqual(['9', '+€30.00', '66.67%']);

      await userEvent.click(tournaments.getByRole('button', { name: 'Details of Tournament' }));
      expect(results.getByText('Invested')).toBeInTheDocument();
      expect(results.getByText('€45.00')).toBeInTheDocument();
      expect(results.getByText('% ITM')).toBeInTheDocument();
      expect(results.getByText('Avg. buy-in')).toBeInTheDocument();
      // The total unfolds on its own.
      expect(results.getAllByText('Invested')).toHaveLength(1);
    });

    it('does the same with the results of each period', async () => {
      stubEverything();
      renderApp('/stats?period=all');

      const table = within(await screen.findByRole('table'));
      expect(table.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
        'Month',
        'Played',
        'Net',
        'ROI',
        '',
      ]);
      await userEvent.click(table.getByRole('button', { name: 'Details of January 2026' }));
      // The net of each game type is part of what unfolds.
      expect(table.getByText('Tournament')).toBeInTheDocument();
      expect(table.getByText('Invested')).toBeInTheDocument();
    });

    it('still sorts a breakdown by the columns that are left', async () => {
      stubEverything();
      renderApp('/stats?period=all&view=breakdown');

      const table = within(await screen.findByRole('table'));
      const labels = () =>
        table.getAllByRole('rowheader').map((cell) => {
          const label = cell.cloneNode(true) as HTMLElement;
          label.querySelector('.mantine-Avatar-root')?.remove();
          return label.textContent;
        });
      expect(labels()).toEqual(['Winamax', 'PokerStars']);

      await userEvent.click(table.getByRole('button', { name: 'Net' }));
      expect(labels()).toEqual(['PokerStars', 'Winamax']);

      await userEvent.click(table.getByRole('button', { name: 'Details of Winamax' }));
      expect(table.getByText('Avg. buy-in')).toBeInTheDocument();
    });

    it('folds the filters of the statistics away too', async () => {
      stubEverything();
      renderApp('/stats?period=all&type=TOURNAMENT');

      await screen.findByRole('table');
      const filters = screen.getByRole('button', { name: /Filters/ });
      expect(within(filters).getByText('1')).toBeInTheDocument();
      expect(screen.getByRole('combobox', { name: 'Period' })).toBeVisible();
    });
  });
});
