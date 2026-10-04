import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type { Game, GameRequest, Room } from '../api/types';
import { todayIso } from '../games/gameDefaults';
import { game, page, room, ROOMS, TAGS, VARIANTS } from '../test/fixtures';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

/** The API with the given rooms; saving a game echoes it back as the backend would. */
function stubGamesApi(options: { rooms?: Room[]; games?: Game[]; onCreate?: unknown } = {}) {
  return stubApi({
    'GET /rooms': options.rooms ?? ROOMS,
    'GET /variants': VARIANTS,
    'GET /tags': TAGS,
    'GET /games': page(options.games ?? []),
    'POST /games':
      options.onCreate ??
      ((call: ApiCall) => {
        const request = call.body as GameRequest;
        const finished = request.status === 'FINISHED';
        return game({
          gameType: request.gameType,
          room: { id: request.roomId, name: 'Winamax' },
          status: request.status ?? 'IN_PLAY',
          buyIn: request.buyIn,
          net: finished
            ? (request.prize ?? 0) + (request.bounty ?? 0) - request.buyIn
            : -request.buyIn,
        });
      }),
  });
}

function created(calls: ApiCall[]): GameRequest[] {
  return calls
    .filter((call) => call.method === 'POST' && call.path === '/games')
    .map((call) => call.body as GameRequest);
}

async function openForm() {
  await userEvent.click(await screen.findByRole('button', { name: 'Add game' }));
  const dialog = await screen.findByRole('dialog', { name: 'Add game' });
  await within(dialog).findByRole('textbox', { name: 'Buy-in' });
  return within(dialog);
}

/** The options a select offers, read from the list it controls. */
async function optionsOf(form: ReturnType<typeof within>, label: string) {
  const select = form.getByRole('combobox', { name: label });
  await userEvent.click(select);
  const list = await waitFor(() => {
    const element = document.getElementById(select.getAttribute('aria-controls') ?? '');
    expect(element).not.toBeNull();
    return element!;
  });
  return within(list)
    .getAllByRole('option', { hidden: true })
    .map((option) => option.textContent);
}

/** Options live in a popover that jsdom never sees as shown, hence `hidden: true`. */
async function choose(form: ReturnType<typeof within>, label: string, option: string) {
  await userEvent.click(form.getByRole('combobox', { name: label }));
  await userEvent.click(await screen.findByRole('option', { name: option, hidden: true }));
}

