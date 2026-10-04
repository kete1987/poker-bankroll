import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { StatsFigures, StatsGroups, StatsSummary } from '../api/types';
import { rangeOf } from '../components/period';
import { granularityFor } from '../stats/useStatsFilters';
import { room, statsGroups, statsSummary, VARIANTS } from '../test/fixtures';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

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

const EUR_SUMMARY = {
  currencyCode: 'EUR',
  total: figures({ games: 12, net: 25.5, invested: 51, won: 76.5, roi: 0.5 }),
  byGameType: [
    { gameType: 'TOURNAMENT' as const, figures: figures({ games: 9, net: 30 }) },
    { gameType: 'SIT_AND_GO' as const, figures: figures({ games: 3, net: -4.5 }) },
  ],
  inPlay: { games: 0, invested: 0 },
};
const USD_SUMMARY = {
  currencyCode: 'USD',
  total: figures({ games: 2, net: -10, invested: 10 }),
  byGameType: [],
  inPlay: { games: 0, invested: 0 },
};

const SUMMARY: StatsSummary = statsSummary([EUR_SUMMARY]);

const PERIODS: Record<string, string[]> = {
  DAY: ['2026-01-19', '2026-01-20', '2026-01-22'],
  WEEK: ['2026-01-19', '2026-01-26', '2026-02-02'],
  MONTH: ['2026-01', '2026-02', '2026-03'],
};
const NETS = [10, -4.5, 20];

/** The groups of each period with these nets, in a currency. */
function periodGroups(groupBy: string, nets: number[]) {
  const periods = PERIODS[groupBy] ?? [];
  let cumulativeNet = 0;
  return periods.map((period, index) => {
    const net = nets[index] ?? 0;
    cumulativeNet += net;
    return {
      key: { period },
      figures: figures({ games: index + 3, net, invested: 10, won: 10 + net, roi: net / 10 }),
      cumulativeNet,
      byGameType: [
        {
          gameType: index === 1 ? ('SIT_AND_GO' as const) : ('TOURNAMENT' as const),
          figures: figures({ games: index + 3, net }),
        },
      ],
    };
  });
}

function groups(groupBy: string): StatsGroups {
  return statsGroups(groupBy as StatsGroups['groupBy'], [
    { currencyCode: 'EUR', groups: periodGroups(groupBy, NETS) },
  ]);
}

