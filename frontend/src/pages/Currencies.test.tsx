import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type { CurrencySettings, ExchangeRateStatus, ManualRate } from '../api/types';
import { onANarrowScreen } from '../test/narrowScreen';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

const SETTINGS: CurrencySettings = {
  baseCurrencyCode: null,
  automaticBaseCurrencyCode: 'EUR',
  effectiveBaseCurrencyCode: 'EUR',
};

const STATUS: ExchangeRateStatus = {
  enabled: true,
  running: false,
  lastAttemptAt: '2026-10-04T15:00:00Z',
  lastSuccessAt: '2026-10-04T15:00:05Z',
  lastError: null,
  baseCurrencyCode: 'EUR',
  currencies: [
    {
      currencyCode: 'USD',
      firstRateOn: '2025-03-03',
      lastRateOn: '2026-10-02',
      neededFrom: '2025-01-10',
      manualRates: 1,
    },
  ],
};

const MANUAL: ManualRate[] = [
  { currencyCode: 'USD', date: '2026-09-01', rate: 1.085, downloadedRate: 1.0912 },
];

const CATALOG = {
  currencies: [
    { code: 'EUR', symbol: '€', decimals: 2 },
    { code: 'GBP', symbol: '£', decimals: 2 },
    { code: 'USD', symbol: '$', decimals: 2 },
  ],
  gameTypes: ['TOURNAMENT', 'SIT_AND_GO', 'CASH'],
  modalities: ['NLHE', 'PLO'],
};

function stubCurrencies(handlers: Record<string, unknown> = {}) {
  return stubApi({
    'GET /catalog': CATALOG,
    'GET /settings/currency': SETTINGS,
    'GET /exchange-rates/status': STATUS,
    'GET /exchange-rates/manual': MANUAL,
    ...handlers,
  });
}

function sent(calls: ApiCall[], method: string) {
  return calls.filter((call) => call.method === method);
}

/** Chooses an option of one select, by the list it controls. */
async function pick(select: HTMLElement, option: string) {
  await userEvent.click(select);
  const list = await waitFor(() => {
    const element = document.getElementById(select.getAttribute('aria-controls') ?? '');
    expect(element).not.toBeNull();
    return element!;
  });
  await userEvent.click(within(list).getByRole('option', { name: option, hidden: true }));
}

