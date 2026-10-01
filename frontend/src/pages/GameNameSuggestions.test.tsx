import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type { Game, GameName, GameRequest } from '../api/types';
import { game, page, ROOMS, VARIANTS } from '../test/fixtures';
import { renderApp, stubApi, type ApiCall } from '../test/renderApp';

const KILL_THE_FISH = suggestion({
  name: 'Kill The Fish',
  games: 12,
  buyIn: 5,
  modality: 'PLO',
  variant: { id: 10, code: 'KO', name: null },
});
const FISH_AND_CHIPS = suggestion({ name: 'Fish & Chips', games: 3, buyIn: 2.5 });

function suggestion(overrides: Partial<GameName>): GameName {
  return {
    name: 'Kill The Fish',
    games: 1,
    gameType: 'TOURNAMENT',
    modality: 'NLHE',
    buyIn: 5,
    currencyCode: 'EUR',
    variant: null,
    ...overrides,
  };
}

/** The API suggesting the given names for any text of two characters or more. */
function stubNamesApi(names: GameName[], games: Game[] = []) {
  return stubApi({
    'GET /rooms': ROOMS,
    'GET /variants': VARIANTS,
    'GET /games': (call: ApiCall) => page(call.query.get('status') === 'IN_PLAY' ? [] : games),
    'GET /games/names': names,
    'POST /games': (call: ApiCall) => game({ buyIn: (call.body as GameRequest).buyIn }),
    'PUT /games/7': (call: ApiCall) => game({ id: 7, name: (call.body as GameRequest).name }),
  });
}

function nameSearches(calls: ApiCall[]) {
  return calls
    .filter((call) => call.path === '/games/names')
    .map((call) => Object.fromEntries(call.query));
}

function saved(calls: ApiCall[], method: string): GameRequest[] {
  return calls
    .filter((call) => call.method === method && call.path.startsWith('/games'))
    .map((call) => call.body as GameRequest);
}

async function openForm() {
  await userEvent.click(await screen.findByRole('button', { name: 'Add game' }));
  const dialog = await screen.findByRole('dialog', { name: 'Add game' });
  await within(dialog).findByRole('textbox', { name: 'Buy-in' });
  return within(dialog);
}

/** Options live in a popover that jsdom never sees as shown, hence `hidden: true`. */
async function choose(form: ReturnType<typeof within>, label: string, option: string) {
  await userEvent.click(form.getByRole('combobox', { name: label }));
  await userEvent.click(await screen.findByRole('option', { name: option, hidden: true }));
}

function suggested(name: RegExp) {
  return screen.findByRole('option', { name, hidden: true });
}

/** Longer than the pause after which names are asked for. */
function typingPause() {
  return new Promise((resolve) => setTimeout(resolve, 400));
}