function stubStats(handlers: Record<string, unknown> = {}) {
  return stubApi({
    'GET /rooms': [room({ id: 1, name: 'Winamax' })],
    'GET /variants': VARIANTS,
    'GET /stats/summary': SUMMARY,
    'GET /stats/groups': (call: ApiCall) => groups(call.query.get('groupBy') ?? ''),
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

/** What the chart was asked to draw. */
async function chartOption() {
  const chart = await screen.findByTestId('chart');
  return JSON.parse(chart.getAttribute('data-option') ?? '{}') as {
    xAxis: { data: string[] };
    series: { data: number[] }[];
  };
}

describe('granularityFor', () => {
  it('cuts by day, week or month depending on how long the period is', () => {
    expect(granularityFor({ from: '2026-01-01', to: '2026-01-31' })).toBe('DAY');
    expect(granularityFor({ from: '2026-01-01', to: '2026-04-02' })).toBe('DAY');
    expect(granularityFor({ from: '2026-01-01', to: '2026-04-03' })).toBe('WEEK');
    expect(granularityFor({ from: '2026-01-01', to: '2026-12-31' })).toBe('WEEK');
    expect(granularityFor({ from: '2025-01-01', to: '2026-12-31' })).toBe('MONTH');
    // An open end has no length: by month.
    expect(granularityFor({})).toBe('MONTH');
    expect(granularityFor({ from: '2026-01-01' })).toBe('MONTH');
  });
});

describe('Statistics page', () => {
  it('draws the cumulative net of this year by week, in the only currency there is', async () => {
    const calls = stubStats();
    renderApp('/stats');

    const option = await chartOption();

    // A week is its Monday on the axis.
    expect(option.xAxis.data).toEqual(['19/01/2026', '26/01/2026', '02/02/2026']);
    expect(option.series[0]?.data).toEqual([10, 5.5, 25.5]);
    const thisYear = rangeOf('thisYear');
    expect(queriesTo(calls, '/stats/groups').at(-1)).toEqual({
      groupBy: 'WEEK',
      byGameType: 'true',
      from: thisYear.from,
      to: thisYear.to,
    });
    expect(screen.getByRole('combobox', { name: 'Group by' })).toHaveValue('Week');
    expect(screen.queryByRole('combobox', { name: 'Currency' })).not.toBeInTheDocument();
  });

  it('converts everything to the base currency when currencies are mixed', async () => {
    stubStats({
      'GET /stats/summary': statsSummary([EUR_SUMMARY, USD_SUMMARY], {
        currencyCode: 'EUR',
        total: figures({ games: 14, net: 16.5, invested: 60, roi: 0.275 }),
        missingRates: [{ currencyCode: 'USD', from: '2026-01-19', to: '2026-01-19' }],
      }),
      'GET /stats/groups': (call: ApiCall) => {
        const groupBy = call.query.get('groupBy') ?? '';
        return statsGroups(
          groupBy as StatsGroups['groupBy'],
          [
            { currencyCode: 'EUR', groups: periodGroups(groupBy, NETS) },
            { currencyCode: 'USD', groups: periodGroups(groupBy, [-10]) },
          ],
          { currencyCode: 'EUR', groups: periodGroups(groupBy, [1, -4.5, 20]) },
        );
      },
    });
    renderApp('/stats');

    const option = await chartOption();
    expect(option.series[0]?.data).toEqual([1, -3.5, 16.5]);
    expect(card('Net of the period').getByText('+€16.50')).toBeInTheDocument();
    expect(card('Net of the period').getByText('+€25.50 · -US$10.00')).toBeInTheDocument();
    expect(card('ROI').getByText('€60.00 invested')).toBeInTheDocument();
    expect(card('ROI').getByText('€51.00 · US$10.00')).toBeInTheDocument();
    expect(card('Best week').getByText('+€20.00')).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Currency' })).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('USD on 19/01/2026');
  });

  it('sums the period up in cards, with its best and worst week', async () => {
    stubStats();
    renderApp('/stats');
    await chartOption();

    expect(card('Net of the period').getByText('+€25.50')).toBeInTheDocument();
    expect(card('Net of the period').getByText('12 games')).toBeInTheDocument();
    expect(card('ROI').getByText('50.00%')).toBeInTheDocument();
    expect(card('ROI').getByText('€51.00 invested')).toBeInTheDocument();
    expect(card('Best week').getByText('+€20.00')).toBeInTheDocument();
    expect(card('Best week').getByText('Week of 02/02/2026')).toBeInTheDocument();
    expect(card('Worst week').getByText('-€4.50')).toBeInTheDocument();
    expect(card('Worst week').getByText('Week of 26/01/2026')).toBeInTheDocument();
  });

  it('follows the length of the period unless a cut is chosen', async () => {
    const calls = stubStats();
    renderApp('/stats?from=2026-01-01&to=2026-01-31');

    expect((await chartOption()).xAxis.data).toEqual(['19/01/2026', '20/01/2026', '22/01/2026']);
    expect(queriesTo(calls, '/stats/groups').at(-1)).toMatchObject({ groupBy: 'DAY' });

    await userEvent.click(screen.getByRole('combobox', { name: 'Group by' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Month', hidden: true }));

    await waitFor(() =>
      expect(queriesTo(calls, '/stats/groups').at(-1)).toMatchObject({ groupBy: 'MONTH' }),
    );
    await waitFor(async () =>
      expect((await chartOption()).xAxis.data).toEqual([
        'January 2026',
        'February 2026',
        'March 2026',
      ]),
    );
    expect(card('Best month').getByText('March 2026')).toBeInTheDocument();
  });

  it('keeps naming the data on screen by its own cut while another one loads', async () => {
    let releaseMonths: () => void = () => {};
    const monthsCanLoad = new Promise<void>((resolve) => {
      releaseMonths = resolve;
    });
    stubStats({
      'GET /stats/groups': async (call: ApiCall) => {
        const groupBy = call.query.get('groupBy') ?? '';
        if (groupBy === 'MONTH') {
          await monthsCanLoad;
        }
        return groups(groupBy);
      },
    });
    renderApp('/stats');
    await chartOption();

    await userEvent.click(screen.getByRole('combobox', { name: 'Group by' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Month', hidden: true }));

    // The weeks are still there, as weeks.
    expect((await chartOption()).xAxis.data).toEqual(['19/01/2026', '26/01/2026', '02/02/2026']);
    expect(card('Best week').getByText('Week of 02/02/2026')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Best month' })).not.toBeInTheDocument();

    releaseMonths();
    expect(await screen.findByRole('region', { name: 'Best month' })).toBeInTheDocument();
  });

  it('draws the net of each period as bars when asked', async () => {
    stubStats();
    renderApp('/stats');
    expect((await chartOption()).series[0]).toMatchObject({ type: 'line' });

    await userEvent.click(screen.getByRole('radio', { name: 'Per period' }));

    await waitFor(async () =>
      expect((await chartOption()).series[0]).toMatchObject({ type: 'bar' }),
    );
    const bars = (await chartOption()).series[0]?.data as unknown as { value: number }[];
    expect(bars.map((bar) => bar.value)).toEqual([10, -4.5, 20]);
  });

  it('takes the kind of chart from the URL', async () => {
    stubStats();
    renderApp('/stats?chart=period');

    expect((await chartOption()).series[0]).toMatchObject({ type: 'bar' });
    expect(screen.getByRole('radio', { name: 'Per period' })).toBeChecked();
  });

  it('lists the results of each period, newest first, with the net of each game type', async () => {
    stubStats();
    renderApp('/stats');

    const table = within(await screen.findByRole('table'));
    expect(screen.getByRole('heading', { name: 'Results per week' })).toBeInTheDocument();
    // Cash was not played: it has no column.
    expect(table.getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      'Week',
      'Played',
      'Tournament',
      'Sit & Go / Spin',
      'Invested',
      'Won',
      'Net',
      'ROI',
    ]);
    const rows = table.getAllByRole('row').slice(1);
    expect(rows.map((row) => within(row).getAllByRole('rowheader')[0]?.textContent)).toEqual([
      'Week of 02/02/2026',
      'Week of 26/01/2026',
      'Week of 19/01/2026',
      'Total',
    ]);
    expect(rows[0]).toHaveTextContent(
      ['5', '+€20.00', '—', '€10.00', '€30.00', '+€20.00', '200.00%'].join(''),
    );
    // A week with only Sit & Go: a dash under Tournament.
    expect(rows[1]).toHaveTextContent(
      ['4', '—', '-€4.50', '€10.00', '€5.50', '-€4.50', '-45.00%'].join(''),
    );
    expect(rows[3]).toHaveTextContent(
      ['12', '+€30.00', '-€4.50', '€51.00', '€76.50', '+€25.50', '50.00%'].join(''),
    );
  });

  it('pages the table when there are many periods', async () => {
    const days = Array.from({ length: 40 }, (_, index) => {
      const day = String(index + 1).padStart(2, '0');
      return index < 31 ? `2026-01-${day}` : `2026-02-${String(index - 30).padStart(2, '0')}`;
    });
    stubStats({
      'GET /stats/groups': statsGroups('DAY', [
        {
          currencyCode: 'EUR',
          groups: days.map((period, index) => ({
            key: { period },
            figures: figures({ games: 1, net: 1 }),
            cumulativeNet: index + 1,
            byGameType: [{ gameType: 'TOURNAMENT', figures: figures({ games: 1, net: 1 }) }],
          })),
        },
      ]),
    });
    renderApp('/stats?group=day');

    const table = within(await screen.findByRole('table'));
    // 31 periods and the total.
    expect(table.getAllByRole('rowheader')).toHaveLength(32);
    expect(table.getAllByRole('rowheader')[0]).toHaveTextContent('09/02/2026');

    await userEvent.click(screen.getByRole('button', { name: 'Page 2' }));

    expect(table.getAllByRole('rowheader')).toHaveLength(10);
    expect(table.getAllByRole('rowheader')[0]).toHaveTextContent('09/01/2026');
    expect(table.getAllByRole('rowheader').at(-1)).toHaveTextContent('Total');
  });

  it('takes the page of the table from the URL and goes back to the first when a filter changes', async () => {
    const days = Array.from({ length: 40 }, (_, index) =>
      new Date(Date.UTC(2026, 0, index + 1)).toISOString().slice(0, 10),
    );
    stubStats({
      'GET /stats/groups': statsGroups('DAY', [
        {
          currencyCode: 'EUR',
          groups: days.map((period, index) => ({
            key: { period },
            figures: figures({ games: 1, net: 1 }),
            cumulativeNet: index + 1,
            byGameType: [{ gameType: 'TOURNAMENT', figures: figures({ games: 1, net: 1 }) }],
          })),
        },
      ]),
    });
    renderApp('/stats?group=day&page=2');

    const table = within(await screen.findByRole('table'));
    expect(table.getAllByRole('rowheader')).toHaveLength(10);
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page');

    // The kind of chart does not move the table...
    await userEvent.click(screen.getByRole('radio', { name: 'Per period' }));
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page');

    // ...but another filter lists other periods.
    await userEvent.click(screen.getByRole('combobox', { name: 'Room' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Winamax', hidden: true }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Page 1' })).toHaveAttribute(
        'aria-current',
        'page',
      ),
    );
  });

  it('filters by type, room and variant', async () => {
    const calls = stubStats();
    renderApp('/stats');
    await chartOption();

    await userEvent.click(screen.getByRole('combobox', { name: 'Type' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Tournament', hidden: true }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Room' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Winamax', hidden: true }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Variant' }));
    await userEvent.click(await screen.findByRole('option', { name: 'KO', hidden: true }));

    await waitFor(() =>
      expect(queriesTo(calls, '/stats/groups').at(-1)).toMatchObject({
        gameType: 'TOURNAMENT',
        roomId: '1',
        variantId: '10',
      }),
    );
    // The summary below the chart is about the same games.
    expect(queriesTo(calls, '/stats/summary').at(-1)).toMatchObject({
      gameType: 'TOURNAMENT',
      roomId: '1',
      variantId: '10',
    });
  });

  it('takes everything from the URL', async () => {
    const calls = stubStats();
    renderApp('/stats?period=all&type=CASH,SIT_AND_GO&room=1&variant=20&group=day');

    const option = await chartOption();

    expect(queriesTo(calls, '/stats/groups').at(-1)).toEqual({
      groupBy: 'DAY',
      byGameType: 'true',
      gameType: 'CASH,SIT_AND_GO',
      roomId: '1',
      variantId: '20',
    });
    expect(screen.getByRole('combobox', { name: 'Period' })).toHaveValue('All time');
    expect(screen.getByRole('combobox', { name: 'Group by' })).toHaveValue('Day');
    expect(option.series[0]?.data).toEqual([10, 5.5, 25.5]);
  });

  it('has no best or worst period with a single one', async () => {
    stubStats({
      'GET /stats/summary': statsSummary([USD_SUMMARY]),
      'GET /stats/groups': (call: ApiCall) =>
        statsGroups(call.query.get('groupBy') as StatsGroups['groupBy'], [
          { currencyCode: 'USD', groups: periodGroups('MONTH', [-10]).slice(0, 1) },
        ]),
    });
    renderApp('/stats?period=all');

    const option = await chartOption();
    expect(option.series[0]?.data).toEqual([-10]);
    // In dollars, the only currency there is.
    expect(card('Net of the period').getByText('-US$10.00')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: /Best/ })).not.toBeInTheDocument();
  });

  it('shows no chart for a single day, but the cards and the table', async () => {
    stubStats();
    renderApp('/stats?from=2026-01-20&to=2026-01-20');

    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(card('Net of the period').getByText('+€25.50')).toBeInTheDocument();
    expect(screen.queryByTestId('chart')).not.toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: 'Per period' })).not.toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Period' })).toHaveValue('Custom');
  });

  it('names today in the URL and hides the chart for it', async () => {
    stubStats();
    const today = rangeOf('today');
    renderApp(`/stats?from=${today.from}&to=${today.to}`);

    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Period' })).toHaveValue('Today');
    expect(screen.queryByTestId('chart')).not.toBeInTheDocument();
  });

  it('hides the chart when yesterday is chosen, and draws it again for this week', async () => {
    const calls = stubStats();
    renderApp('/stats');
    await chartOption();

    await userEvent.click(screen.getByRole('combobox', { name: 'Period' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Yesterday', hidden: true }));

    const yesterday = rangeOf('yesterday');
    await waitFor(() =>
      expect(queriesTo(calls, '/stats/groups').at(-1)).toMatchObject({
        groupBy: 'DAY',
        from: yesterday.from,
        to: yesterday.to,
      }),
    );
    await waitFor(() => expect(screen.queryByTestId('chart')).not.toBeInTheDocument());
    expect(screen.getByRole('table')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('combobox', { name: 'Period' }));
    await userEvent.click(await screen.findByRole('option', { name: 'This week', hidden: true }));

    const thisWeek = rangeOf('thisWeek');
    expect(await screen.findByTestId('chart')).toBeInTheDocument();
    expect(queriesTo(calls, '/stats/groups').at(-1)).toMatchObject({
      from: thisWeek.from,
      to: thisWeek.to,
    });
    expect(screen.getByRole('radio', { name: 'Per period' })).toBeInTheDocument();
  });

  it('says so when the period has no games', async () => {
    stubStats({ 'GET /stats/summary': statsSummary([]) });
    renderApp('/stats');

    expect(await screen.findByText('No finished games in this period.')).toBeInTheDocument();
    expect(screen.queryByTestId('chart')).not.toBeInTheDocument();
  });

  it.each(['/stats/summary', '/stats/groups', '/rooms', '/variants'])(
    'says so when %s cannot be loaded',
    async (path) => {
      stubStats({ [`GET ${path}`]: () => problem(500, 'INTERNAL_ERROR', 'Boom') });
      renderApp('/stats');

      expect(
        await screen.findByText(
          'The data could not be loaded. Check that the API is available and reload the page.',
        ),
      ).toBeInTheDocument();
    },
  );
});
