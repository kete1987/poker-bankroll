import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type { Game, GameBatchRequest, GameRequest, GameTemplateRequest } from '../api/types';
import { todayIso } from '../games/gameDefaults';
import { game, page, ROOMS, template, VARIANTS } from '../test/fixtures';
import { onANarrowScreen } from '../test/narrowScreen';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

const EXPRESSO = template({ id: 1 });
const SUNDAY_KO = template({
  id: 2,
  label: 'Sunday KO',
  gameType: 'TOURNAMENT',
  modality: 'PLO',
  variant: { id: 10, code: 'KO', name: null },
  name: 'Kill The Fish',
  buyIn: 10,
});
const CASH = template({
  id: 3,
  room: { id: 2, name: 'PokerStars' },
  gameType: 'CASH',
  variant: null,
  currencyCode: 'USD',
  buyIn: 25,
});
/** In the inactive room: it starts no games. */
const CLOSED = template({
  id: 4,
  room: { id: 3, name: 'Unibet' },
  gameType: 'TOURNAMENT',
  variant: null,
  name: 'Night Owl',
  buyIn: 2,
  usable: false,
});
const TEMPLATES = [EXPRESSO, SUNDAY_KO, CASH, CLOSED];

const FINISHED = game({
  id: 7,
  gameType: 'TOURNAMENT',
  variant: { id: 10, code: 'KO', name: null },
  name: 'Kill The Fish',
  buyIn: 5,
  prize: 20,
  net: 15,
});

function stubTemplatesApi(handlers: Record<string, unknown> = {}) {
  return stubApi({
    'GET /rooms': ROOMS,
    'GET /variants': VARIANTS,
    'GET /game-templates': TEMPLATES,
    'GET /games': (call: ApiCall) => page(call.query.get('status') === 'IN_PLAY' ? [] : [FINISHED]),
    'POST /games': (call: ApiCall) => echo(call.body as GameRequest),
    'POST /game-templates': (call: ApiCall) => {
      const request = call.body as GameTemplateRequest;
      return template({ id: 50, label: request.label, name: request.name ?? null });
    },
    ...handlers,
  });
}

function echo(request: GameRequest, id = 100): Game {
  return game({
    id,
    gameType: request.gameType,
    room: { id: request.roomId, name: 'Winamax' },
    status: request.status ?? 'IN_PLAY',
    name: request.name ?? null,
    buyIn: request.buyIn,
  });
}

function sent<T>(calls: ApiCall[], method: string, path: string): T[] {
  return calls
    .filter((call) => call.method === method && call.path === path)
    .map((call) => call.body as T);
}

/** Options live in a popover that jsdom never sees as shown, hence `hidden: true`. */
async function choose(form: ReturnType<typeof within>, label: string, option: string | RegExp) {
  await userEvent.click(form.getByRole('combobox', { name: label }));
  await userEvent.click(await screen.findByRole('option', { name: option, hidden: true }));
}

/** What a game started from the Expresso template is. */
const EXPRESSO_GAME: GameRequest = {
  playedOn: todayIso(),
  playedAt: null,
  roomId: 1,
  gameType: 'SIT_AND_GO',
  modality: 'NLHE',
  variantId: 20,
  status: 'IN_PLAY',
  name: null,
  buyIn: 5,
  entries: null,
  paidWithTicket: false,
  prize: null,
  bounty: null,
  ticketPrizeValue: null,
  ticketDescription: null,
  notes: null,
};

