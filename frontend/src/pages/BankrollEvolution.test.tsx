import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type {
  BankrollEvolution,
  BankrollFigures,
  BankrollSummary,
  CurrencyEvolution,
  EvolutionPeriod,
  TimePeriod,
} from '../api/types';
import { rangeOf } from '../components/period';
import { bankrollEvolution, bankrollSummary, room } from '../test/fixtures';
import { onANarrowScreen } from '../test/narrowScreen';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

// Canvas rendering is not available in jsdom: the chart is replaced by what it was asked to draw.
vi.mock('../components/Chart', () => ({
  Chart: ({ option }: { option: unknown }) => (
    <div data-testid="chart" data-option={JSON.stringify(option)} />
  ),
}));

function figures(overrides: Partial<BankrollFigures> = {}): BankrollFigures {
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

const SUMMARY: BankrollSummary = bankrollSummary([
  { currencyCode: 'EUR', total: figures({ bankroll: 130 }), withoutRoom: figures(), rooms: [] },
]);

function period(overrides: Partial<EvolutionPeriod> & { period: string }): EvolutionPeriod {
  return {
    startsOn: overrides.period,
    endsOn: overrides.period,
    deposited: 0,
    withdrawn: 0,
    bonuses: 0,
    adjustments: 0,
    gamesNet: 0,
    bankroll: 0,
    ...overrides,
  };
}

const EUR: CurrencyEvolution = {
  currencyCode: 'EUR',
  total: {
    startingBankroll: 50,
    periods: [
      period({ period: '2026-01-05', deposited: 100, gamesNet: -10, bankroll: 140 }),
      period({ period: '2026-01-12', gamesNet: 20, bankroll: 160 }),
      period({ period: '2026-01-20', withdrawn: 30, bankroll: 130 }),
    ],
  },
  rooms: [
    {
      room: { id: 3, name: 'Unibet' },
      active: true,
      series: {
        startingBankroll: 0,
        periods: [period({ period: '2026-01-12', gamesNet: 20, bankroll: 20 })],
      },
    },
    {
      room: { id: 1, name: 'Winamax' },
      active: true,
      series: {
        startingBankroll: 50,
        periods: [
          period({ period: '2026-01-05', deposited: 100, gamesNet: -10, bankroll: 140 }),
          period({ period: '2026-01-20', withdrawn: 30, bankroll: 110 }),
        ],
      },
    },
  ],
};

const USD: CurrencyEvolution = {
  currencyCode: 'USD',
  total: {
    startingBankroll: 0,
    periods: [period({ period: '2026-01-07', deposited: 40, bankroll: 40 })],
  },
  rooms: [
    {
      room: { id: 2, name: 'PokerStars' },
      active: true,
      series: {
        startingBankroll: 0,
        periods: [period({ period: '2026-01-07', deposited: 40, bankroll: 40 })],
      },
    },
  ],
};

/** Months of activity, from November 2025 to January 2026: three months, drawn by days. */
const BY_MONTH: CurrencyEvolution = {
  currencyCode: 'EUR',
  total: {
    startingBankroll: 0,
    periods: [
      period({ period: '2025-11', startsOn: '2025-11-01', endsOn: '2025-11-30', bankroll: 10 }),
      period({ period: '2026-01', startsOn: '2026-01-01', endsOn: '2026-01-31', bankroll: 30 }),
    ],
  },
  rooms: [],
};

function evolution(call: ApiCall, byMonth: CurrencyEvolution = BY_MONTH): BankrollEvolution {
  const groupBy = call.query.get('groupBy') as TimePeriod;
  return bankrollEvolution(groupBy, groupBy === 'MONTH' ? [byMonth] : [EUR]);
}

function stubEvolution(handlers: Record<string, unknown> = {}) {
  return stubApi({
    'GET /rooms': [room({ id: 1, name: 'Winamax' }), room({ id: 3, name: 'Unibet' })],
    'GET /bankroll/summary': SUMMARY,
    'GET /bankroll/evolution': (call: ApiCall) => evolution(call),
    ...handlers,
  });
}

function evolutionQueries(calls: ApiCall[]) {
  return calls
    .filter((call) => call.path === '/bankroll/evolution')
    .map((call) => {
      const query: Record<string, string> = {};
      for (const key of new Set(call.query.keys())) {
        query[key] = call.query.getAll(key).join(',');
      }
      return query;
    });
}

interface Series {
  id: string;
  name: string;
  type: string;
  data: [number, number][];
  lineStyle?: { width: number };
  symbol?: string;
  symbolRotate?: number;
  z: number;
}

interface Option {
  xAxis: { type: string; splitNumber: number };
  series: Series[];
  legend: { type: string };
}

async function drawn(): Promise<Option> {
  const chart = await screen.findByTestId('chart');
  return JSON.parse(chart.getAttribute('data-option') ?? '{}') as Option;
}

const at = (date: string) => Date.parse(date);

describe('Bankroll evolution', () => {
  it('draws a line per room and the total on top, with deposits and withdrawals marked', async () => {
    const calls = stubEvolution();
    renderApp('/bankroll?from=2026-01-01&to=2026-01-31&room=1,3');

    expect(await screen.findByRole('heading', { name: 'Evolution' })).toBeInTheDocument();
    const option = await drawn();
    expect(evolutionQueries(calls).at(-1)).toEqual({
      groupBy: 'DAY',
      from: '2026-01-01',
      to: '2026-01-31',
      roomId: '1,3',
    });
    expect(option.xAxis.type).toBe('time');
    expect(option.legend.type).toBe('scroll');
    expect(option.series.map((series) => [series.id, series.name, series.type])).toEqual([
      ['room-3', 'Unibet', 'line'],
      ['room-1', 'Winamax', 'line'],
      ['total', 'Total', 'line'],
      ['deposits', 'Deposits', 'scatter'],
      ['withdrawals', 'Withdrawals', 'scatter'],
    ]);
    const [unibet, winamax, total, deposits, withdrawals] = option.series as [
      Series,
      Series,
      Series,
      Series,
      Series,
    ];
    // The total starts from the bankroll there was, and goes on to the end of the period.
    expect(total.data).toEqual([
      [at('2026-01-01'), 50],
      [at('2026-01-05'), 140],
      [at('2026-01-12'), 160],
      [at('2026-01-20'), 130],
      [at('2026-01-31'), 130],
    ]);
    expect(total.lineStyle?.width).toBeGreaterThan(winamax.lineStyle?.width ?? 0);
    expect(total.z).toBeGreaterThan(winamax.z);
    expect(unibet.data.map(([, value]) => value)).toEqual([0, 0, 20, 20, 20]);
    expect(winamax.data.map(([, value]) => value)).toEqual([50, 140, 140, 110, 110]);
    // On the total line, where they happened.
    expect(deposits.symbol).toBe('triangle');
    expect(deposits.data).toEqual([[at('2026-01-05'), 140]]);
    expect(withdrawals.symbolRotate).toBe(180);
    expect(withdrawals.data).toEqual([[at('2026-01-20'), 130]]);
  });

  it('draws everything converted to the base currency when currencies are mixed', async () => {
    // 40 USD are 36 EUR on the 7th and 32 EUR at the end of the month.
    const converted = {
      currencyCode: 'EUR',
      total: {
        startingBankroll: 50,
        periods: [
          period({ period: '2026-01-05', deposited: 100, gamesNet: -10, bankroll: 140 }),
          period({ period: '2026-01-07', deposited: 36, bankroll: 176 }),
          period({ period: '2026-01-12', gamesNet: 20, bankroll: 196 }),
          period({ period: '2026-01-20', withdrawn: 30, bankroll: 162 }),
        ],
      },
      rooms: [
        {
          room: { id: 2, name: 'PokerStars' },
          active: true,
          series: {
            startingBankroll: 0,
            periods: [
              period({ period: '2026-01-05', bankroll: 0 }),
              period({ period: '2026-01-07', deposited: 36, bankroll: 36 }),
              period({ period: '2026-01-12', bankroll: 34 }),
              period({ period: '2026-01-20', bankroll: 32 }),
            ],
          },
        },
      ],
      missingRates: [{ currencyCode: 'GBP', from: '2026-01-02', to: '2026-01-02' }],
    };
    stubEvolution({
      'GET /bankroll/summary': bankrollSummary(
        [
          SUMMARY.currencies[0]!,
          {
            currencyCode: 'USD',
            total: figures({ bankroll: 40 }),
            withoutRoom: figures(),
            rooms: [],
          },
        ],
        { currencyCode: 'EUR', total: figures({ bankroll: 162 }) },
      ),
      'GET /bankroll/evolution': (call: ApiCall) =>
        bankrollEvolution(call.query.get('groupBy') as TimePeriod, [EUR, USD], converted),
    });
    renderApp('/bankroll?from=2026-01-01&to=2026-01-31');

    const option = await drawn();
    expect(option.series.find((series) => series.id === 'total')?.data).toEqual([
      [at('2026-01-01'), 50],
      [at('2026-01-05'), 140],
      [at('2026-01-07'), 176],
      [at('2026-01-12'), 196],
      [at('2026-01-20'), 162],
      [at('2026-01-31'), 162],
    ]);
    expect(option.series.find((series) => series.id === 'room-2')?.data).toEqual([
      [at('2026-01-01'), 0],
      [at('2026-01-05'), 0],
      [at('2026-01-07'), 36],
      [at('2026-01-12'), 34],
      [at('2026-01-20'), 32],
      [at('2026-01-31'), 32],
    ]);
    // What the chart cannot convert is said next to it.
    expect(screen.getByRole('alert')).toHaveTextContent('GBP on 02/01/2026');
  });

  it('cuts the time by the length of the period', async () => {
    let calls = stubEvolution();
    const year = renderApp('/bankroll?from=2026-01-01&to=2026-12-31');
    await drawn();
    expect(evolutionQueries(calls).map((query) => query.groupBy)).toEqual(['WEEK']);
    expect(screen.getByRole('combobox', { name: 'Group by' })).toHaveValue('Automatic (weeks)');
    year.unmount();

    calls = stubEvolution();
    const years = renderApp('/bankroll?from=2023-01-01&to=2026-12-31');
    await screen.findByRole('heading', { name: 'Evolution' });
    await waitFor(() =>
      expect(evolutionQueries(calls).map((query) => query.groupBy)).toEqual(['MONTH']),
    );
    years.unmount();
  });

  it('for all time, cuts the time by when there was activity', async () => {
    let calls = stubEvolution();
    const short = renderApp('/bankroll');
    await drawn();
    // Three months of activity: by days, once the months say so.
    await waitFor(() =>
      expect(evolutionQueries(calls).map((query) => query.groupBy)).toEqual(['MONTH', 'DAY']),
    );
    expect(evolutionQueries(calls)[1]).toEqual({ groupBy: 'DAY' });
    expect(screen.getByRole('combobox', { name: 'Group by' })).toHaveValue('Automatic (days)');
    short.unmount();

    const years: CurrencyEvolution = {
      ...BY_MONTH,
      total: {
        startingBankroll: 0,
        periods: [
          period({ period: '2023-03', startsOn: '2023-03-01', endsOn: '2023-03-31' }),
          period({ period: '2026-01', startsOn: '2026-01-01', endsOn: '2026-01-31' }),
        ],
      },
    };
    calls = stubEvolution({ 'GET /bankroll/evolution': (call: ApiCall) => evolution(call, years) });
    renderApp('/bankroll');
    await drawn();
    // Years of activity: the months are what is drawn.
    expect(evolutionQueries(calls).map((query) => query.groupBy)).toEqual(['MONTH']);
    expect(screen.getByRole('combobox', { name: 'Group by' })).toHaveValue('Automatic (months)');
  });

  it('draws the cut chosen after the months for all time failed', async () => {
    const calls = stubEvolution({
      'GET /bankroll/evolution': (call: ApiCall) =>
        call.query.get('groupBy') === 'MONTH'
          ? problem(500, 'INTERNAL_ERROR', 'Boom')
          : evolution(call),
    });
    renderApp('/bankroll');
    expect(await screen.findByText(/could not be loaded/i)).toBeInTheDocument();

    const select = screen.getByRole('combobox', { name: 'Group by' });
    await userEvent.click(select);
    const list = document.getElementById(select.getAttribute('aria-controls') ?? '')!;
    await userEvent.click(within(list).getByRole('option', { name: 'Days', hidden: true }));
    await drawn();
    expect(evolutionQueries(calls).at(-1)).toEqual({ groupBy: 'DAY' });
    expect(screen.queryByText(/could not be loaded/i)).not.toBeInTheDocument();
  });

  it('cuts the time as chosen, kept in the URL, without changing the page of movements', async () => {
    const calls = stubEvolution({
      'GET /bankroll/movements': {
        items: [
          {
            id: 1,
            occurredOn: '2026-01-05',
            type: 'DEPOSIT',
            room: { id: 1, name: 'Winamax' },
            currencyCode: 'EUR',
            amount: 100,
            signedAmount: 100,
            notes: null,
            createdAt: '2026-01-05T20:00:00Z',
            updatedAt: '2026-01-05T20:00:00Z',
          },
        ],
        page: 1,
        size: 25,
        totalItems: 60,
        totalPages: 3,
      },
    });
    renderApp('/bankroll?from=2026-01-01&to=2026-01-31&group=week&page=2');
    await drawn();
    expect(evolutionQueries(calls).at(-1)).toMatchObject({ groupBy: 'WEEK' });
    const select = screen.getByRole('combobox', { name: 'Group by' });
    expect(select).toHaveValue('Weeks');

    await userEvent.click(select);
    const list = document.getElementById(select.getAttribute('aria-controls') ?? '')!;
    await userEvent.click(within(list).getByRole('option', { name: 'Years', hidden: true }));
    await waitFor(() => expect(evolutionQueries(calls).at(-1)).toMatchObject({ groupBy: 'YEAR' }));
    expect(select).toHaveValue('Years');
    const movements = calls.filter((call) => call.path === '/bankroll/movements');
    expect(movements.at(-1)?.query.get('page')).toBe('1');

    // Back to automatic: the length of the period decides again.
    await userEvent.click(select);
    await userEvent.click(within(list).getByRole('option', { name: /^Automatic/, hidden: true }));
    await waitFor(() => expect(evolutionQueries(calls).at(-1)).toMatchObject({ groupBy: 'DAY' }));
  });

  it('ignores a cut it does not know in the URL', async () => {
    const calls = stubEvolution();
    renderApp('/bankroll?from=2026-01-01&to=2026-01-31&group=hour');
    await drawn();
    expect(evolutionQueries(calls).map((query) => query.groupBy)).toEqual(['DAY']);
    expect(screen.getByRole('combobox', { name: 'Group by' })).toHaveValue('Automatic (days)');
  });

  it('says so when there is nothing to draw', async () => {
    stubEvolution({
      'GET /bankroll/evolution': (call: ApiCall) => ({
        groupBy: call.query.get('groupBy'),
        currencies: [],
      }),
    });
    renderApp('/bankroll');

    expect(await screen.findByText('Nothing to show for these filters.')).toBeInTheDocument();
    expect(screen.queryByTestId('chart')).not.toBeInTheDocument();
  });

  it('is left out for a single day, which the cards already sum up', async () => {
    const calls = stubEvolution();
    renderApp('/bankroll?from=2026-01-20&to=2026-01-20');

    expect(await screen.findByRole('heading', { name: 'Per room' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Evolution' })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Group by' })).not.toBeInTheDocument();
    expect(screen.queryByTestId('chart')).not.toBeInTheDocument();
    expect(evolutionQueries(calls)).toEqual([]);
  });

  it('is drawn for this week', async () => {
    const calls = stubEvolution();
    const thisWeek = rangeOf('thisWeek');
    renderApp(`/bankroll?from=${thisWeek.from}&to=${thisWeek.to}`);

    await drawn();
    expect(evolutionQueries(calls).at(-1)).toMatchObject({
      groupBy: 'DAY',
      from: thisWeek.from,
      to: thisWeek.to,
    });
    expect(screen.getByRole('combobox', { name: 'Period' })).toHaveValue('This week');
  });

  describe('on a phone', () => {
    onANarrowScreen();

    it('fits the chart with fewer dates on its axis', async () => {
      stubEvolution();
      renderApp('/bankroll?from=2026-01-01&to=2026-01-31');

      const option = await drawn();
      expect(option.xAxis.splitNumber).toBeLessThanOrEqual(3);
      expect(screen.getByRole('combobox', { name: 'Group by' })).toBeInTheDocument();
    });
  });
});
