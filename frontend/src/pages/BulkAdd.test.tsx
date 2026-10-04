import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type { Game, GameBatchRequest, GameName, GameRequest, Room } from '../api/types';
import { todayIso } from '../games/gameDefaults';
import { game, page, room, ROOMS, VARIANTS } from '../test/fixtures';
import { onANarrowScreen } from '../test/narrowScreen';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

/** A finished Expresso of another day, with everything a duplicate must not copy. */
const EXPRESSO = game({
  id: 7,
  playedOn: '2026-01-19',
  playedAt: '21:30:00',
  gameType: 'SIT_AND_GO',
  modality: 'PLO',
  variant: { id: 20, code: 'EXPRESSO', name: null },
  name: 'Expresso Nitro',
  buyIn: 2,
  entries: 2,
  prize: 10,
  bounty: 1,
  paidWithTicket: true,
  notes: 'x5',
  net: 7,
});

/** The API: creating games echoes them back as the backend would. */
function stubGamesApi(
  options: { rooms?: Room[]; games?: Game[]; names?: GameName[]; onBatch?: unknown } = {},
) {
  return stubApi({
    'GET /rooms': options.rooms ?? ROOMS,
    'GET /variants': VARIANTS,
    'GET /games': (call: ApiCall) =>
      page(call.query.get('status') === 'IN_PLAY' ? [] : (options.games ?? [])),
    'GET /games/names': options.names ?? [],
    'POST /games': (call: ApiCall) => echo(call.body as GameRequest, 100),
    'POST /games/batch':
      options.onBatch ??
      ((call: ApiCall) => ({
        games: (call.body as GameBatchRequest).games.map((request, index) =>
          echo(request, 200 + index),
        ),
      })),
  });
}

function echo(request: GameRequest, id: number): Game {
  const won = (request.prize ?? 0) + (request.bounty ?? 0);
  return game({
    id,
    gameType: request.gameType,
    room: { id: request.roomId, name: 'Winamax' },
    status: request.status ?? 'IN_PLAY',
    buyIn: request.buyIn,
    net: request.status === 'FINISHED' ? won - request.buyIn : -request.buyIn,
  });
}

function posted<T>(calls: ApiCall[], path: string): T[] {
  return calls
    .filter((call) => call.method === 'POST' && call.path === path)
    .map((call) => call.body as T);
}

/** Options live in a popover that jsdom never sees as shown, hence `hidden: true`. */
async function choose(form: ReturnType<typeof within>, label: string, option: string) {
  await userEvent.click(form.getByRole('combobox', { name: label }));
  await userEvent.click(await screen.findByRole('option', { name: option, hidden: true }));
}

async function openBulkAdd() {
  await userEvent.click(await screen.findByRole('button', { name: 'Add several' }));
  const dialog = await screen.findByRole('dialog', { name: 'Add several games' });
  await within(dialog).findByRole('textbox', { name: 'Buy-in' });
  return within(dialog);
}

async function setCount(form: ReturnType<typeof within>, count: number) {
  const input = form.getByRole('textbox', { name: 'Number of games' });
  await userEvent.clear(input);
  await userEvent.type(input, String(count));
}