describe('Quick start on the games page', () => {
  it('starts a game in play, today, with what the template says, in one click', async () => {
    const calls = stubTemplatesApi();
    renderApp('/games');

    const quickStart = within(await screen.findByRole('region', { name: 'Quick start' }));
    // Named after room, variant and buy-in without a label; templates of inactive rooms are left out.
    const buttons = quickStart
      .getAllByRole('button')
      .filter((button) => button.getAttribute('aria-label')?.startsWith('Start '));
    expect(buttons.map((button) => button.textContent)).toEqual([
      'Winamax · Expresso · €5.00',
      'Sunday KO',
      'PokerStars · Cash · US$25.00',
    ]);
    expect(quickStart.queryByRole('button', { name: /Night Owl/ })).not.toBeInTheDocument();

    await userEvent.click(
      quickStart.getByRole('button', { name: 'Start Winamax · Expresso · €5.00' }),
    );

    await waitFor(() => expect(sent(calls, 'POST', '/games')).toHaveLength(1));
    expect(sent(calls, 'POST', '/games')[0]).toEqual(EXPRESSO_GAME);
    expect(await screen.findByText('Winamax · Expresso · €5.00 started')).toBeInTheDocument();
    // No form was opened.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('sends every field a template has, a cash table too', async () => {
    const calls = stubTemplatesApi();
    renderApp('/games');

    await userEvent.click(await screen.findByRole('button', { name: 'Start Sunday KO' }));
    await userEvent.click(
      screen.getByRole('button', { name: 'Start PokerStars · Cash · US$25.00' }),
    );

    await waitFor(() => expect(sent(calls, 'POST', '/games')).toHaveLength(2));
    const [ko, cash] = sent<GameRequest>(calls, 'POST', '/games');
    expect(ko).toMatchObject({
      roomId: 1,
      gameType: 'TOURNAMENT',
      modality: 'PLO',
      variantId: 10,
      name: 'Kill The Fish',
      buyIn: 10,
      status: 'IN_PLAY',
    });
    expect(cash).toMatchObject({ roomId: 2, gameType: 'CASH', variantId: null, buyIn: 25 });
  });

  it('records one game however many times the button is clicked while it is saved', async () => {
    let answer: (game: Game) => void = () => {};
    const calls = stubTemplatesApi({
      'POST /games': () => new Promise<Game>((resolve) => (answer = resolve)),
    });
    renderApp('/games');

    const start = await screen.findByRole('button', { name: 'Start Winamax · Expresso · €5.00' });
    await userEvent.dblClick(start);
    await userEvent.click(start);
    answer(echo(EXPRESSO_GAME));

    expect(await screen.findByText('Winamax · Expresso · €5.00 started')).toBeInTheDocument();
    expect(sent(calls, 'POST', '/games')).toHaveLength(1);
  });

  it('says why a game was not started', async () => {
    stubTemplatesApi({
      'POST /games': () =>
        problem(
          409,
          'ROOM_INACTIVE',
          'The room Winamax is inactive: activate it to record new games in it.',
        ),
    });
    renderApp('/games');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Start Winamax · Expresso · €5.00' }),
    );

    expect(
      await screen.findByText('Winamax · Expresso · €5.00 was not started'),
    ).toBeInTheDocument();
    expect(screen.getByText(/The room Winamax is inactive/)).toBeInTheDocument();
  });

  it('is not there without templates that can start games', async () => {
    stubTemplatesApi({ 'GET /game-templates': [CLOSED] });
    renderApp('/games');

    expect(await screen.findByRole('button', { name: 'Add game' })).toBeInTheDocument();
    await screen.findByRole('button', { name: 'Duplicate Kill The Fish' });
    expect(screen.queryByRole('region', { name: 'Quick start' })).not.toBeInTheDocument();
  });

  it('opens the form filled from the template with its arrow, to change something first', async () => {
    const calls = stubTemplatesApi();
    renderApp('/games');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Change Sunday KO before starting it' }),
    );
    const form = within(await screen.findByRole('dialog', { name: 'Add game' }));

    expect(form.getByLabelText(/^Date/)).toHaveValue(todayIso());
    expect(form.getByRole('radio', { name: 'Tournament' })).toBeChecked();
    expect(form.getByRole('combobox', { name: 'Room' })).toHaveValue('Winamax (EUR)');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('KO');
    expect(form.getByRole('radio', { name: 'Omaha' })).toBeChecked();
    expect(form.getByRole('combobox', { name: 'Name' })).toHaveValue('Kill The Fish');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('10');

    // Recorded finished this time.
    await userEvent.click(form.getByRole('radio', { name: 'Finished' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Prize' }), '30');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'POST', '/games')).toHaveLength(1));
    expect(sent(calls, 'POST', '/games')[0]).toMatchObject({
      gameType: 'TOURNAMENT',
      variantId: 10,
      modality: 'PLO',
      name: 'Kill The Fish',
      buyIn: 10,
      status: 'FINISHED',
      prize: 30,
    });
  });
});

describe('From template in the forms', () => {
  it('fills the add game form from a usable template', async () => {
    const calls = stubTemplatesApi();
    renderApp('/games');

    await userEvent.click(await screen.findByRole('button', { name: 'Add game' }));
    const form = within(await screen.findByRole('dialog', { name: 'Add game' }));
    await userEvent.click(form.getByRole('combobox', { name: 'From template' }));
    // Only those that can start games are offered.
    expect(
      screen.queryByRole('option', { name: /Night Owl/, hidden: true }),
    ).not.toBeInTheDocument();
    await userEvent.click(
      await screen.findByRole('option', { name: 'PokerStars · Cash · US$25.00', hidden: true }),
    );

    expect(form.getByRole('radio', { name: 'Cash' })).toBeChecked();
    expect(form.getByRole('combobox', { name: 'Room' })).toHaveValue('PokerStars (USD)');
    expect(form.getByRole('textbox', { name: 'Brought to the table' })).toHaveValue('25');

    await userEvent.click(form.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(sent(calls, 'POST', '/games')).toHaveLength(1));
    expect(sent(calls, 'POST', '/games')[0]).toMatchObject({
      roomId: 2,
      gameType: 'CASH',
      buyIn: 25,
      variantId: null,
      playedOn: todayIso(),
    });
  });

  it('fills what the games of add several share, offering no cash templates', async () => {
    const calls = stubTemplatesApi({
      'POST /games/batch': (call: ApiCall) => ({
        games: (call.body as GameBatchRequest).games.map((request, index) =>
          echo(request, 200 + index),
        ),
      }),
    });
    renderApp('/games');

    await userEvent.click(await screen.findByRole('button', { name: 'Add several' }));
    const form = within(await screen.findByRole('dialog', { name: 'Add several games' }));
    await userEvent.click(form.getByRole('combobox', { name: 'From template' }));
    expect(
      screen.queryByRole('option', { name: /PokerStars · Cash/, hidden: true }),
    ).not.toBeInTheDocument();
    await userEvent.click(
      await screen.findByRole('option', { name: 'Winamax · Expresso · €5.00', hidden: true }),
    );

    expect(form.getByRole('radio', { name: 'Sit & Go / Spin' })).toBeChecked();
    expect(form.getByRole('combobox', { name: 'Room' })).toHaveValue('Winamax (EUR)');
    expect(form.getByRole('combobox', { name: 'Variant' })).toHaveValue('Expresso');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('5');

    await userEvent.click(form.getByRole('button', { name: 'Add 2 games' }));
    await waitFor(() => expect(sent(calls, 'POST', '/games/batch')).toHaveLength(1));
    const { games } = sent<GameBatchRequest>(calls, 'POST', '/games/batch')[0]!;
    expect(games).toHaveLength(2);
    expect(games[0]).toMatchObject({ roomId: 1, gameType: 'SIT_AND_GO', variantId: 20, buyIn: 5 });
  });
});

describe('Save a game as a template', () => {
  it('keeps its room, type, variant, modality, name and buy-in, with an optional label', async () => {
    const calls = stubTemplatesApi();
    renderApp('/games');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Save Kill The Fish as a template' }),
    );
    const dialog = within(await screen.findByRole('dialog', { name: 'Save as template' }));
    const label = dialog.getByRole('textbox', { name: /Label/ });
    // Without a label, it would be named after what it is.
    expect(label).toHaveAttribute('placeholder', 'Winamax · Kill The Fish · €5.00');
    await userEvent.type(label, 'Daily KO');
    await userEvent.click(dialog.getByRole('button', { name: 'Save template' }));

    await waitFor(() => expect(sent(calls, 'POST', '/game-templates')).toHaveLength(1));
    expect(sent(calls, 'POST', '/game-templates')[0]).toEqual({
      label: 'Daily KO',
      roomId: 1,
      gameType: 'TOURNAMENT',
      modality: 'NLHE',
      variantId: 10,
      name: 'Kill The Fish',
      buyIn: 5,
    });
    expect(await screen.findByText('Template saved')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('says why it was not saved', async () => {
    stubTemplatesApi({
      'POST /game-templates': () =>
        problem(
          409,
          'ROOM_INACTIVE',
          'The room Winamax is inactive: activate it to record new games in it.',
        ),
    });
    renderApp('/games');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Save Kill The Fish as a template' }),
    );
    const dialog = within(await screen.findByRole('dialog', { name: 'Save as template' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Save template' }));

    expect(await dialog.findByText(/The room Winamax is inactive/)).toBeInTheDocument();
  });
});

describe('Settings: templates', () => {
  function rowOf(name: string) {
    const row = screen.getAllByRole('row').find((candidate) => within(candidate).queryByText(name));
    expect(row).toBeDefined();
    return within(row!);
  }

  it('lists every template, marking those that start no games', async () => {
    stubTemplatesApi();
    renderApp('/settings?tab=templates');

    expect(await screen.findByRole('tab', { name: 'Templates' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    const rows = (await screen.findAllByRole('row')).slice(1);
    expect(rows).toHaveLength(4);
    expect(
      rowOf('Sunday KO').getByText('Tournament · KO · Omaha · Kill The Fish'),
    ).toBeInTheDocument();
    expect(
      rowOf('Unibet · Night Owl · €2.00').getByText('Inactive room or variant'),
    ).toBeInTheDocument();
    expect(rowOf('Sunday KO').queryByText('Inactive room or variant')).not.toBeInTheDocument();
  });

  it('creates a template', async () => {
    const calls = stubTemplatesApi({ 'GET /game-templates': [] });
    renderApp('/settings?tab=templates');

    expect(await screen.findByText(/There are no templates yet/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add template' }));
    const form = within(await screen.findByRole('dialog', { name: 'Add template' }));
    await userEvent.click(form.getByRole('radio', { name: 'Sit & Go / Spin' }));
    await choose(form, 'Room', 'Winamax (EUR)');
    await choose(form, 'Variant', 'Expresso');
    await userEvent.type(form.getByRole('textbox', { name: 'Buy-in' }), '2');
    await userEvent.type(form.getByRole('textbox', { name: /Label/ }), 'Evening Expresso');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'POST', '/game-templates')).toHaveLength(1));
    expect(sent(calls, 'POST', '/game-templates')[0]).toEqual({
      label: 'Evening Expresso',
      roomId: 1,
      gameType: 'SIT_AND_GO',
      modality: 'NLHE',
      variantId: 20,
      name: null,
      buyIn: 2,
    });
    expect(await screen.findByText('Template created')).toBeInTheDocument();
  });

  it('asks for the room and the buy-in before sending', async () => {
    const calls = stubTemplatesApi({ 'GET /game-templates': [] });
    renderApp('/settings?tab=templates');

    await userEvent.click(await screen.findByRole('button', { name: 'Add template' }));
    const form = within(await screen.findByRole('dialog', { name: 'Add template' }));
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    expect(form.getAllByText('Required')).toHaveLength(2);
    expect(sent(calls, 'POST', '/game-templates')).toHaveLength(0);
  });

  it('edits a template, keeping the inactive room it has', async () => {
    const calls = stubTemplatesApi({
      'PUT /game-templates/4': (call: ApiCall) => ({
        ...CLOSED,
        label: (call.body as GameTemplateRequest).label,
      }),
    });
    renderApp('/settings?tab=templates');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Edit Unibet · Night Owl · €2.00' }),
    );
    const form = within(await screen.findByRole('dialog', { name: 'Edit template' }));
    expect(form.getByRole('combobox', { name: 'Room' })).toHaveValue('Unibet (EUR)');
    expect(form.getByRole('textbox', { name: 'Buy-in' })).toHaveValue('2');
    await userEvent.type(form.getByRole('textbox', { name: /Label/ }), 'Old one');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'PUT', '/game-templates/4')).toHaveLength(1));
    expect(sent(calls, 'PUT', '/game-templates/4')[0]).toEqual({
      label: 'Old one',
      roomId: 3,
      gameType: 'TOURNAMENT',
      modality: 'NLHE',
      variantId: null,
      name: 'Night Owl',
      buyIn: 2,
    });
    expect(await screen.findByText('Template saved')).toBeInTheDocument();
  });

  it('deletes a template after asking', async () => {
    const calls = stubTemplatesApi({
      'DELETE /game-templates/2': new Response(null, { status: 204 }),
    });
    renderApp('/settings?tab=templates');

    await userEvent.click(await screen.findByRole('button', { name: 'Delete Sunday KO' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Delete template' }));
    expect(dialog.getByText(/Delete the template Sunday KO\?/)).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Delete' }));

    await waitFor(() =>
      expect(
        calls.some((call) => call.method === 'DELETE' && call.path === '/game-templates/2'),
      ).toBe(true),
    );
    expect(await screen.findByText('Template deleted')).toBeInTheDocument();
  });
});

describe('Templates on a phone', () => {
  onANarrowScreen();

  it('keeps the quick start buttons and saves a game as a template from its card', async () => {
    const calls = stubTemplatesApi();
    renderApp('/games');

    const quickStart = within(await screen.findByRole('region', { name: 'Quick start' }));
    expect(
      quickStart.getByRole('button', { name: 'Start Winamax · Expresso · €5.00' }),
    ).toBeInTheDocument();

    await userEvent.click(await screen.findByRole('button', { name: 'Actions for Kill The Fish' }));
    await userEvent.click(
      await screen.findByRole('menuitem', { name: 'Save as template', hidden: true }),
    );
    const dialog = within(await screen.findByRole('dialog', { name: 'Save as template' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Save template' }));

    await waitFor(() => expect(sent(calls, 'POST', '/game-templates')).toHaveLength(1));
    expect(sent<GameTemplateRequest>(calls, 'POST', '/game-templates')[0]!.label).toBeNull();
  });

  it('lists the templates as cards in the settings', async () => {
    stubTemplatesApi();
    renderApp('/settings?tab=templates');

    expect(await screen.findByText('Sunday KO')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(TEMPLATES.length);
    expect(screen.getByText('Inactive room or variant')).toBeInTheDocument();
  });
});