describe('Add game form', () => {
  it('starts with today, a tournament in play, and only offers active rooms and variants', async () => {
    stubGamesApi();
    renderApp('/games');
    const form = await openForm();

    expect(form.getByLabelText(/^Date/)).toHaveValue(todayIso());
    expect(form.getByRole('radio', { name: 'Tournament' })).toBeChecked();
    expect(form.getByRole('radio', { name: 'In play' })).toBeChecked();
    expect(form.getByRole('radio', { name: "Hold'em" })).toBeChecked();
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveFocus();
    // No result while it is in play.
    expect(form.queryByRole('textbox', { name: 'Prize' })).not.toBeInTheDocument();

    expect(await optionsOf(form, 'Room')).toEqual(['PokerStars (USD)', 'Winamax (EUR)']);

    expect(await optionsOf(form, 'Variant')).toEqual(['KO']);
  });

  it('records a game in play with just the room and the buy-in', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openForm();

    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '2.50');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(created(calls)).toHaveLength(1));
    expect(created(calls)[0]).toEqual({
      playedOn: todayIso(),
      playedAt: null,
      roomId: 1,
      gameType: 'TOURNAMENT',
      modality: 'NLHE',
      variantId: null,
      status: 'IN_PLAY',
      name: null,
      buyIn: 2.5,
      entries: 1,
      paidWithTicket: false,
      prize: null,
      bounty: null,
      ticketPrizeValue: null,
      ticketDescription: null,
      notes: null,
      tags: [],
    });
    expect(await screen.findByText('Game saved')).toBeInTheDocument();
    expect(
      screen.getByText('It is in play: finish it when you know the result.'),
    ).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('records a finished tournament with every field', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openForm();

    await choose(form, 'Room', 'Winamax (EUR)');
    await choose(form, 'Variant', 'KO');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '5');
    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), ' Kill The Fish ');
    await userEvent.type(form.getByLabelText('Start time'), '21:30');
    await userEvent.clear(form.getByRole('textbox', { name: 'Entries (with re-entries)' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Entries (with re-entries)' }), '2');
    await userEvent.click(form.getByRole('radio', { name: 'Omaha' }));
    await userEvent.click(form.getByRole('checkbox', { name: 'I paid the entry with a ticket' }));
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Prize' }), '30.00');
    await userEvent.type(form.getByRole('textbox', { name: 'Bounties' }), '2.75');
    await userEvent.click(form.getByRole('checkbox', { name: 'I won a ticket' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Ticket value' }), '20');
    await userEvent.type(form.getByRole('textbox', { name: 'Ticket description' }), 'Main Event');
    await userEvent.type(form.getByRole('textbox', { name: 'Notes' }), 'final table');
    // The existing tags are suggested; a new one is just typed.
    await userEvent.click(form.getByLabelText('Tags'));
    expect(
      (await screen.findAllByRole('option', { hidden: true }))
        .map((option) => option.textContent)
        .filter((text) => text === 'Challenge' || text === 'Friends'),
    ).toEqual(expect.arrayContaining(['Challenge', 'Friends']));
    await userEvent.type(form.getByLabelText('Tags'), 'Series{Enter}Challenge{Enter}');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(created(calls)).toHaveLength(1));
    expect(created(calls)[0]).toEqual({
      playedOn: todayIso(),
      playedAt: '21:30',
      roomId: 1,
      gameType: 'TOURNAMENT',
      modality: 'PLO',
      variantId: 10,
      status: 'FINISHED',
      name: 'Kill The Fish',
      buyIn: 5,
      entries: 2,
      paidWithTicket: true,
      prize: 30,
      bounty: 2.75,
      ticketPrizeValue: 20,
      ticketDescription: 'Main Event',
      notes: 'final table',
      tags: ['Series', 'Challenge'],
    });
    expect(await screen.findByText('Net: +€27.75')).toBeInTheDocument();
  });

  it('a finished game with nothing won is sent as finished', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openForm();

    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '5');
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(created(calls)).toHaveLength(1));
    expect(created(calls)[0]).toMatchObject({ status: 'FINISHED', prize: null, bounty: null });
    expect(await screen.findByText('Net: -€5.00')).toBeInTheDocument();
  });

  it('adapts to a cash game: one sitting, no entries, bounties or tickets', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openForm();

    await userEvent.click(form.getByRole('checkbox', { name: 'I paid the entry with a ticket' }));
    await userEvent.click(form.getByRole('radio', { name: 'Cash' }));
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));

    expect(
      form.queryByRole('textbox', { name: 'Entries (with re-entries)' }),
    ).not.toBeInTheDocument();
    expect(form.queryByRole('combobox', { name: 'Variant' })).not.toBeInTheDocument();
    expect(
      form.queryByRole('checkbox', { name: 'I paid the entry with a ticket' }),
    ).not.toBeInTheDocument();
    expect(form.queryByRole('textbox', { name: 'Bounties' })).not.toBeInTheDocument();
    expect(form.queryByRole('checkbox', { name: 'I won a ticket' })).not.toBeInTheDocument();

    await choose(form, 'Room', 'PokerStars (USD)');
    await userEvent.type(form.getByRole('textbox', { name: 'Brought to the table' }), '10');
    await userEvent.type(form.getByRole('textbox', { name: 'Taken from the table' }), '14,20');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(created(calls)).toHaveLength(1));
    expect(created(calls)[0]).toMatchObject({
      gameType: 'CASH',
      roomId: 2,
      buyIn: 10,
      prize: 14.2,
      entries: null,
      paidWithTicket: false,
      bounty: null,
      ticketPrizeValue: null,
      variantId: null,
    });
  });

  it('offers the variants of the chosen type, user-defined ones by their name', async () => {
    stubGamesApi();
    renderApp('/games');
    const form = await openForm();

    await choose(form, 'Variant', 'KO');
    await userEvent.click(form.getByRole('radio', { name: 'Sit & Go / Spin' }));

    // The variant of the other type is dropped.
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('');
    expect(await optionsOf(form, 'Variant')).toEqual(['Expresso', 'Hyper Turbo']);
  });

  it('asks for the room and the buy-in before saving', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openForm();

    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    expect(await form.findAllByText('Required')).toHaveLength(2);
    expect(form.getByRole('combobox', { name: 'Room' })).toBeInvalid();
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toBeInvalid();
    expect(created(calls)).toHaveLength(0);
  });

  it('asks for the value of a ticket won', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openForm();

    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '2');
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));
    await userEvent.click(form.getByRole('checkbox', { name: 'I won a ticket' }));
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    expect(await form.findByText('Enter the value of the ticket')).toBeInTheDocument();
    expect(created(calls)).toHaveLength(0);
  });

  it('shows the errors of the backend next to their fields, or on top', async () => {
    let response = problem(400, 'VALIDATION_FAILED', 'The request contains invalid data.', [
      { field: 'buyIn', code: 'Digits', message: 'Too many digits' },
    ]);
    stubGamesApi({ onCreate: () => response });
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '5');

    await userEvent.click(form.getByRole('button', { name: 'Save' }));
    expect(await form.findByText('Too many digits')).toBeInTheDocument();
    expect(form.queryByText('The game was not saved')).not.toBeInTheDocument();

    response = problem(409, 'ROOM_INACTIVE', 'The room Winamax is inactive.');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));
    expect(await form.findByText('The game was not saved')).toBeInTheDocument();
    expect(form.getByText('The room Winamax is inactive.')).toBeInTheDocument();
    // Nothing was saved: the form stays open.
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('saves and goes on with another game like the one just saved', async () => {
    const calls = stubGamesApi();
    renderApp('/games');
    const form = await openForm();

    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.click(form.getByRole('radio', { name: 'Sit & Go / Spin' }));
    await choose(form, 'Variant', 'Expresso');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '2');
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Prize' }), '4');
    await userEvent.click(form.getByRole('button', { name: 'Save and add another' }));

    await waitFor(() => expect(created(calls)).toHaveLength(1));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    // What and where are kept; the result is cleared and the buy-in is ready to be changed.
    expect(form.getByRole('radio', { name: 'Sit & Go / Spin' })).toBeChecked();
    expect(form.getByRole('combobox', { name: 'Room' })).toHaveValue('Winamax (EUR)');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('Expresso');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('2');
    expect(form.getByRole('textbox', { name: 'Prize' })).toHaveValue('');
    await waitFor(() => expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveFocus());

    // Ctrl + Enter does the same from the keyboard.
    await userEvent.keyboard('{Control>}{Enter}{/Control}');
    await waitFor(() => expect(created(calls)).toHaveLength(2));
    expect(created(calls)[1]).toMatchObject({
      gameType: 'SIT_AND_GO',
      variantId: 20,
      buyIn: 2,
      prize: null,
    });
  });

  it('saves once, however many times Enter is pressed while it is being saved', async () => {
    let finish: (response: unknown) => void = () => {};
    const pending = new Promise((resolve) => {
      finish = resolve;
    });
    const calls = stubGamesApi({ onCreate: () => pending });
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '5');

    await userEvent.keyboard('{Enter}{Enter}{Control>}{Enter}{/Control}');
    expect(created(calls)).toHaveLength(1);

    finish(game({}));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(created(calls)).toHaveLength(1);
  });

  it('remembers the room, type and status of the last game for the next time', async () => {
    stubGamesApi();
    const first = renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.click(form.getByRole('radio', { name: 'Sit & Go / Spin' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '1');
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));
    await userEvent.click(form.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    first.unmount();

    renderApp('/games');
    const again = await openForm();

    expect(again.getByRole('combobox', { name: 'Room' })).toHaveValue('Winamax (EUR)');
    expect(again.getByRole('radio', { name: 'Sit & Go / Spin' })).toBeChecked();
    expect(again.getByRole('radio', { name: 'Finished' })).toBeChecked();
    expect(again.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('');
  });

  it('explains that a room is needed when none is active', async () => {
    stubGamesApi({ rooms: [room({ id: 3, name: 'Unibet', active: false })] });
    renderApp('/games');

    await userEvent.click(await screen.findByRole('button', { name: 'Add game' }));

    expect(await screen.findByText('There are no active rooms')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
  });

  it('is translated', async () => {
    stubGamesApi();
    renderApp('/games');
    await userEvent.click(await screen.findByText('ES'));

    await userEvent.click(await screen.findByRole('button', { name: 'Añadir partida' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Añadir partida' }));

    expect(await dialog.findByRole('radio', { name: 'Torneo' })).toBeChecked();
    expect(dialog.getByRole('radio', { name: 'En juego' })).toBeChecked();
    expect(dialog.getByRole('button', { name: 'Guardar y añadir otra' })).toBeInTheDocument();
  });
});
