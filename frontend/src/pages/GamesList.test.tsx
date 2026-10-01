import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type { Game, GamePage } from '../api/types';
import { rangeOf } from '../games/period';
import { game, page, ROOMS, VARIANTS } from '../test/fixtures';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

const KO = { id: 10, code: 'KO', name: null };

interface StubOptions {
  finished?: Game[];
  inPlay?: Game[];
  paging?: Partial<GamePage>;
  handlers?: Record<string, unknown>;
}

/** The API of the games page: the games in play and one page of finished ones. */
function stubGames({ finished = [], inPlay = [], paging = {}, handlers = {} }: StubOptions = {}) {
  return stubApi({
    'GET /rooms': ROOMS,
    'GET /variants': VARIANTS,
    'GET /games': (call: ApiCall) =>
      call.query.get('status') === 'IN_PLAY' ? page(inPlay) : page(finished, paging),
    ...handlers,
  });
}

/** The requests for the table (not the ones for the games in play). */
function listQueries(calls: ApiCall[]) {
  return calls
    .filter((call) => call.method === 'GET' && call.path === '/games')
    .filter((call) => call.query.get('status') === 'FINISHED')
    .map((call) => Object.fromEntries(call.query));
}

function lastListQuery(calls: ApiCall[]) {
  return listQueries(calls).at(-1);
}

function sent(calls: ApiCall[], method: string) {
  return calls.filter((call) => call.method === method);
}

async function tableRows() {
  const table = await screen.findByRole('table');
  return within(table).getAllByRole('row').slice(1);
}

function inPlaySection() {
  return within(screen.getByRole('region', { name: /in play/ }));
}

