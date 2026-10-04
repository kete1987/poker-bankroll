import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type {
  BankrollFigures,
  BankrollSummary,
  Movement,
  MovementPage,
  MovementRequest,
} from '../api/types';
import { todayIso } from '../games/gameDefaults';
import { bankrollSummary, room } from '../test/fixtures';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

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

const WINAMAX = { id: 1, name: 'Winamax' };
const UNIBET = { id: 3, name: 'Unibet' };
const STARS = { id: 2, name: 'PokerStars' };

const EUR_NOW = {
  currencyCode: 'EUR',
  total: figures({
    deposited: 300,
    withdrawn: 50,
    bonuses: 12.5,
    adjustments: -3.2,
    gamesNet: 40,
    result: 52.5,
    bankroll: 299.3,
    ticketsWon: 20,
  }),
  withoutRoom: figures({ deposited: 200, bankroll: 200 }),
  rooms: [
    {
      room: UNIBET,
      active: false,
      figures: figures({
        deposited: 20,
        withdrawn: 50,
        gamesNet: 60,
        result: 60,
        bankroll: 30,
      }),
    },
    {
      room: WINAMAX,
      active: true,
      figures: figures({
        deposited: 80,
        bonuses: 12.5,
        adjustments: -3.2,
        gamesNet: -20,
        result: -7.5,
        bankroll: 69.3,
      }),
    },
  ],
};
const USD_NOW = {
  currencyCode: 'USD',
  total: figures({ deposited: 50, gamesNet: -10, result: -10, bankroll: 40 }),
  withoutRoom: figures(),
  rooms: [
    {
      room: STARS,
      active: true,
      figures: figures({ deposited: 50, gamesNet: -10, result: -10, bankroll: 40 }),
    },
  ],
};

const NOW: BankrollSummary = bankrollSummary([EUR_NOW]);

const OF_PERIOD: BankrollSummary = bankrollSummary([
  {
    currencyCode: 'EUR',
    total: figures({ deposited: 100, gamesNet: -30, result: -30, bankroll: 70 }),
    withoutRoom: figures(),
    rooms: [{ room: WINAMAX, active: true, figures: figures({ deposited: 100, bankroll: 70 }) }],
  },
]);

function movement(overrides: Partial<Movement> = {}): Movement {
  return {
    id: 1,
    occurredOn: '2026-01-19',
    type: 'DEPOSIT',
    room: WINAMAX,
    currencyCode: 'EUR',
    amount: 100,
    signedAmount: 100,
    notes: null,
    createdAt: '2026-01-19T20:00:00Z',
    updatedAt: '2026-01-19T20:00:00Z',
    ...overrides,
  };
}

/** The movement the backend would answer with for the request of a call. */
function saved(call: ApiCall, overrides: Partial<Movement>): Movement {
  const request = call.body as MovementRequest;
  return movement({
    occurredOn: request.occurredOn,
    type: request.type,
    amount: request.amount,
    signedAmount: request.amount,
    notes: request.notes ?? null,
    ...overrides,
  });
}

function page(items: Movement[], overrides: Partial<MovementPage> = {}): MovementPage {
  return { items, page: 0, size: 25, totalItems: items.length, totalPages: 1, ...overrides };
}

const MOVEMENTS = [
  movement({ id: 1, notes: 'Initial bankroll' }),
  movement({ id: 2, type: 'WITHDRAWAL', amount: 50, signedAmount: -50, room: UNIBET }),
  movement({ id: 3, type: 'BONUS', amount: 12.5, signedAmount: 12.5 }),
  movement({ id: 4, type: 'DEPOSIT', amount: 200, signedAmount: 200, room: null }),
];

function stubBankroll(handlers: Record<string, unknown> = {}) {
  return stubApi({
    'GET /rooms': [
      room({ id: 2, name: 'PokerStars', currencyCode: 'USD' }),
      room({ id: 3, name: 'Unibet', active: false }),
      room({ id: 1, name: 'Winamax' }),
    ],
    'GET /bankroll/summary': (call: ApiCall) =>
      call.query.has('from') || call.query.has('to') ? OF_PERIOD : NOW,
    'GET /bankroll/movements': page(MOVEMENTS),
    ...handlers,
  });
}

