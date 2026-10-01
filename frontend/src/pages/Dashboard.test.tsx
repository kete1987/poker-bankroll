import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type {
  BankrollFigures,
  BankrollSummary,
  StatsFigures,
  StatsGroups,
  StatsSummary,
} from '../api/types';
import { rangeOf } from '../components/period';
import { room } from '../test/fixtures';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

function figures(overrides: Partial<StatsFigures> = {}): StatsFigures {
  return {
    games: 0,
    entries: 0,
    gamesWithPrize: 0,
    withPrizeRate: null,
    gamesInTheMoney: 0,
    inTheMoneyRate: null,
    winningGames: 0,
    winningRate: null,
    averageBuyIn: null,
    invested: 0,
    won: 0,
    bounties: 0,
    ticketsWon: 0,
    net: 0,
    roi: null,
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
  games: 40,
  entries: 44,
  gamesWithPrize: 12,
  withPrizeRate: 0.3,
  gamesInTheMoney: 8,
  inTheMoneyRate: 0.2,
  averageBuyIn: 5.25,
  invested: 230,
  won: 300,
  net: 70,
  roi: 0.3043,
  ticketsWon: 20,
});
const CASH = figures({
  games: 3,
  entries: 3,
  gamesWithPrize: null,
  gamesInTheMoney: null,
  invested: 30,
  won: 25,
  net: -5,
  roi: -0.1667,
});
const TOTAL = figures({
  games: 43,
  entries: 47,
  gamesWithPrize: 12,
  withPrizeRate: 0.3,
  gamesInTheMoney: 8,
  inTheMoneyRate: 0.2,
  averageBuyIn: 5.25,
  invested: 260,
  won: 325,
  net: 65,
  roi: 0.25,
  ticketsWon: 20,
});

const STATS: StatsSummary = {
  currencies: [
    {
      currencyCode: 'EUR',
      total: TOTAL,
      byGameType: [
        { gameType: 'TOURNAMENT', figures: TOURNAMENTS },
        { gameType: 'CASH', figures: CASH },
      ],
      inPlay: { games: 0, invested: 0 },
    },
    {
      currencyCode: 'USD',
      total: figures({ games: 2, invested: 20, won: 10, net: -10, roi: -0.5 }),
      byGameType: [
        {
          gameType: 'TOURNAMENT',
          figures: figures({ games: 2, invested: 20, won: 10, net: -10, roi: -0.5 }),
        },
      ],
      inPlay: { games: 0, invested: 0 },
    },
  ],
};

const BY_VARIANT: StatsGroups = {
  groupBy: 'VARIANT',
  currencies: [
    {
      currencyCode: 'EUR',
      groups: [
        {
          key: { gameType: 'TOURNAMENT', variant: { id: 10, code: 'KO', name: null } },
          figures: figures({ games: 25, invested: 150, won: 220, net: 70, roi: 0.4667 }),
        },
        {
          key: { gameType: 'TOURNAMENT', variant: null },
          figures: figures({ games: 15, invested: 80, won: 80, net: 0, roi: 0 }),
        },
        {
          key: { gameType: 'SIT_AND_GO', variant: { id: 21, code: null, name: 'Hyper Turbo' } },
          figures: figures({ games: 3, invested: 30, won: 25, net: -5 }),
        },
      ],
    },
  ],
};

const WINAMAX = { id: 1, name: 'Winamax' };
const UNIBET = { id: 3, name: 'Unibet' };

/** The bankroll as it is now. */
const BANKROLL_NOW: BankrollSummary = {
  currencies: [
    {
      currencyCode: 'EUR',
      total: bankroll({
        deposited: 200,
        withdrawn: 50,
        bankroll: 320.5,
        result: 120.5,
        gamesInPlay: 2,
        investedInPlay: 12,
      }),
      withoutRoom: bankroll({ deposited: 200, bankroll: 200 }),
      rooms: [
        { room: UNIBET, active: false, figures: bankroll({ result: -30, bankroll: -30 }) },
        { room: WINAMAX, active: true, figures: bankroll({ result: 150.5, bankroll: 150.5 }) },
      ],
    },
    {
      currencyCode: 'USD',
      total: bankroll({ bankroll: 40 }),
      withoutRoom: bankroll(),
      rooms: [],
    },
  ],
};
/** Net of the finished games of the period per room. */
const BY_ROOM: StatsGroups = {
  groupBy: 'ROOM',
  currencies: [
    {
      currencyCode: 'EUR',
      groups: [{ key: { room: WINAMAX }, figures: figures({ games: 43, net: 72 }) }],
    },
  ],
};

