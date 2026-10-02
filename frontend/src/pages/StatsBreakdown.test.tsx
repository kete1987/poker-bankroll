import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { StatsFigures, StatsGroup, StatsGroups, StatsSummary } from '../api/types';
import { VARIANTS } from '../test/fixtures';
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

const SUMMARY: StatsSummary = {
  currencies: [
    {
      currencyCode: 'EUR',
      total: figures({ games: 12, net: 25.5 }),
      byGameType: [{ gameType: 'TOURNAMENT', figures: figures({ games: 12, net: 25.5 }) }],
      inPlay: { games: 0, invested: 0 },
    },
  ],
};

function group(key: StatsGroup['key'], games: number, net: number): StatsGroup {
  return {
    key,
    figures: figures({
      games,
      net,
      invested: games * 2,
      won: games * 2 + net,
      roi: net / (games * 2),
      averageBuyIn: 2,
      inTheMoneyRate: 0.25,
    }),
  };
}

/** What the backend answers for each breakdown, in its own order. */
const BREAKDOWNS: Record<string, StatsGroup[]> = {
  ROOM: [
    group({ room: { id: 1, name: 'Winamax' } }, 30, 12.5),
    group({ room: { id: 2, name: 'PokerStars' } }, 20, -8),
    group({ room: { id: 3, name: '888poker' } }, 5, 40),
  ],
  GAME_TYPE: [group({ gameType: 'TOURNAMENT' }, 9, 30), group({ gameType: 'CASH' }, 3, -4.5)],
  VARIANT: [
    group({ gameType: 'TOURNAMENT', variant: { id: 10, code: 'KO', name: null } }, 9, 30),
    group({ gameType: 'SIT_AND_GO', variant: { id: 21, code: null, name: 'Hyper Turbo' } }, 4, 1),
    group({ gameType: 'CASH', variant: null }, 3, -4.5),
  ],
  MODALITY: [group({ modality: 'NLHE' }, 10, 5), group({ modality: 'PLO' }, 2, -1)],
  BUY_IN_RANGE: [
    group({ buyInRange: { from: 0, to: 0 } }, 4, 3),
    group({ buyInRange: { from: 0, to: 1 } }, 20, -2),
    group({ buyInRange: { from: 1, to: 2 } }, 30, 15),
    group({ buyInRange: { from: 50, to: null } }, 1, -50),
  ],
  WEEKDAY: [group({ weekday: 1 }, 5, 1), group({ weekday: 7 }, 8, 20)],
  NAME: [
    ...Array.from({ length: 60 }, (_, index) =>
      group({ name: `Tournament ${String(index + 1).padStart(2, '0')}` }, 100 - index, index - 30),
    ),
    group({ name: null }, 7, -3),
  ],
};

function stubStats(handlers: Record<string, unknown> = {}) {
  return stubApi({
    'GET /variants': VARIANTS,
    'GET /stats/summary': SUMMARY,
    'GET /stats/groups': (call: ApiCall): StatsGroups => {
      const groupBy = call.query.get('groupBy') ?? '';
      return {
        groupBy: groupBy as StatsGroups['groupBy'],
        currencies: [{ currencyCode: 'EUR', groups: BREAKDOWNS[groupBy] ?? [] }],
      };
    },
    ...handlers,
  });
}

/** The requests for a breakdown: those for the periods ask for the game types too. */
function breakdownQueries(calls: ApiCall[]) {
  return calls
    .filter((call) => call.path === '/stats/groups' && !call.query.has('byGameType'))
    .map((call) => {
      const query: Record<string, string> = {};
      for (const key of new Set(call.query.keys())) {
        query[key] = call.query.getAll(key).join(',');
      }
      return query;
    });
}

/** The first cell of every row of the table, the heading apart. */
function rowLabels() {
  return screen.getAllByRole('rowheader').map((cell) => {
    // A room comes with its logo, or its initial: only the name is the label.
    const label = cell.cloneNode(true) as HTMLElement;
    label.querySelector('.mantine-Avatar-root')?.remove();
    return label.textContent;
  });
}

async function chartOption() {
  const chart = await screen.findByTestId('chart');
  return JSON.parse(chart.getAttribute('data-option') ?? '{}') as {
    yAxis: { data: string[] };
    series: { data: { value: number }[] }[];
  };
}

/** Options live in a popover that jsdom never sees as shown, hence `hidden: true`. */
async function breakDownBy(option: string) {
  await userEvent.click(screen.getByRole('combobox', { name: 'Break down by' }));
  await userEvent.click(await screen.findByRole('option', { name: option, hidden: true }));
}