describe('Duplicate a game', () => {
  it('opens a new game like it, today and without its result', async () => {
    const calls = stubGamesApi({ games: [EXPRESSO] });
    renderApp('/games');

    await userEvent.click(await screen.findByRole('button', { name: 'Duplicate Expresso Nitro' }));
    const form = within(await screen.findByRole('dialog', { name: 'Duplicate game' }));

    expect(form.getByLabelText(/^Date/)).toHaveValue(todayIso());
    expect(form.getByLabelText('Start time')).toHaveValue('');
    expect(form.getByRole('radio', { name: 'Sit & Go / Spin' })).toBeChecked();
    expect(form.getByRole('combobox', { name: 'Room' })).toHaveValue('Winamax (EUR)');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('Expresso');
    expect(form.getByRole('radio', { name: 'Omaha' })).toBeChecked();
    expect(form.getByRole('combobox', { name: 'Name' })).toHaveValue('Expresso Nitro');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('2');
    // A new game gets the status of the last one recorded: in play when there is none.
    expect(form.getByRole('radio', { name: 'In play' })).toBeChecked();

    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(posted(calls, '/games')).toHaveLength(1));
    expect(posted(calls, '/games')[0]).toEqual({
      playedOn: todayIso(),
      playedAt: null,
      roomId: 1,
      gameType: 'SIT_AND_GO',
      modality: 'PLO',
      variantId: 20,
      status: 'IN_PLAY',
      name: 'Expresso Nitro',
      buyIn: 2,
      entries: 1,
      paidWithTicket: false,
      prize: null,
      bounty: null,
      ticketPrizeValue: null,
      ticketDescription: null,
      notes: null,
    });
    expect(await screen.findByText('Game saved')).toBeInTheDocument();
  });

  it('leaves the room and the variant to choose when they no longer take games', async () => {
    const variants = VARIANTS.map((one) => (one.id === 20 ? { ...one, active: false } : one));
    stubApi({
      'GET /rooms': ROOMS,
      'GET /variants': variants,
      'GET /games': (call: ApiCall) =>
        page(
          call.query.get('status') === 'IN_PLAY'
            ? []
            : [{ ...EXPRESSO, room: { id: 3, name: 'Unibet' } }],
        ),
    });
    renderApp('/games');

    await userEvent.click(await screen.findByRole('button', { name: 'Duplicate Expresso Nitro' }));
    const form = within(await screen.findByRole('dialog', { name: 'Duplicate game' }));

    expect(form.getByRole('combobox', { name: 'Room' })).toHaveValue('');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('');
    expect(form.getByRole('combobox', { name: 'Name' })).toHaveValue('Expresso Nitro');
  });

  it('what it copied counts as chosen: a name picked afterwards does not replace it', async () => {
    const other = {
      name: 'Expresso Nitro Turbo',
      games: 4,
      gameType: 'SIT_AND_GO' as const,
      modality: 'NLHE' as const,
      buyIn: 5,
      currencyCode: 'EUR',
      variant: { id: 21, code: null, name: 'Hyper Turbo' },
    };
    const calls = stubGamesApi({ games: [EXPRESSO], names: [other] });
    renderApp('/games');

    await userEvent.click(await screen.findByRole('button', { name: 'Duplicate Expresso Nitro' }));
    const form = within(await screen.findByRole('dialog', { name: 'Duplicate game' }));
    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), ' Turbo');
    await userEvent.click(
      await screen.findByRole('option', { name: /Expresso Nitro Turbo/, hidden: true }),
    );

    expect(form.getByRole('combobox', { name: 'Name' })).toHaveValue('Expresso Nitro Turbo');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('2');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('Expresso');
    expect(form.getByRole('radio', { name: 'Omaha' })).toBeChecked();

    // In a room of another currency, the copied buy-in is not an amount of that currency.
    await choose(form, 'Room', 'PokerStars (USD)');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));
    expect(posted(calls, '/games')).toHaveLength(0);
  });

  it('is offered for the games in play too', async () => {
    stubApi({
      'GET /rooms': ROOMS,
      'GET /variants': VARIANTS,
      'GET /games': (call: ApiCall) =>
        page(call.query.get('status') === 'IN_PLAY' ? [{ ...EXPRESSO, status: 'IN_PLAY' }] : []),
    });
    renderApp('/games');

    await userEvent.click(await screen.findByRole('button', { name: 'Duplicate Expresso Nitro' }));

    expect(await screen.findByRole('dialog', { name: 'Duplicate game' })).toBeInTheDocument();
  });
});