describe('Name of a game', () => {
  it('suggests names of the type of the form from the second character', async () => {
    const calls = stubNamesApi([KILL_THE_FISH, FISH_AND_CHIPS]);
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');
    const name = form.getByRole('combobox', { name: 'Name' });

    await userEvent.type(name, 'f');
    await typingPause();
    expect(nameSearches(calls)).toEqual([]);
    expect(screen.queryByRole('option', { name: /Fish/, hidden: true })).not.toBeInTheDocument();

    await userEvent.type(name, 'i');
    await waitFor(() => expect(nameSearches(calls)).toEqual([{ q: 'fi', gameType: 'TOURNAMENT' }]));

    // Each with the buy-in, in its own currency, and the variant of its last game.
    expect(await suggested(/Kill The Fish/)).toHaveTextContent('€5.00 · KO');
    const chips = await suggested(/Fish & Chips/);
    expect(chips).toHaveTextContent('€2.50');
    expect(chips).not.toHaveTextContent('·');
  });

  it('asks once typing pauses, for the type chosen in the form', async () => {
    const calls = stubNamesApi([]);
    renderApp('/games');
    const form = await openForm();
    await userEvent.click(form.getByRole('radio', { name: 'Sit & Go / Spin' }));

    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), ' Expresso');

    await waitFor(() =>
      expect(nameSearches(calls)).toEqual([{ q: 'Expresso', gameType: 'SIT_AND_GO' }]),
    );
  });

  it('picking a name fills in the buy-in, variant and modality of its last game', async () => {
    const calls = stubNamesApi([KILL_THE_FISH]);
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');

    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), 'kill');
    await userEvent.click(await suggested(/Kill The Fish/));

    expect(form.getByRole('combobox', { name: 'Name' })).toHaveValue('Kill The Fish');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('5');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('KO');
    expect(form.getByRole('radio', { name: 'Omaha' })).toBeChecked();

    await userEvent.click(form.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(saved(calls, 'POST')).toHaveLength(1));
    expect(saved(calls, 'POST')[0]).toMatchObject({
      name: 'Kill The Fish',
      buyIn: 5,
      variantId: 10,
      modality: 'PLO',
    });
  });

  it('does not take the buy-in of a game in another currency', async () => {
    const calls = stubNamesApi([suggestion({ name: 'Big Bang', buyIn: 50, currencyCode: 'USD' })]);
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');

    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), 'big');
    // The amount is shown as what it was: dollars.
    const bigBang = await suggested(/Big Bang/);
    expect(bigBang).toHaveTextContent('$50.00');
    await userEvent.click(bigBang);

    expect(form.getByRole('combobox', { name: 'Name' })).toHaveValue('Big Bang');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('');
    expect(saved(calls, 'POST')).toHaveLength(0);
  });

  it('drops a suggested buy-in when the room changes to another currency', async () => {
    stubNamesApi([KILL_THE_FISH]);
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), 'kill');
    await userEvent.click(await suggested(/Kill The Fish/));
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('5');

    await choose(form, 'Room', 'PokerStars (USD)');

    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('');
    expect(form.getByRole('combobox', { name: 'Name' })).toHaveValue('Kill The Fish');
  });

  it('picking another name does not keep what the first one filled in', async () => {
    stubNamesApi([
      KILL_THE_FISH,
      suggestion({
        name: 'Killer Dollars',
        buyIn: 50,
        currencyCode: 'USD',
        // Inactive: not offered any more.
        variant: { id: 11, code: 'SPACE_KO', name: null },
      }),
    ]);
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');
    const name = form.getByRole('combobox', { name: 'Name' });
    await userEvent.type(name, 'kill');
    await userEvent.click(await suggested(/Kill The Fish/));
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('5');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('KO');

    await userEvent.clear(name);
    await userEvent.type(name, 'kill');
    await userEvent.click(await suggested(/Killer Dollars/));

    expect(name).toHaveValue('Killer Dollars');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('');
  });

  it('keeps a buy-in typed by hand when the room changes', async () => {
    stubNamesApi([KILL_THE_FISH]);
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), 'kill');
    await userEvent.click(await suggested(/Kill The Fish/));
    await userEvent.clear(form.getByRole('textbox', { name: 'Buy-in' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '7');

    await choose(form, 'Room', 'PokerStars (USD)');

    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('7');
  });

  it('leaves alone what was typed or chosen before picking a name', async () => {
    stubNamesApi([KILL_THE_FISH]);
    renderApp('/games');
    const form = await openForm();
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '11');
    await userEvent.click(form.getByRole('radio', { name: 'Omaha' }));
    await userEvent.click(form.getByRole('radio', { name: "Hold'em" }));

    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), 'kill');
    await userEvent.click(await suggested(/Kill The Fish/));

    expect(form.getByRole('combobox', { name: 'Name' })).toHaveValue('Kill The Fish');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('11');
    expect(form.getByRole('radio', { name: "Hold'em" })).toBeChecked();
    // Untouched, so it is filled in.
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('KO');
  });

  it('keeps the variant chosen by hand, and does not choose one that is not offered', async () => {
    const spaceKo = suggestion({
      name: 'Space Race',
      variant: { id: 11, code: 'SPACE_KO', name: null },
    });
    stubNamesApi([suggestion({ name: 'Plain Race' }), spaceKo]);
    renderApp('/games');
    const form = await openForm();
    const name = form.getByRole('combobox', { name: 'Name' });

    // The variant of its last game is inactive now.
    await userEvent.type(name, 'race');
    await userEvent.click(await suggested(/Space Race/));
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('');

    await choose(form, 'Variant', 'KO');
    await userEvent.clear(name);
    await userEvent.type(name, 'race');
    await userEvent.click(await suggested(/Plain Race/));
    expect(name).toHaveValue('Plain Race');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('KO');
  });

  it('picks a name with the arrows and Enter without saving; Enter then saves', async () => {
    const calls = stubNamesApi([KILL_THE_FISH, FISH_AND_CHIPS]);
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');

    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), 'fish');
    await suggested(/Fish & Chips/);
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{Enter}');

    expect(form.getByRole('combobox', { name: 'Name' })).toHaveValue('Fish & Chips');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('2.5');
    expect(saved(calls, 'POST')).toHaveLength(0);
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    // The list is closed: Enter saves, as in any other field.
    await userEvent.keyboard('{Enter}');
    await waitFor(() => expect(saved(calls, 'POST')).toHaveLength(1));
    expect(saved(calls, 'POST')[0]).toMatchObject({ name: 'Fish & Chips', buyIn: 2.5 });
  });

  it('Ctrl + Enter on a suggested name picks it without saving', async () => {
    const calls = stubNamesApi([KILL_THE_FISH]);
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');

    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), 'kill');
    await suggested(/Kill The Fish/);
    await userEvent.keyboard('{ArrowDown}{Control>}{Enter}{/Control}');

    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('5');
    expect(saved(calls, 'POST')).toHaveLength(0);

    await userEvent.keyboard('{Control>}{Enter}{/Control}');
    await waitFor(() => expect(saved(calls, 'POST')).toHaveLength(1));
  });

  it('still takes a name that is not suggested', async () => {
    const calls = stubNamesApi([KILL_THE_FISH]);
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '3');

    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), ' Kill them all ');
    await suggested(/Kill The Fish/);
    // Nothing is highlighted while typing: Enter saves what was typed.
    await userEvent.keyboard('{Enter}');

    await waitFor(() => expect(saved(calls, 'POST')).toHaveLength(1));
    expect(saved(calls, 'POST')[0]).toMatchObject({
      name: 'Kill them all',
      buyIn: 3,
      variantId: null,
      modality: 'NLHE',
    });
  });

  it('after saving and adding another, a picked name replaces what was kept', async () => {
    const calls = stubNamesApi([KILL_THE_FISH]);
    renderApp('/games');
    const form = await openForm();
    await choose(form, 'Room', 'Winamax (EUR)');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '2');
    await userEvent.click(form.getByRole('button', { name: 'Save and add another' }));
    await waitFor(() => expect(saved(calls, 'POST')).toHaveLength(1));
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('2');

    await userEvent.type(form.getByRole('combobox', { name: 'Name' }), 'kill');
    await userEvent.click(await suggested(/Kill The Fish/));

    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('5');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('KO');
  });

  it('only changes the name of a game being edited', async () => {
    const original = game({ id: 7, name: 'Monday Special', buyIn: 3, entries: 2 });
    const calls = stubNamesApi([KILL_THE_FISH], [original]);
    renderApp('/games');

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Monday Special' }));
    const form = within(await screen.findByRole('dialog', { name: 'Edit game' }));
    const name = await form.findByRole('combobox', { name: 'Name' });

    // The name it already has is not searched.
    await userEvent.click(name);
    await typingPause();
    expect(nameSearches(calls)).toEqual([]);

    await userEvent.clear(name);
    await userEvent.type(name, 'kill');
    await userEvent.click(await suggested(/Kill The Fish/));

    expect(name).toHaveValue('Kill The Fish');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('3');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('');
    expect(form.getByRole('radio', { name: "Hold'em" })).toBeChecked();

    await userEvent.click(form.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(saved(calls, 'PUT')).toHaveLength(1));
    expect(saved(calls, 'PUT')[0]).toMatchObject({
      name: 'Kill The Fish',
      buyIn: 3,
      entries: 2,
      variantId: null,
      modality: 'NLHE',
    });
  });
});