function queriesTo(calls: ApiCall[], path: string) {
  return calls
    .filter((call) => call.method === 'GET' && call.path === path)
    .map((call) => {
      const query: Record<string, string> = {};
      for (const key of new Set(call.query.keys())) {
        query[key] = call.query.getAll(key).join(',');
      }
      return query;
    });
}

function sent(calls: ApiCall[], method: string) {
  return calls.filter((call) => call.method === method);
}

function card(name: string) {
  return within(screen.getByRole('region', { name }));
}

/** Chooses an option of one select, by the list it controls (others may offer the same name). */
async function pick(select: HTMLElement, option: string) {
  await userEvent.click(select);
  const list = await waitFor(() => {
    const element = document.getElementById(select.getAttribute('aria-controls') ?? '');
    expect(element).not.toBeNull();
    return element!;
  });
  await userEvent.click(within(list).getByRole('option', { name: option, hidden: true }));
}

async function openForm(button = 'Add movement', title = 'Add movement') {
  await userEvent.click(await screen.findByRole('button', { name: button }));
  const dialog = within(await screen.findByRole('dialog', { name: title }));
  await dialog.findByRole('textbox', { name: 'Amount' });
  return dialog;
}

describe('Bankroll page', () => {
  it('shows the bankroll as it is now, without tickets', async () => {
    const calls = stubBankroll();
    renderApp('/bankroll');

    expect(await screen.findByRole('region', { name: 'Bankroll' })).toBeInTheDocument();
    expect(card('Bankroll').getByText('€299.30')).toBeInTheDocument();
    expect(card('Bankroll').getByText('Adjustments: -€3.20')).toBeInTheDocument();
    expect(card('Deposited').getByText('€300.00')).toBeInTheDocument();
    expect(card('Deposited').getByText('€50.00 withdrawn')).toBeInTheDocument();
    expect(card('Result').getByText('+€52.50')).toBeInTheDocument();
    expect(
      card('Result').getByText('Games +€40.00 · Rakeback / bonuses €12.50'),
    ).toBeInTheDocument();
    // Tickets are not money: they have no place here.
    expect(screen.queryByText(/ticket/i)).not.toBeInTheDocument();
    expect(queriesTo(calls, '/bankroll/summary')).toEqual([{}]);
    expect(screen.getByRole('combobox', { name: 'Period' })).toHaveValue('All time');
    expect(screen.queryByRole('combobox', { name: 'Currency' })).not.toBeInTheDocument();
    // In a single currency nothing is converted.
    expect(screen.queryByText(/converted/)).not.toBeInTheDocument();
  });

  it('breaks the bankroll down per room, with the movements without a room and the total', async () => {
    stubBankroll();
    renderApp('/bankroll');

    const table = within((await screen.findAllByRole('table'))[0]!);
    expect(table.getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      'Room',
      'Deposited',
      'Withdrawn',
      'Rakeback / bonuses',
      'Adjustments',
      'Net of games',
      'Result',
      'Bankroll',
    ]);
    const rows = table.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(4);
    expect(rows[0]).toHaveTextContent('Unibet');
    expect(rows[0]).toHaveTextContent('Inactive');
    expect(rows[0]).toHaveTextContent(
      ['€20.00', '€50.00', '€0.00', '€0.00', '+€60.00', '+€60.00', '+€30.00'].join(''),
    );
    expect(rows[1]).toHaveTextContent('Winamax');
    expect(rows[1]).toHaveTextContent(
      ['€80.00', '€0.00', '€12.50', '-€3.20', '-€20.00', '-€7.50', '+€69.30'].join(''),
    );
    expect(rows[2]).toHaveTextContent('No room');
    expect(rows[2]).toHaveTextContent('+€200.00');
    expect(rows[3]).toHaveTextContent('Total');
    expect(rows[3]).toHaveTextContent('+€299.30');
  });

  it('lists the movements, newest first, with what each one adds or takes', async () => {
    const calls = stubBankroll();
    renderApp('/bankroll');

    const table = within((await screen.findAllByRole('table'))[1]!);
    const rows = table.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(4);
    expect(rows[0]).toHaveTextContent('19/01/2026');
    expect(rows[0]).toHaveTextContent('Deposit');
    expect(rows[0]).toHaveTextContent('Winamax');
    expect(rows[0]).toHaveTextContent('+€100.00');
    expect(rows[0]).toHaveTextContent('Initial bankroll');
    expect(rows[1]).toHaveTextContent('Withdrawal');
    expect(rows[1]).toHaveTextContent('-€50.00');
    expect(rows[2]).toHaveTextContent('Rakeback / bonus');
    expect(rows[3]).toHaveTextContent('No room');
    expect(screen.getByText('4 movements')).toBeInTheDocument();
    // Every movement, each in its own currency.
    expect(queriesTo(calls, '/bankroll/movements').at(-1)).toEqual({
      page: '0',
      size: '25',
    });
  });

  it('shows the figures of a period when one is chosen', async () => {
    const calls = stubBankroll();
    renderApp('/bankroll?from=2026-01-01&to=2026-01-31');

    expect(await screen.findByRole('region', { name: 'Change in the period' })).toBeInTheDocument();
    expect(card('Change in the period').getByText('+€70.00')).toBeInTheDocument();
    expect(card('Result').getByText('-€30.00')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Change' })).toBeInTheDocument();
    expect(queriesTo(calls, '/bankroll/summary')).toContainEqual({
      from: '2026-01-01',
      to: '2026-01-31',
    });
    expect(queriesTo(calls, '/bankroll/movements').at(-1)).toMatchObject({
      from: '2026-01-01',
      to: '2026-01-31',
    });
  });

  it('filters by rooms and by type of movement, going back to the first page', async () => {
    const calls = stubBankroll({
      'GET /bankroll/movements': page(MOVEMENTS, { totalPages: 3, totalItems: 60 }),
    });
    renderApp('/bankroll?page=2');
    await screen.findAllByRole('table');
    expect(queriesTo(calls, '/bankroll/movements').at(-1)).toMatchObject({ page: '1' });

    await userEvent.click(screen.getByRole('combobox', { name: 'Room' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Winamax', hidden: true }));
    await userEvent.click(await screen.findByRole('option', { name: 'Unibet', hidden: true }));
    await waitFor(() =>
      expect(queriesTo(calls, '/bankroll/movements').at(-1)).toMatchObject({
        roomId: '1,3',
        page: '0',
      }),
    );
    expect(queriesTo(calls, '/bankroll/summary').at(-1)).toEqual({ roomId: '1,3' });

    await userEvent.click(screen.getByRole('combobox', { name: 'Type of movement' }));
    await userEvent.click(
      await screen.findByRole('option', { name: 'Rakeback / bonus', hidden: true }),
    );
    await waitFor(() =>
      expect(queriesTo(calls, '/bankroll/movements').at(-1)).toMatchObject({ type: 'BONUS' }),
    );

    await userEvent.click(screen.getByRole('button', { name: 'Page 3' }));
    await waitFor(() =>
      expect(queriesTo(calls, '/bankroll/movements').at(-1)).toMatchObject({ page: '2' }),
    );
  });

  it('converts the totals when rooms are in several currencies, each room in its own', async () => {
    stubBankroll({
      'GET /bankroll/summary': bankrollSummary([EUR_NOW, USD_NOW], {
        currencyCode: 'EUR',
        total: figures({
          deposited: 345.5,
          withdrawn: 50,
          bonuses: 12.5,
          adjustments: -3.2,
          gamesNet: 31,
          result: 43.5,
          bankroll: 334.6,
        }),
        rooms: [
          { room: STARS, active: true, figures: figures({ bankroll: 35.3 }) },
          { room: UNIBET, active: false, figures: figures({ bankroll: 30 }) },
          { room: WINAMAX, active: true, figures: figures({ bankroll: 69.3 }) },
        ],
        balanceRatesOn: todayIso(),
        missingRates: [{ currencyCode: 'USD', from: '2025-12-20', to: '2025-12-20' }],
      }),
    });
    renderApp('/bankroll');
    await screen.findByRole('region', { name: 'Bankroll' });

    expect(card('Bankroll').getByText('€334.60')).toBeInTheDocument();
    expect(card('Bankroll').getByText('€299.30 · US$40.00')).toBeInTheDocument();
    expect(card('Deposited').getByText('€345.50')).toBeInTheDocument();
    expect(card('Deposited').getByText('€300.00 · US$50.00')).toBeInTheDocument();
    expect(card('Result').getByText('+€52.50 · -US$10.00')).toBeInTheDocument();
    expect(screen.getByText(/the bankroll with today's rates/)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('USD on 20/12/2025');

    const rows = within(screen.getAllByRole('table')[0]!).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(5);
    ['PokerStars', 'Unibet', 'Winamax', 'No room (EUR)', 'Total in EUR'].forEach((name, index) =>
      expect(rows[index]).toHaveTextContent(name),
    );
    // Each room in its own currency; the total converted.
    expect(rows[0]).toHaveTextContent('-US$10.00+US$40.00');
    expect(rows[4]).toHaveTextContent('+€43.50+€334.60');
  });

  it('says so when there are no movements, and when the data cannot be loaded', async () => {
    stubBankroll({ 'GET /bankroll/movements': page([]) });
    const first = renderApp('/bankroll');
    expect(
      await screen.findByText('There are no movements for these filters.'),
    ).toBeInTheDocument();
    first.unmount();

    stubBankroll({ 'GET /bankroll/summary': () => problem(500, 'INTERNAL_ERROR', 'Boom') });
    renderApp('/bankroll');
    expect(
      await screen.findByText(
        'The data could not be loaded. Check that the API is available and reload the page.',
      ),
    ).toBeInTheDocument();
  });
});

describe('Movement form', () => {
  it('records a movement of a room, in its currency', async () => {
    const calls = stubBankroll({
      'POST /bankroll/movements': (call: ApiCall) =>
        saved(call, { id: 9, room: UNIBET, signedAmount: -25.5 }),
    });
    renderApp('/bankroll');
    const form = await openForm();

    expect(form.getByRole('radio', { name: 'Deposit' })).toBeChecked();
    expect(form.getByLabelText(/^Date/)).toHaveValue(todayIso());
    await userEvent.click(form.getByRole('radio', { name: 'Withdrawal' }));
    // Inactive rooms take movements too.
    await userEvent.click(form.getByRole('combobox', { name: 'Room' }));
    await userEvent.click(
      await screen.findByRole('option', { name: 'Unibet (EUR, inactive)', hidden: true }),
    );
    // The currency is the one of the room.
    expect(form.getByRole('textbox', { name: 'Currency' })).toHaveValue('EUR');
    expect(form.getByRole('textbox', { name: 'Currency' })).toBeDisabled();
    await userEvent.type(form.getByRole('textbox', { name: 'Amount' }), '25,50');
    await userEvent.type(form.getByRole('textbox', { name: 'Notes' }), ' closing ');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]?.body).toEqual({
      occurredOn: todayIso(),
      type: 'WITHDRAWAL',
      roomId: 3,
      currencyCode: null,
      amount: 25.5,
      notes: 'closing',
    });
    expect(await screen.findByText('Movement saved')).toBeInTheDocument();
    expect(screen.getByText('Withdrawal: -€25.50')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('records a movement without a room, in the currency chosen', async () => {
    const calls = stubBankroll({
      'POST /bankroll/movements': (call: ApiCall) =>
        saved(call, { id: 9, room: null, currencyCode: 'USD' }),
    });
    renderApp('/bankroll');
    const form = await openForm();

    // The currency of the page is offered first.
    expect(form.getByRole('combobox', { name: 'Currency' })).toHaveValue('EUR');
    await pick(form.getByRole('combobox', { name: 'Currency' }), 'USD');
    await userEvent.type(form.getByRole('textbox', { name: 'Amount' }), '200');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]?.body).toMatchObject({
      type: 'DEPOSIT',
      roomId: null,
      currencyCode: 'USD',
      amount: 200,
    });
  });

  it('asks for a positive amount, except for an adjustment', async () => {
    const calls = stubBankroll({
      'POST /bankroll/movements': (call: ApiCall) =>
        saved(call, { id: 9, room: null, signedAmount: -4.5 }),
    });
    renderApp('/bankroll');
    const form = await openForm();

    await userEvent.click(form.getByRole('button', { name: 'Save' }));
    expect(await form.findByText('Required')).toBeInTheDocument();

    // A deposit cannot be typed negative: the sign is dropped, and zero is not an amount.
    await userEvent.type(form.getByRole('textbox', { name: 'Amount' }), '-0');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));
    expect(await form.findByText('Enter an amount greater than zero')).toBeInTheDocument();
    expect(sent(calls, 'POST')).toHaveLength(0);

    await userEvent.click(form.getByRole('radio', { name: 'Adjustment' }));
    expect(form.getByText('Negative to subtract.')).toBeInTheDocument();
    await userEvent.clear(form.getByRole('textbox', { name: 'Amount' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Amount' }), '-4.50');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]?.body).toMatchObject({ type: 'ADJUSTMENT', amount: -4.5 });
  });

  it('shows the errors of the backend', async () => {
    stubBankroll({
      'POST /bankroll/movements': () => problem(400, 'UNKNOWN_ROOM', 'The room 1 does not exist.'),
    });
    renderApp('/bankroll');
    const form = await openForm();
    await userEvent.type(form.getByRole('textbox', { name: 'Amount' }), '10');

    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    expect(await form.findByText('The movement was not saved')).toBeInTheDocument();
    expect(form.getByText('The room 1 does not exist.')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('edits a movement with the form filled in', async () => {
    const calls = stubBankroll({
      'PUT /bankroll/movements/3': (call: ApiCall) => saved(call, { id: 3, signedAmount: 15 }),
    });
    renderApp('/bankroll');
    const form = await openForm('Edit Rakeback / bonus of 19/01/2026', 'Edit movement');

    expect(form.getByRole('radio', { name: 'Rakeback / bonus' })).toBeChecked();
    expect(form.getByRole('combobox', { name: 'Room' })).toHaveValue('Winamax (EUR)');
    expect(form.getByRole('textbox', { name: 'Amount' })).toHaveValue('12.5');
    expect(form.getByLabelText(/^Date/)).toHaveValue('2026-01-19');

    await userEvent.clear(form.getByRole('textbox', { name: 'Amount' }));
    await userEvent.type(form.getByRole('textbox', { name: 'Amount' }), '15');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    expect(sent(calls, 'PUT')[0]).toMatchObject({
      path: '/bankroll/movements/3',
      body: { type: 'BONUS', roomId: 1, currencyCode: null, amount: 15, occurredOn: '2026-01-19' },
    });
  });

  it('deletes a movement after asking', async () => {
    const calls = stubBankroll({
      'DELETE /bankroll/movements/2': () => new Response(null, { status: 204 }),
    });
    renderApp('/bankroll');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Delete Withdrawal of 19/01/2026' }),
    );
    const dialog = within(await screen.findByRole('dialog', { name: 'Delete movement' }));
    expect(
      dialog.getByText('Delete the Withdrawal of €50.00 of 19/01/2026? This cannot be undone.'),
    ).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(sent(calls, 'DELETE')).toHaveLength(1));
    expect(sent(calls, 'DELETE')[0]?.path).toBe('/bankroll/movements/2');
    expect(await screen.findByText('Movement deleted')).toBeInTheDocument();
  });
});