describe('Games table', () => {
  it('asks for the finished games, newest first, a page at a time', async () => {
    const calls = stubGames({ finished: [game({})] });
    renderApp('/games');

    await tableRows();

    expect(lastListQuery(calls)).toEqual({
      status: 'FINISHED',
      page: '0',
      size: '25',
      sort: 'playedOn,desc',
    });
  });

  it('shows each game with its room, buy-in, winnings and net', async () => {
    stubGames({
      finished: [
        game({
          id: 1,
          name: 'Kill The Fish',
          playedAt: '21:30:00',
          variant: KO,
          entries: 2,
          buyIn: 5,
          prize: 10,
          bounty: 3.5,
          won: 13.5,
          net: 3.5,
          notes: 'final table',
        }),
        game({
          id: 2,
          gameType: 'SIT_AND_GO',
          modality: 'PLO',
          currencyCode: 'USD',
          room: { id: 2, name: 'PokerStars' },
          buyIn: 10,
          paidWithTicket: true,
          ticketPrizeValue: 20,
          ticketDescription: 'Main Event',
          net: 0,
        }),
        game({ id: 3, gameType: 'CASH', buyIn: 2, prize: 1.4, won: 1.4, net: -0.6 }),
      ],
    });
    renderApp('/games');

    const [tournament, sitAndGo, cash] = await tableRows();

    expect(tournament).toHaveTextContent('19/01/2026');
    expect(tournament).toHaveTextContent('21:30');
    expect(tournament).toHaveTextContent('Kill The Fish');
    expect(tournament).toHaveTextContent('Tournament · KO');
    expect(tournament).toHaveTextContent('Winamax');
    expect(tournament).toHaveTextContent('€5.00 ×2');
    // With bounties: how much came from the prize, how much from bounties, and the total.
    expect(tournament).toHaveTextContent('Prize €10.00');
    expect(tournament).toHaveTextContent('Bounties €3.50');
    expect(tournament).toHaveTextContent('€13.50');
    expect(tournament).toHaveTextContent('+€3.50');
    expect(
      within(tournament!).getByRole('img', { name: 'Notes: final table' }),
    ).toBeInTheDocument();

    expect(sitAndGo).toHaveTextContent('Sit & Go / Spin');
    expect(sitAndGo).toHaveTextContent('Omaha');
    expect(sitAndGo).toHaveTextContent('PokerStars');
    expect(sitAndGo).toHaveTextContent('US$10.00');
    expect(sitAndGo).not.toHaveTextContent('Prize');
    expect(
      within(sitAndGo!).getByRole('img', { name: 'One entry paid with a ticket' }),
    ).toBeInTheDocument();
    expect(
      within(sitAndGo!).getByRole('img', { name: 'Ticket won: US$20.00' }),
    ).toBeInTheDocument();
    expect(within(sitAndGo!).queryByRole('img', { name: /Notes/ })).not.toBeInTheDocument();

    expect(cash).toHaveTextContent('Cash');
    expect(cash).toHaveTextContent('€1.40');
    expect(cash).toHaveTextContent('-€0.60');
  });

  it('says so when there are no games, or none for the filters', async () => {
    stubGames();
    const first = renderApp('/games');
    expect(await screen.findByText('There are no finished games yet.')).toBeInTheDocument();
    first.unmount();

    renderApp('/games?type=CASH');
    expect(await screen.findByText('No game matches these filters.')).toBeInTheDocument();
  });

  it('takes filters, order and page from the URL', async () => {
    const calls = stubGames({
      finished: [game({})],
      paging: { page: 1, totalPages: 3, totalItems: 60 },
    });
    renderApp(
      '/games?from=2026-01-01&to=2026-01-31&type=TOURNAMENT&room=1&variant=10&q=fish&sort=net,asc&page=2',
    );

    await tableRows();

    expect(lastListQuery(calls)).toEqual({
      status: 'FINISHED',
      from: '2026-01-01',
      to: '2026-01-31',
      gameType: 'TOURNAMENT',
      roomId: '1',
      variantId: '10',
      q: 'fish',
      page: '1',
      size: '25',
      sort: 'net,asc',
    });
    expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('fish');
    expect(screen.getByRole('combobox', { name: 'Type' })).toHaveValue('Tournament');
    expect(screen.getByRole('combobox', { name: 'Room' })).toHaveValue('Winamax');
    expect(screen.getByRole('combobox', { name: 'Period' })).toHaveValue('Custom');
    expect(screen.getByLabelText('From')).toHaveValue('2026-01-01');
    expect(screen.getByText('60 games')).toBeInTheDocument();
  });

  it('ignores what makes no sense in the URL', async () => {
    const calls = stubGames({ finished: [game({})] });
    renderApp('/games?from=yesterday&to=2026-02-31&type=BINGO&room=abc&sort=name,up&page=-3');

    await tableRows();

    expect(lastListQuery(calls)).toEqual({
      status: 'FINISHED',
      page: '0',
      size: '25',
      sort: 'playedOn,desc',
    });
  });

  it('filters by period, type, room, variant and text, going back to the first page', async () => {
    const calls = stubGames({ finished: [game({})], paging: { page: 2, totalPages: 5 } });
    renderApp('/games?page=3');
    await tableRows();

    await userEvent.click(screen.getByRole('combobox', { name: 'Period' }));
    await userEvent.click(await screen.findByRole('option', { name: 'This month', hidden: true }));
    const thisMonth = rangeOf('thisMonth');
    await waitFor(() =>
      expect(lastListQuery(calls)).toMatchObject({
        from: thisMonth.from,
        to: thisMonth.to,
        page: '0',
      }),
    );

    await userEvent.click(screen.getByRole('combobox', { name: 'Type' }));
    await userEvent.click(
      await screen.findByRole('option', { name: 'Sit & Go / Spin', hidden: true }),
    );
    await userEvent.click(screen.getByRole('combobox', { name: 'Room' }));
    // Inactive rooms are offered too: they have history.
    await userEvent.click(await screen.findByRole('option', { name: 'Unibet', hidden: true }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Variant' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Expresso', hidden: true }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Search' }), 'nitro');

    await waitFor(() =>
      expect(lastListQuery(calls)).toMatchObject({
        gameType: 'SIT_AND_GO',
        roomId: '3',
        variantId: '20',
        q: 'nitro',
        page: '0',
      }),
    );

    // The variant belongs to the type: changing the type drops it.
    await userEvent.click(screen.getByRole('combobox', { name: 'Type' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Tournament', hidden: true }));
    await waitFor(() => expect(lastListQuery(calls)).toMatchObject({ gameType: 'TOURNAMENT' }));
    expect(lastListQuery(calls)).not.toHaveProperty('variantId');

    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() =>
      expect(lastListQuery(calls)).toEqual({
        status: 'FINISHED',
        page: '0',
        size: '25',
        sort: 'playedOn,desc',
      }),
    );
    expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('');
    expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument();
  });

  it('lets a custom range of dates be typed', async () => {
    const calls = stubGames({ finished: [game({})] });
    renderApp('/games');
    await tableRows();

    await userEvent.click(screen.getByRole('combobox', { name: 'Period' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Custom', hidden: true }));
    await userEvent.type(screen.getByLabelText('From'), '2026-02-01');

    await waitFor(() => expect(lastListQuery(calls)).toMatchObject({ from: '2026-02-01' }));
    expect(lastListQuery(calls)).not.toHaveProperty('to');
  });

  it('orders by the column that is clicked, and reverses it when clicked again', async () => {
    const calls = stubGames({ finished: [game({})] });
    renderApp('/games');
    await tableRows();
    expect(screen.getByRole('columnheader', { name: 'Date' })).toHaveAttribute(
      'aria-sort',
      'descending',
    );

    await userEvent.click(screen.getByRole('button', { name: 'Net' }));
    await waitFor(() => expect(lastListQuery(calls)).toMatchObject({ sort: 'net,desc' }));
    expect(screen.getByRole('columnheader', { name: 'Net' })).toHaveAttribute(
      'aria-sort',
      'descending',
    );
    expect(screen.getByRole('columnheader', { name: 'Date' })).toHaveAttribute('aria-sort', 'none');

    await userEvent.click(screen.getByRole('button', { name: 'Net' }));
    await waitFor(() => expect(lastListQuery(calls)).toMatchObject({ sort: 'net,asc' }));

    await userEvent.click(screen.getByRole('button', { name: 'Buy-in' }));
    await waitFor(() => expect(lastListQuery(calls)).toMatchObject({ sort: 'buyIn,desc' }));
    await userEvent.click(screen.getByRole('button', { name: 'Won' }));
    await waitFor(() => expect(lastListQuery(calls)).toMatchObject({ sort: 'won,desc' }));
  });

  it('moves between pages', async () => {
    const calls = stubGames({ finished: [game({})], paging: { totalPages: 4, totalItems: 1234 } });
    renderApp('/games');
    await tableRows();
    expect(screen.getByText('1,234 games')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Page 3' }));
    await waitFor(() => expect(lastListQuery(calls)).toMatchObject({ page: '2' }));

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await waitFor(() => expect(lastListQuery(calls)).toMatchObject({ page: '3' }));
  });

  it('goes to the last page when the one asked for is past the end', async () => {
    const calls = stubApi({
      'GET /rooms': ROOMS,
      'GET /variants': VARIANTS,
      'GET /games': (call: ApiCall) => {
        if (call.query.get('status') === 'IN_PLAY') {
          return page([]);
        }
        const asked = Number(call.query.get('page'));
        // Two pages: anything further is empty, as the backend answers.
        return page(asked < 2 ? [game({ id: asked + 1 })] : [], {
          page: asked,
          totalPages: 2,
          totalItems: 26,
        });
      },
    });
    renderApp('/games?page=9');

    await tableRows();

    expect(lastListQuery(calls)).toMatchObject({ page: '1' });
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByText('There are no finished games yet.')).not.toBeInTheDocument();
  });

  it('edits a game with the form filled in', async () => {
    const original = game({
      id: 7,
      name: 'Kill The Fish',
      playedOn: '2026-01-19',
      playedAt: '21:30:00',
      variant: KO,
      buyIn: 5,
      entries: 2,
      prize: 10,
      bounty: 3.5,
      won: 13.5,
      notes: 'final table',
    });
    const calls = stubGames({
      finished: [original],
      handlers: { 'PUT /games/7': { ...original, prize: 12 } },
    });
    renderApp('/games');
    await tableRows();

    await userEvent.click(screen.getByRole('button', { name: 'Edit Kill The Fish' }));
    const form = within(await screen.findByRole('dialog', { name: 'Edit game' }));
    await form.findByRole('textbox', { name: 'Buy-in' });

    expect(form.getByRole('radio', { name: 'Tournament' })).toBeChecked();
    expect(form.getByRole('radio', { name: 'Finished' })).toBeChecked();
    expect(form.getByRole('combobox', { name: 'Room' })).toHaveValue('Winamax (EUR)');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('KO');
    expect(form.getByRole('textbox', { name: 'Name' })).toHaveValue('Kill The Fish');
    expect(form.getByLabelText(/^Date/)).toHaveValue('2026-01-19');
    expect(form.getByLabelText('Start time')).toHaveValue('21:30');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('5');
    expect(form.getByRole('textbox', { name: 'Entries (with re-entries)' })).toHaveValue('2');
    expect(form.getByRole('textbox', { name: 'Prize' })).toHaveValue('10');
    expect(form.getByRole('textbox', { name: 'Bounties' })).toHaveValue('3.5');
    expect(form.getByRole('textbox', { name: 'Notes' })).toHaveValue('final table');
    // Adding another only makes sense for new games.
    expect(form.queryByRole('button', { name: 'Save and add another' })).not.toBeInTheDocument();

    await userEvent.clear(form.getByRole('textbox', { name: 'Prize' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Prize' }), '12');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    expect(sent(calls, 'PUT')[0]).toMatchObject({
      path: '/games/7',
      body: {
        roomId: 1,
        gameType: 'TOURNAMENT',
        status: 'FINISHED',
        variantId: 10,
        name: 'Kill The Fish',
        playedAt: '21:30',
        buyIn: 5,
        entries: 2,
        prize: 12,
        bounty: 3.5,
        notes: 'final table',
      },
    });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(await screen.findByText('Game saved')).toBeInTheDocument();
    // Editing does not change what the next new game starts with.
    expect(localStorage.getItem('poker-bankroll.game-defaults')).toBeNull();
  });

  it('keeps the inactive room of a game when it is edited', async () => {
    stubGames({ finished: [game({ id: 7, room: { id: 3, name: 'Unibet' } })] });
    renderApp('/games');
    await tableRows();

    await userEvent.click(screen.getByRole('button', { name: 'Edit Tournament' }));
    const form = within(await screen.findByRole('dialog', { name: 'Edit game' }));

    expect(await form.findByRole('combobox', { name: 'Room' })).toHaveValue('Unibet (EUR)');
  });

  it('deletes a game after asking', async () => {
    const calls = stubGames({
      finished: [game({ id: 7, name: 'Kill The Fish' })],
      handlers: { 'DELETE /games/7': () => new Response(null, { status: 204 }) },
    });
    renderApp('/games');
    await tableRows();

    await userEvent.click(screen.getByRole('button', { name: 'Delete Kill The Fish' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Delete game' }));
    expect(
      dialog.getByText('Delete Kill The Fish of 19/01/2026 in Winamax? This cannot be undone.'),
    ).toBeInTheDocument();

    // Cancelling deletes nothing.
    await userEvent.click(dialog.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(sent(calls, 'DELETE')).toHaveLength(0);

    await userEvent.click(screen.getByRole('button', { name: 'Delete Kill The Fish' }));
    const again = within(await screen.findByRole('dialog', { name: 'Delete game' }));
    await userEvent.click(again.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(sent(calls, 'DELETE')).toHaveLength(1));
    expect(sent(calls, 'DELETE')[0]?.path).toBe('/games/7');
    expect(await screen.findByText('Game deleted')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('shows why a game could not be deleted', async () => {
    stubGames({
      finished: [game({ id: 7 })],
      handlers: {
        'DELETE /games/7': () => problem(404, 'NOT_FOUND', 'The game no longer exists.'),
      },
    });
    renderApp('/games');
    await tableRows();

    await userEvent.click(screen.getByRole('button', { name: 'Delete Tournament' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Delete game' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Delete' }));

    expect(await dialog.findByText('The game no longer exists.')).toBeInTheDocument();
  });
});

describe('Games in play', () => {
  const tournament = game({
    id: 1,
    name: 'Kill The Fish',
    status: 'IN_PLAY',
    variant: KO,
    buyIn: 5,
    entries: 2,
    invested: 10,
    net: -10,
  });
  const cash = game({
    id: 2,
    gameType: 'CASH',
    status: 'IN_PLAY',
    currencyCode: 'USD',
    room: { id: 2, name: 'PokerStars' },
    buyIn: 10,
    invested: 10,
    net: -10,
  });

  it('are listed on top with what is invested so far, apart from the filters', async () => {
    const calls = stubGames({ inPlay: [tournament, cash] });
    renderApp('/games?type=CASH&q=nothing');

    expect(await screen.findByRole('heading', { name: '2 games in play' })).toBeInTheDocument();
    const rows = inPlaySection().getAllByRole('row').slice(1);
    expect(rows[0]).toHaveTextContent('Kill The Fish');
    expect(rows[0]).toHaveTextContent('€5.00 ×2');
    expect(rows[0]).toHaveTextContent('€10.00');
    expect(rows[1]).toHaveTextContent('Cash');
    expect(rows[1]).toHaveTextContent('US$10.00');
    // A re-entry for tournaments, a rebuy for cash games.
    expect(within(rows[0]!).getByRole('button', { name: /re-entry/ })).toBeInTheDocument();
    expect(within(rows[0]!).queryByRole('button', { name: /rebuy/ })).not.toBeInTheDocument();
    expect(within(rows[1]!).getByRole('button', { name: /rebuy/ })).toBeInTheDocument();

    const inPlayQuery = calls.find((call) => call.query.get('status') === 'IN_PLAY');
    expect(Object.fromEntries(inPlayQuery!.query)).toEqual({
      status: 'IN_PLAY',
      size: '200',
      sort: 'playedOn,desc',
    });
  });

  it('are not shown when there is none', async () => {
    stubGames({ finished: [game({})] });
    renderApp('/games');

    await tableRows();

    expect(screen.queryByRole('heading', { name: /in play/ })).not.toBeInTheDocument();
  });

  it('finishes a tournament with its prize, bounties and ticket', async () => {
    const calls = stubGames({
      inPlay: [tournament],
      handlers: { 'POST /games/1/finish': { ...tournament, status: 'FINISHED', net: 22.5 } },
    });
    renderApp('/games');
    await userEvent.click(await screen.findByRole('button', { name: 'Finish Kill The Fish' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Finish game' }));

    expect(dialog.getByText('Kill The Fish: €10.00 invested.')).toBeInTheDocument();
    await userEvent.type(dialog.getByRole('textbox', { name: 'Prize' }), '30');
    await userEvent.type(dialog.getByRole('textbox', { name: 'Bounties' }), '2,5');
    await userEvent.click(dialog.getByRole('checkbox', { name: 'I won a ticket' }));
    // A ticket won needs its value.
    await userEvent.click(dialog.getByRole('button', { name: 'Finish' }));
    expect(await dialog.findByText('Enter the value of the ticket')).toBeInTheDocument();
    expect(sent(calls, 'POST')).toHaveLength(0);

    await userEvent.type(dialog.getByRole('textbox', { name: 'Ticket value' }), '20');
    await userEvent.type(dialog.getByRole('textbox', { name: 'Ticket description' }), 'Main Event');
    await userEvent.click(dialog.getByRole('button', { name: 'Finish' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]).toMatchObject({
      path: '/games/1/finish',
      body: { prize: 30, bounty: 2.5, ticketPrizeValue: 20, ticketDescription: 'Main Event' },
    });
    expect(await screen.findByText('Game finished')).toBeInTheDocument();
    expect(screen.getByText('Net: +€22.50')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('finishes a game with nothing won when the result is left empty', async () => {
    const calls = stubGames({
      inPlay: [tournament],
      handlers: { 'POST /games/1/finish': { ...tournament, status: 'FINISHED' } },
    });
    renderApp('/games');
    await userEvent.click(await screen.findByRole('button', { name: 'Finish Kill The Fish' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Finish game' }));

    await userEvent.click(dialog.getByRole('button', { name: 'Finish' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]?.body).toEqual({
      prize: null,
      bounty: null,
      ticketPrizeValue: null,
      ticketDescription: null,
    });
  });

  it('finishes a cash game with just what was taken from the table', async () => {
    const calls = stubGames({
      inPlay: [cash],
      handlers: { 'POST /games/2/finish': { ...cash, status: 'FINISHED', net: 4.2 } },
    });
    renderApp('/games');
    await userEvent.click(await screen.findByRole('button', { name: 'Finish Cash' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Finish game' }));

    expect(dialog.queryByRole('textbox', { name: 'Bounties' })).not.toBeInTheDocument();
    expect(dialog.queryByRole('checkbox', { name: 'I won a ticket' })).not.toBeInTheDocument();
    expect(dialog.queryByRole('textbox', { name: 'Prize' })).not.toBeInTheDocument();
    // A zero typed at the end must not lose the amount.
    await userEvent.type(dialog.getByRole('textbox', { name: 'Taken from the table' }), '14,20');
    await userEvent.click(dialog.getByRole('button', { name: 'Finish' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]).toMatchObject({
      path: '/games/2/finish',
      body: { prize: 14.2 },
    });
    expect(sent(calls, 'POST')[0]?.body).not.toHaveProperty('bounty');
    expect(await screen.findByText('Net: +US$4.20')).toBeInTheDocument();
  });

  it('adds a re-entry after asking', async () => {
    const calls = stubGames({
      inPlay: [tournament],
      handlers: { 'POST /games/1/re-entries': { ...tournament, entries: 3 } },
    });
    renderApp('/games');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Add a re-entry to Kill The Fish' }),
    );
    const dialog = within(await screen.findByRole('dialog', { name: 'Add re-entry' }));
    expect(dialog.getByText('Add one more entry of €5.00 to Kill The Fish?')).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(sent(calls, 'POST')).toHaveLength(0);

    await userEvent.click(screen.getByRole('button', { name: 'Add a re-entry to Kill The Fish' }));
    const again = within(await screen.findByRole('dialog', { name: 'Add re-entry' }));
    await userEvent.click(again.getByRole('button', { name: 'Re-entry' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]?.path).toBe('/games/1/re-entries');
    expect(await screen.findByText('Re-entry added')).toBeInTheDocument();
    expect(screen.getByText('3 entries')).toBeInTheDocument();
  });

  it('adds a rebuy with its amount', async () => {
    const calls = stubGames({
      inPlay: [cash],
      handlers: { 'POST /games/2/rebuys': { ...cash, buyIn: 17.5 } },
    });
    renderApp('/games');

    await userEvent.click(await screen.findByRole('button', { name: 'Add a rebuy to Cash' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Add rebuy' }));
    expect(dialog.getByText('You have brought US$10.00 to the table so far.')).toBeInTheDocument();

    await userEvent.click(dialog.getByRole('button', { name: 'Rebuy' }));
    expect(await dialog.findByText('Enter an amount greater than zero')).toBeInTheDocument();
    expect(sent(calls, 'POST')).toHaveLength(0);

    await userEvent.type(dialog.getByRole('textbox', { name: 'Amount of the rebuy' }), '7.50');
    await userEvent.click(dialog.getByRole('button', { name: 'Rebuy' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]).toMatchObject({
      path: '/games/2/rebuys',
      body: { amount: 7.5 },
    });
    expect(await screen.findByText('Brought to the table: US$17.50')).toBeInTheDocument();
  });

  it('shows why an action failed and keeps the dialog open', async () => {
    stubGames({
      inPlay: [tournament],
      handlers: {
        'POST /games/1/re-entries': () =>
          problem(409, 'GAME_NOT_IN_PLAY', 'The game is already finished.'),
      },
    });
    renderApp('/games');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Add a re-entry to Kill The Fish' }),
    );
    const dialog = within(await screen.findByRole('dialog', { name: 'Add re-entry' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Re-entry' }));

    expect(await dialog.findByText('The game is already finished.')).toBeInTheDocument();
  });

  it('can be edited and deleted too', async () => {
    stubGames({ inPlay: [tournament] });
    renderApp('/games');

    const section = within(await screen.findByRole('region', { name: /in play/ }));
    await userEvent.click(section.getByRole('button', { name: 'Edit Kill The Fish' }));
    const form = within(await screen.findByRole('dialog', { name: 'Edit game' }));
    expect(await form.findByRole('radio', { name: 'In play' })).toBeChecked();
    await userEvent.click(form.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await userEvent.click(section.getByRole('button', { name: 'Delete Kill The Fish' }));
    expect(await screen.findByRole('dialog', { name: 'Delete game' })).toBeInTheDocument();
  });
});