describe('Statistics breakdowns', () => {
  it('is the other view of the statistics, by room to start with', async () => {
    const calls = stubStats();
    renderApp('/stats');

    // Results over time first; nothing is asked for the breakdown until it is opened.
    await screen.findByRole('combobox', { name: 'Group by' });
    expect(breakdownQueries(calls)).toEqual([]);
    await userEvent.click(screen.getByRole('radio', { name: 'Breakdowns' }));

    expect(await screen.findByRole('columnheader', { name: 'Room' })).toBeInTheDocument();
    expect(rowLabels()).toEqual(['Winamax', 'PokerStars', '888poker']);
    const thisYear = new Date().getFullYear();
    expect(breakdownQueries(calls)).toEqual([
      { groupBy: 'ROOM', from: `${thisYear}-01-01`, to: `${thisYear}-12-31` },
    ]);
    // The cut in time belongs to the other view.
    expect(screen.queryByRole('combobox', { name: 'Group by' })).not.toBeInTheDocument();
    expect(screen.queryByText('Net evolution')).not.toBeInTheDocument();

    const winamax = within(screen.getByRole('row', { name: /Winamax/ }));
    expect(winamax.getAllByRole('cell').map((cell) => cell.textContent)).toEqual([
      '30',
      '€2.00',
      '25.00%',
      '€60.00',
      '€72.50',
      '+€12.50',
      '20.83%',
    ]);
  });

  it('draws the net of each group as bars, in the order of the table', async () => {
    const calls = stubStats();
    renderApp('/stats?view=breakdown');

    const option = await chartOption();

    // The axis of a bar chart grows upwards: the first row is the last category.
    expect(option.yAxis.data).toEqual(['888poker', 'PokerStars', 'Winamax']);
    // Opened on this view, the periods of the other one are never asked for.
    expect(
      calls
        .filter((call) => call.path === '/stats/groups')
        .map((call) => call.query.get('groupBy')),
    ).toEqual(['ROOM']);
    expect(option.series[0]?.data.map((bar) => bar.value)).toEqual([40, -8, 12.5]);
  });

  it('sorts by the column that is clicked, and the other way when clicked again', async () => {
    stubStats();
    renderApp('/stats?view=breakdown');
    await screen.findByRole('columnheader', { name: 'Room' });
    expect(screen.getByRole('columnheader', { name: 'Played' })).toHaveAttribute(
      'aria-sort',
      'descending',
    );

    await userEvent.click(screen.getByRole('button', { name: 'Net' }));
    expect(rowLabels()).toEqual(['888poker', 'Winamax', 'PokerStars']);
    expect(screen.getByRole('columnheader', { name: 'Net' })).toHaveAttribute(
      'aria-sort',
      'descending',
    );
    expect(screen.getByRole('columnheader', { name: 'Played' })).toHaveAttribute(
      'aria-sort',
      'none',
    );
    expect((await chartOption()).yAxis.data).toEqual(['PokerStars', 'Winamax', '888poker']);

    await userEvent.click(screen.getByRole('button', { name: 'Net' }));
    expect(rowLabels()).toEqual(['PokerStars', 'Winamax', '888poker']);
    expect(screen.getByRole('columnheader', { name: 'Net' })).toHaveAttribute(
      'aria-sort',
      'ascending',
    );

    // Names read from the first one.
    await userEvent.click(screen.getByRole('button', { name: 'Room' }));
    expect(rowLabels()).toEqual(['888poker', 'PokerStars', 'Winamax']);
  });

  it('names game types, variants and modalities', async () => {
    stubStats();
    renderApp('/stats?view=breakdown&by=variant');

    expect(await screen.findByRole('columnheader', { name: 'Variant' })).toBeInTheDocument();
    // A variant goes with its type: the same one exists in several.
    expect(rowLabels()).toEqual([
      'KO · Tournament',
      'Hyper Turbo · Sit & Go / Spin',
      'No variant · Cash',
    ]);

    await breakDownBy('Game type');
    await waitFor(() => expect(rowLabels()).toEqual(['Tournament', 'Cash']));

    await breakDownBy('Modality');
    await waitFor(() => expect(rowLabels()).toEqual(["Hold'em", 'Omaha']));
  });

  it('names the ranges of buy-ins and keeps them from lowest to highest', async () => {
    stubStats();
    renderApp('/stats?view=breakdown&by=buy_in_range');

    expect(await screen.findByRole('columnheader', { name: 'Buy-in range' })).toBeInTheDocument();
    expect(rowLabels()).toEqual(['Free', 'Below €1.00', '€1.00 to €1.99', '€50.00 or more']);
    // Their own order, not the one of any column, until one is chosen.
    for (const header of screen.getAllByRole('columnheader')) {
      expect(header).toHaveAttribute('aria-sort', 'none');
    }

    await userEvent.click(screen.getByRole('button', { name: 'Played' }));
    expect(rowLabels()).toEqual(['€1.00 to €1.99', 'Below €1.00', 'Free', '€50.00 or more']);
  });

  it('names the days of the week', async () => {
    stubStats();
    renderApp('/stats?view=breakdown&by=weekday');

    expect(
      await screen.findByRole('columnheader', { name: 'Day of the week' }),
    ).toBeInTheDocument();
    expect(rowLabels()).toEqual(['Monday', 'Sunday']);
  });

  it('breaks tournaments down by name: the most played ones, and the rest by searching', async () => {
    const calls = stubStats();
    renderApp('/stats?view=breakdown&by=name&room=1');

    expect(
      await screen.findByRole('columnheader', { name: 'Tournament name' }),
    ).toBeInTheDocument();
    // Names are those of tournaments when the filter has no type.
    expect(breakdownQueries(calls).at(-1)).toMatchObject({
      groupBy: 'NAME',
      gameType: 'TOURNAMENT',
      roomId: '1',
    });
    expect(rowLabels()).toHaveLength(50);
    expect(rowLabels()[0]).toBe('Tournament 01');
    expect(screen.getByText('And 11 more names: search to see them.')).toBeInTheDocument();
    expect((await chartOption()).yAxis.data).toHaveLength(15);

    await userEvent.type(screen.getByRole('textbox', { name: 'Search a name' }), 'ment 5');
    expect(rowLabels()).toEqual(Array.from({ length: 10 }, (_, index) => `Tournament 5${index}`));
    expect(screen.queryByText(/more names/)).not.toBeInTheDocument();

    // The games without a name are a group too.
    await userEvent.clear(screen.getByRole('textbox', { name: 'Search a name' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Search a name' }), 'no name');
    expect(rowLabels()).toEqual(['No name']);

    await userEvent.clear(screen.getByRole('textbox', { name: 'Search a name' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Search a name' }), 'zzz');
    expect(screen.getByText('No name contains that text.')).toBeInTheDocument();
    expect(screen.queryByTestId('chart')).not.toBeInTheDocument();
  });

  it('breaks down by name the types the filter asks for', async () => {
    const calls = stubStats();
    renderApp('/stats?view=breakdown&by=name&type=SIT_AND_GO,CASH');

    await screen.findByRole('columnheader', { name: 'Tournament name' });
    expect(breakdownQueries(calls).at(-1)).toMatchObject({
      groupBy: 'NAME',
      gameType: 'SIT_AND_GO,CASH',
    });
  });

  it('takes the breakdown and its order from the URL, and drops the order with another one', async () => {
    const calls = stubStats();
    renderApp('/stats?view=breakdown&by=room&sort=net,asc&period=all');

    expect(await screen.findByRole('columnheader', { name: 'Net' })).toHaveAttribute(
      'aria-sort',
      'ascending',
    );
    expect(rowLabels()).toEqual(['PokerStars', 'Winamax', '888poker']);
    expect(breakdownQueries(calls)).toEqual([{ groupBy: 'ROOM' }]);

    await breakDownBy('Day of the week');
    await waitFor(() => expect(rowLabels()).toEqual(['Monday', 'Sunday']));
    expect(screen.getByRole('columnheader', { name: 'Net' })).toHaveAttribute('aria-sort', 'none');
  });

  it('ignores a breakdown or an order the URL makes up', async () => {
    stubStats();
    renderApp('/stats?view=breakdown&by=month&sort=luck,desc');

    expect(await screen.findByRole('columnheader', { name: 'Room' })).toBeInTheDocument();
    expect(rowLabels()).toEqual(['Winamax', 'PokerStars', '888poker']);
  });

  it('says so when there is nothing to break down or it cannot be loaded', async () => {
    stubStats({
      'GET /stats/groups': (call: ApiCall) =>
        call.query.get('groupBy') === 'ROOM'
          ? { groupBy: 'ROOM', currencies: [] }
          : call.query.get('groupBy') === 'MODALITY'
            ? problem(500, 'INTERNAL_ERROR', 'Boom')
            : { groupBy: call.query.get('groupBy'), currencies: [] },
    });
    renderApp('/stats?view=breakdown');

    expect(await screen.findByText('No finished games in this period.')).toBeInTheDocument();
    expect(screen.queryByTestId('chart')).not.toBeInTheDocument();

    await breakDownBy('Modality');
    expect(
      await screen.findByText(/The data could not be loaded/, {}, { timeout: 5000 }),
    ).toBeInTheDocument();
  });
});