describe('Settings: currencies', () => {
  it('shows the base currency, automatic until one is chosen, and chooses one', async () => {
    const calls = stubCurrencies({
      'PUT /settings/currency': (call: ApiCall) => ({
        baseCurrencyCode: (call.body as { baseCurrencyCode: string | null }).baseCurrencyCode,
        automaticBaseCurrencyCode: 'EUR',
        effectiveBaseCurrencyCode: 'USD',
      }),
    });
    renderApp('/settings?tab=currencies');

    const select = await screen.findByRole('combobox', { name: 'Show mixed currencies in' });
    expect(select).toHaveValue('Automatic: EUR (the one with most games)');

    await pick(select, 'USD');

    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    expect(sent(calls, 'PUT')[0]).toMatchObject({
      path: '/settings/currency',
      body: { baseCurrencyCode: 'USD' },
    });
    expect(await screen.findByText('Base currency saved')).toBeInTheDocument();
  });

  it('goes back to the automatic base currency', async () => {
    const calls = stubCurrencies({
      'GET /settings/currency': { ...SETTINGS, baseCurrencyCode: 'USD' },
      'PUT /settings/currency': SETTINGS,
    });
    renderApp('/settings?tab=currencies');

    const select = await screen.findByRole('combobox', { name: 'Show mixed currencies in' });
    expect(select).toHaveValue('USD');
    await pick(select, 'Automatic: EUR (the one with most games)');

    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    expect(sent(calls, 'PUT')[0]?.body).toEqual({ baseCurrencyCode: null });
  });

  it('says which rates there are, which are missing, and downloads them on demand', async () => {
    const calls = stubCurrencies({
      'POST /exchange-rates/refresh': { ...STATUS, lastSuccessAt: '2026-10-04T16:00:00Z' },
    });
    renderApp('/settings?tab=currencies');

    expect(await screen.findByText(/Last download: \d\d\/\d\d\/2026/)).toBeInTheDocument();
    const usd = within(screen.getByRole('row', { name: /^USD/ }));
    expect(usd.getByText('03/03/2025 to 02/10/2026')).toBeInTheDocument();
    // Amounts from January need rates from before the first one.
    expect(usd.getByText('Missing before 03/03/2025')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Update now' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]?.path).toBe('/exchange-rates/refresh');
    expect(await screen.findByText('Exchange rates updated')).toBeInTheDocument();
  });

  it('shows what went wrong in the last download', async () => {
    stubCurrencies({
      'GET /exchange-rates/status': {
        ...STATUS,
        lastSuccessAt: null,
        lastError: 'USD: HTTP 500',
        currencies: [{ ...STATUS.currencies[0]!, firstRateOn: null, lastRateOn: null }],
      },
    });
    renderApp('/settings?tab=currencies');

    expect(await screen.findByText('Last download: not yet')).toBeInTheDocument();
    expect(screen.getByText('The last download failed')).toBeInTheDocument();
    expect(screen.getByText('USD: HTTP 500')).toBeInTheDocument();
    expect(
      within(screen.getByRole('row', { name: /^USD/ })).getByText('No rates'),
    ).toBeInTheDocument();
  });

  it('says so when the installation does not download rates', async () => {
    stubCurrencies({ 'GET /exchange-rates/status': { ...STATUS, enabled: false } });
    renderApp('/settings?tab=currencies');

    expect(
      await screen.findByText(
        'This installation does not download exchange rates: type them by hand below.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Update now' })).not.toBeInTheDocument();
  });

  it('lists the rates typed by hand next to the downloaded one, and adds one', async () => {
    const calls = stubCurrencies({
      'PUT /exchange-rates/manual/GBP/2026-10-01': {
        currencyCode: 'GBP',
        date: '2026-10-01',
        rate: 0.8512,
        downloadedRate: null,
      },
    });
    renderApp('/settings?tab=currencies');

    const row = within(await screen.findByRole('row', { name: /1 EUR = 1.0850 USD/ }));
    expect(row.getByText('01/09/2026')).toBeInTheDocument();
    expect(row.getByText('1.0912')).toBeInTheDocument();

    const form = within(screen.getByRole('form', { name: 'Add rate' }));
    // Not the euro: rates are per euro.
    const currency = form.getByRole('combobox', { name: 'Currency' });
    await userEvent.click(currency);
    const options = within(
      await waitFor(() => document.getElementById(currency.getAttribute('aria-controls') ?? '')!),
    );
    expect(options.queryByRole('option', { name: 'EUR', hidden: true })).not.toBeInTheDocument();
    await userEvent.click(options.getByRole('option', { name: 'GBP', hidden: true }));
    const date = form.getByLabelText(/^Date/);
    await userEvent.clear(date);
    await userEvent.type(date, '2026-10-01');
    await userEvent.click(form.getByRole('button', { name: 'Add rate' }));
    expect(await form.findByText('Enter a rate greater than zero')).toBeInTheDocument();
    expect(sent(calls, 'PUT')).toHaveLength(0);

    await userEvent.type(form.getByRole('textbox', { name: '1 EUR in GBP' }), '0,8512');
    await userEvent.click(form.getByRole('button', { name: 'Add rate' }));

    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    expect(sent(calls, 'PUT')[0]?.body).toEqual({ rate: 0.8512 });
    expect(await screen.findByText('Exchange rate saved')).toBeInTheDocument();
  });

  it('deletes a rate typed by hand after asking', async () => {
    const calls = stubCurrencies({
      'DELETE /exchange-rates/manual/USD/2026-09-01': () => new Response(null, { status: 204 }),
    });
    renderApp('/settings?tab=currencies');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Delete 1 EUR = 1.0850 USD of 01/09/2026' }),
    );
    const dialog = within(await screen.findByRole('dialog', { name: 'Delete exchange rate' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(sent(calls, 'DELETE')).toHaveLength(1));
    expect(await screen.findByText('Exchange rate deleted')).toBeInTheDocument();
  });

  it('says so when the currencies cannot be loaded', async () => {
    stubCurrencies({
      'GET /exchange-rates/status': () => problem(500, 'INTERNAL_ERROR', 'Boom'),
    });
    renderApp('/settings?tab=currencies');

    expect(
      await screen.findByText(
        'The data could not be loaded. Check that the API is available and reload the page.',
      ),
    ).toBeInTheDocument();
  });
});

describe('Settings: currencies on a phone', () => {
  onANarrowScreen();

  it('leaves the downloaded rate out on a phone', async () => {
    stubCurrencies();
    renderApp('/settings?tab=currencies');

    const row = within(await screen.findByRole('row', { name: /1 EUR = 1.0850 USD/ }));
    expect(row.queryByText('1.0912')).not.toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Show mixed currencies in' })).toBeInTheDocument();
  });
});