function stubDashboard(handlers: Record<string, unknown> = {}) {
  return stubApi({
    'GET /rooms': [
      room({ id: 1, name: 'Winamax' }),
      room({ id: 3, name: 'Unibet', active: false }),
    ],
    'GET /stats/summary': STATS,
    'GET /stats/groups': (call: ApiCall) =>
      call.query.get('groupBy') === 'VARIANT' ? BY_VARIANT : BY_ROOM,
    'GET /bankroll/summary': BANKROLL_NOW,
    ...handlers,
  });
}

function queriesTo(calls: ApiCall[], path: string) {
  return calls
    .filter((call) => call.path === path)
    .map((call) => {
      const query: Record<string, string> = {};
      for (const key of new Set(call.query.keys())) {
        query[key] = call.query.getAll(key).join(',');
      }
      return query;
    });
}

function card(name: string) {
  return within(screen.getByRole('region', { name }));
}

describe('Dashboard', () => {
  it('shows the results of this year by default, in the currency with most games', async () => {
    const calls = stubDashboard();
    renderApp('/');

    expect(await screen.findByRole('region', { name: 'Net' })).toBeInTheDocument();
    const thisYear = rangeOf('thisYear');
    expect(queriesTo(calls, '/stats/summary').at(-1)).toEqual({
      from: thisYear.from,
      to: thisYear.to,
    });
    expect(screen.getByRole('combobox', { name: 'Period' })).toHaveValue('This year');
    expect(screen.getByRole('combobox', { name: 'Currency' })).toHaveValue('EUR');

    expect(card('Net').getByText('+€65.00')).toBeInTheDocument();
    expect(card('Net').getByText('€325.00 won · €260.00 invested')).toBeInTheDocument();
    expect(card('ROI').getByText('25.00%')).toBeInTheDocument();
    expect(card('ROI').getByText('43 games')).toBeInTheDocument();
    // Only tournaments count for the ITM card.
    expect(card('ITM in tournaments').getByText('20.00%')).toBeInTheDocument();
    expect(card('ITM in tournaments').getByText('8 of 40 tournaments')).toBeInTheDocument();
  });

  it('shows the bankroll as it is now, whatever the period', async () => {
    const calls = stubDashboard();
    renderApp('/');
    await screen.findByRole('region', { name: 'Bankroll' });

    expect(card('Bankroll').getByText('€320.50')).toBeInTheDocument();
    // Just the total: the detail belongs to the bankroll screen.
    expect(card('Bankroll').queryByText(/deposited|withdrawn/)).not.toBeInTheDocument();
    // The bankroll is never asked for with the dates of the period.
    expect(queriesTo(calls, '/bankroll/summary')).toEqual([{}]);
  });

  it('breaks the results down per game type, with a total', async () => {
    stubDashboard();
    renderApp('/');

    const table = within((await screen.findAllByRole('table'))[0]!);
    const rows = table.getAllByRole('row');
    expect(
      rows.map((row) => within(row).queryAllByRole('rowheader')[0]?.textContent ?? ''),
    ).toEqual(['', 'Tournament', 'Cash', 'Total']);
    expect(rows[1]).toHaveTextContent(
      [
        '40',
        '12',
        '30.00%',
        '€5.25',
        '8',
        '20.00%',
        '€230.00',
        '€300.00',
        '+€70.00',
        '30.43%',
      ].join(''),
    );
    // What does not apply to cash games is a dash.
    expect(rows[2]).toHaveTextContent(['3', '—', '—', '—', '—', '—', '€30.00', '€25.00'].join(''));
    expect(rows[2]).toHaveTextContent('-€5.00');
    expect(rows[3]).toHaveTextContent('+€65.00');
    expect(screen.getByText('Tickets won: €20.00 (not counted as money).')).toBeInTheDocument();
  });

  it('breaks the results down per variant when asked', async () => {
    const calls = stubDashboard();
    renderApp('/');
    await screen.findAllByRole('table');
    const variantQueries = () =>
      queriesTo(calls, '/stats/groups').filter((query) => query.groupBy === 'VARIANT');
    expect(variantQueries()).toHaveLength(0);

    await userEvent.click(screen.getByRole('radio', { name: 'By variant' }));

    await waitFor(() =>
      expect(
        within(screen.getAllByRole('table')[0]!)
          .getAllByRole('rowheader')
          .map((header) => header.textContent),
      ).toEqual([
        'Tournament · KO',
        'Tournament · No variant',
        'Sit & Go / Spin · Hyper Turbo',
        'Total',
      ]),
    );
    const thisYear = rangeOf('thisYear');
    expect(variantQueries().at(-1)).toEqual({
      groupBy: 'VARIANT',
      from: thisYear.from,
      to: thisYear.to,
    });
  });

  it('lists each room with the net of the period and its bankroll now', async () => {
    stubDashboard();
    renderApp('/');

    const table = within((await screen.findAllByRole('table'))[1]!);
    const rows = table.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(4);
    expect(rows[0]).toHaveTextContent('Unibet');
    expect(rows[0]).toHaveTextContent('€0.00-€30.00');
    expect(rows[1]).toHaveTextContent('Winamax');
    expect(rows[1]).toHaveTextContent('+€72.00+€150.50');
    // Movements that belong to no room have their own row; they have no games.
    expect(rows[2]).toHaveTextContent('No room');
    expect(rows[2]).toHaveTextContent('—+€200.00');
    expect(rows[3]).toHaveTextContent('Total');
    // The same net as the card and the results table.
    expect(rows[3]).toHaveTextContent('+€65.00+€320.50');
  });

  it('reminds of the games in play', async () => {
    stubDashboard();
    renderApp('/');

    expect(await screen.findByText('2 games in play, €12.00 invested.')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Go to games' })[0]).toHaveAttribute(
      'href',
      '/games',
    );
  });

  it('changes the period and filters by rooms, keeping them in the URL', async () => {
    const calls = stubDashboard();
    renderApp('/');
    await screen.findByRole('region', { name: 'Net' });

    await userEvent.click(screen.getByRole('combobox', { name: 'Period' }));
    await userEvent.click(await screen.findByRole('option', { name: 'All time', hidden: true }));
    await waitFor(() => expect(queriesTo(calls, '/stats/summary').at(-1)).toEqual({}));

    await userEvent.click(screen.getByRole('combobox', { name: 'Room' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Winamax', hidden: true }));
    await userEvent.click(await screen.findByRole('option', { name: 'Unibet', hidden: true }));

    await waitFor(() =>
      expect(queriesTo(calls, '/stats/summary').at(-1)).toEqual({ roomId: '1,3' }),
    );
    expect(queriesTo(calls, '/bankroll/summary').at(-1)).toEqual({ roomId: '1,3' });
  });

  it('takes period, rooms, currency and breakdown from the URL', async () => {
    const calls = stubDashboard();
    renderApp('/?from=2026-01-01&to=2026-01-31&room=1&currency=usd&by=variant');

    expect(await screen.findByRole('region', { name: 'Net' })).toBeInTheDocument();

    expect(queriesTo(calls, '/stats/summary').at(-1)).toEqual({
      from: '2026-01-01',
      to: '2026-01-31',
      roomId: '1',
    });
    expect(screen.getByRole('combobox', { name: 'Period' })).toHaveValue('Custom');
    expect(screen.getByRole('combobox', { name: 'Currency' })).toHaveValue('USD');
    expect(screen.getByRole('radio', { name: 'By variant' })).toBeChecked();
    expect(card('Net').getByText('-US$10.00')).toBeInTheDocument();
    expect(card('Bankroll').getByText('US$40.00')).toBeInTheDocument();
  });

  it('switches currency', async () => {
    stubDashboard();
    renderApp('/');
    await screen.findByRole('region', { name: 'Net' });

    await userEvent.click(screen.getByRole('combobox', { name: 'Currency' }));
    await userEvent.click(await screen.findByRole('option', { name: 'USD', hidden: true }));

    await waitFor(() => expect(card('Net').getByText('-US$10.00')).toBeInTheDocument());
    expect(card('ROI').getByText('-50.00%')).toBeInTheDocument();
  });

  it('has no currency selector with a single currency, and says so when the period has no games', async () => {
    stubDashboard({
      'GET /stats/summary': { currencies: [] },
      'GET /bankroll/summary': {
        currencies: [BANKROLL_NOW.currencies[0]],
      },
    });
    renderApp('/');

    expect(await screen.findByText('No finished games in this period.')).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Currency' })).not.toBeInTheDocument();
    expect(card('Net').getByText('€0.00')).toBeInTheDocument();
    expect(card('ROI').getByText('—')).toBeInTheDocument();
    expect(card('Bankroll').getByText('€320.50')).toBeInTheDocument();
  });

  it('invites to record games when there is nothing at all', async () => {
    stubApi({});
    renderApp('/');

    expect(await screen.findByText('There is nothing to show yet.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to games' })).toHaveAttribute('href', '/games');
  });

  it.each(['/stats/summary', '/bankroll/summary', '/stats/groups', '/rooms'])(
    'says so when %s cannot be loaded',
    async (path) => {
      stubDashboard({ [`GET ${path}`]: () => problem(500, 'INTERNAL_ERROR', 'Boom') });
      renderApp('/');

      expect(
        await screen.findByText(
          'The data could not be loaded. Check that the API is available and reload the page.',
        ),
      ).toBeInTheDocument();
    },
  );
});