describe('Add several games', () => {
  it('records finished games with the result of each one, an empty prize being nothing', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openBulkAdd();

    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.click(form.getByRole('radio', { name: 'Sit & Go / Spin' }));
    await choose(form, 'Variant', 'Expresso');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '2');
    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), 'Expresso');
    await setCount(form, 3);
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));

    // Sit & Go: no bounties.
    expect(form.queryByRole('textbox', { name: 'Bounties of game 1' })).not.toBeInTheDocument();
    expect(form.queryByRole('textbox', { name: 'Prize of game 4' })).not.toBeInTheDocument();
    await userEvent.type(form.getByRole('textbox', { name: 'Prize of game 1' }), '10');
    await userEvent.type(form.getByRole('textbox', { name: 'Prize of game 3' }), '4.5');
    await userEvent.type(form.getByRole('textbox', { name: 'Notes of game 3' }), 'x3');

    const totals = within(form.getByRole('group', { name: 'Totals' }));
    expect(totals.getByText('€6.00')).toBeInTheDocument();
    expect(totals.getByText('€14.50')).toBeInTheDocument();
    expect(totals.getByText('+€8.50')).toBeInTheDocument();

    await userEvent.click(form.getByRole('button', { name: 'Add 3 games' }));

    await waitFor(() => expect(posted(calls, '/games/batch')).toHaveLength(1));
    const { games } = posted<GameBatchRequest>(calls, '/games/batch')[0]!;
    const common = {
      playedOn: todayIso(),
      playedAt: null,
      roomId: 1,
      gameType: 'SIT_AND_GO',
      modality: 'NLHE',
      variantId: 20,
      status: 'FINISHED',
      name: 'Expresso',
      buyIn: 2,
      entries: 1,
      paidWithTicket: false,
      bounty: null,
      ticketPrizeValue: null,
      ticketDescription: null,
    };
    expect(games).toEqual([
      { ...common, prize: 10, notes: null },
      { ...common, prize: 0, notes: null },
      { ...common, prize: 4.5, notes: 'x3' },
    ]);
    expect(await screen.findByText('3 games added')).toBeInTheDocument();
    expect(screen.getByText('Net: +€8.50')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('records games in play without any result', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openBulkAdd();

    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '5');
    expect(form.getByRole('radio', { name: 'In play' })).toBeChecked();
    expect(form.queryByRole('textbox', { name: 'Prize of game 1' })).not.toBeInTheDocument();
    await userEvent.type(form.getByRole('textbox', { name: 'Notes of game 2' }), 'table 2');
    await userEvent.click(form.getByRole('button', { name: 'Add 2 games' }));

    await waitFor(() => expect(posted(calls, '/games/batch')).toHaveLength(1));
    const { games } = posted<GameBatchRequest>(calls, '/games/batch')[0]!;
    expect(games).toHaveLength(2);
    expect(
      games.map((one) => [one.gameType, one.status, one.prize, one.bounty, one.notes]),
    ).toEqual([
      ['TOURNAMENT', 'IN_PLAY', null, null, null],
      ['TOURNAMENT', 'IN_PLAY', null, null, 'table 2'],
    ]);
    expect(await screen.findByText('2 games added')).toBeInTheDocument();
    expect(
      screen.getByText('They are in play: finish each one when you know its result.'),
    ).toBeInTheDocument();
  });

  it('tournaments take bounties too', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openBulkAdd();

    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '5');
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Bounties of game 2' }), '2.5');
    await userEvent.click(form.getByRole('button', { name: 'Add 2 games' }));

    await waitFor(() => expect(posted(calls, '/games/batch')).toHaveLength(1));
    const { games } = posted<GameBatchRequest>(calls, '/games/batch')[0]!;
    expect(games.map((one) => [one.prize, one.bounty])).toEqual([
      [0, 0],
      [0, 2.5],
    ]);
  });

  it('checks what the games share and how many they are before sending', async () => {
    const calls = stubGamesApi({ rooms: [room({ id: 1, name: 'Winamax' }), room({ id: 2 })] });
    renderApp('/games');
    const form = await openBulkAdd();

    await userEvent.clear(form.getByRole('textbox', { name: 'Number of games' }));
    await userEvent.click(form.getByRole('button', { name: 'Add 2 games' }));

    expect(form.getByText('From 2 to 50')).toBeInTheDocument();
    expect(form.getAllByText('Required')).toHaveLength(2);
    expect(posted(calls, '/games/batch')).toHaveLength(0);
  });

  it('shows an error of the backend on the row of its game', async () => {
    stubGamesApi({
      onBatch: problem(400, 'VALIDATION_FAILED', 'Invalid request', [
        { field: 'games[2].prize', code: 'Digits', message: 'At most 2 decimals' },
      ]),
    });
    renderApp('/games');
    const form = await openBulkAdd();

    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '5');
    await setCount(form, 3);
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));
    await userEvent.click(form.getByRole('button', { name: 'Add 3 games' }));

    expect(await form.findByText('At most 2 decimals')).toBeInTheDocument();
    expect(form.getByRole('textbox', { name: 'Prize of game 3' })).toBeInvalid();
    expect(form.getByRole('textbox', { name: 'Prize of game 1' })).toBeValid();
    expect(form.queryByText('The games were not saved')).not.toBeInTheDocument();
  });

  it('says which game a business error comes from', async () => {
    stubGamesApi({
      onBatch: () =>
        new Response(
          JSON.stringify({
            status: 409,
            code: 'VARIANT_INACTIVE',
            detail: 'The variant is inactive',
            index: 1,
          }),
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
    });
    renderApp('/games');
    const form = await openBulkAdd();

    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '5');
    await userEvent.click(form.getByRole('button', { name: 'Add 2 games' }));

    expect(await form.findByText('The games were not saved')).toBeInTheDocument();
    expect(form.getByText('Game 2: The variant is inactive')).toBeInTheDocument();
  });
});

describe('On a phone', () => {
  onANarrowScreen();

  it('adds several games without a table', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openBulkAdd();

    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '5');
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));

    expect(form.queryByRole('table')).not.toBeInTheDocument();
    const rows = within(form.getByRole('list', { name: 'Games to add' })).getAllByRole('listitem');
    expect(rows).toHaveLength(2);
    expect(within(rows[0]!).getByRole('textbox', { name: 'Prize of game 1' })).toBeInTheDocument();
    expect(
      within(rows[0]!).getByRole('textbox', { name: 'Bounties of game 1' }),
    ).toBeInTheDocument();
    expect(within(rows[0]!).getByRole('textbox', { name: 'Notes of game 1' })).toBeInTheDocument();

    await userEvent.type(form.getByRole('textbox', { name: 'Prize of game 2' }), '12');
    await userEvent.click(form.getByRole('button', { name: 'Add 2 games' }));
    await waitFor(() => expect(posted(calls, '/games/batch')).toHaveLength(1));
  });

  it('duplicates a game from the menu of its card', async () => {
    stubGamesApi({ games: [EXPRESSO] });
    renderApp('/games');

    await userEvent.click(
      await screen.findByRole('button', { name: /^Actions for Expresso Nitro/ }),
    );
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Duplicate', hidden: true }));

    const form = within(await screen.findByRole('dialog', { name: 'Duplicate game' }));
    expect(form.getByRole('combobox', { name: 'Name' })).toHaveValue('Expresso Nitro');
  });
});
