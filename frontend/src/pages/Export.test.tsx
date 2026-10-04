import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { BankrollSummary } from '../api/types';
import i18n from '../i18n';
import { bankrollSummary, game, page, ROOMS, VARIANTS } from '../test/fixtures';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/** The files the browser was asked to save: the name given to each and its content. */
let saved: { name: string; blob: Blob }[];

beforeEach(() => {
  saved = [];
  // jsdom has no object URLs and does not download: the link clicked tells what would be saved.
  const blobs = new Map<string, Blob>();
  URL.createObjectURL = vi.fn((blob: Blob) => {
    const url = `blob:file-${blobs.size}`;
    blobs.set(url, blob);
    return url;
  });
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    saved.push({ name: this.download, blob: blobs.get(this.href)! });
  });
});

/** A file as the backend sends it. */
function file(content: string, contentType: string, name: string) {
  return new Response(content, {
    status: 200,
    headers: {
      'Content-Type': contentType,
      'Content-Disposition': `attachment; filename="${name}"`,
    },
  });
}

/** The parameters of a request; one sent several times (a list) has its values joined by commas. */
function queryOf(call: ApiCall): Record<string, string> {
  const query: Record<string, string> = {};
  for (const key of new Set(call.query.keys())) {
    query[key] = call.query.getAll(key).join(',');
  }
  return query;
}

function exportsOf(calls: ApiCall[], what: 'games' | 'movements') {
  return calls.filter((call) => call.path === `/exports/${what}`);
}

/** Opens the menu of the button and picks a format. */
async function exportAs(button: string, format: 'CSV' | 'Excel') {
  const user = userEvent.setup();
  await user.click(await screen.findByRole('button', { name: button }));
  await user.click(await screen.findByRole('menuitem', { name: format, hidden: true }));
}

function stubGames(handlers: Record<string, unknown>) {
  return stubApi({
    'GET /rooms': ROOMS,
    'GET /variants': VARIANTS,
    'GET /games': (call: ApiCall) =>
      call.query.get('status') === 'IN_PLAY'
        ? page([game({ id: 1, status: 'IN_PLAY' })])
        : page([game({ id: 2 })], { totalItems: 80, totalPages: 4 }),
    ...handlers,
  });
}

describe('Export of games', () => {
  it('asks for every game of the filters of the list, in the format chosen, and saves the file', async () => {
    const calls = stubGames({
      'GET /exports/games': () => file('xlsx', XLSX, 'poker-bankroll-games-2026-10-02.xlsx'),
    });
    renderApp(
      '/games?from=2026-01-01&to=2026-01-31&type=TOURNAMENT,CASH&room=1,2&variant=10&q=fish&sort=net,asc&page=2',
    );

    await exportAs('Export games', 'Excel');

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(exportsOf(calls, 'games').map(queryOf)).toEqual([
      // The filters of the table, but neither its page nor its order; nor a status: the
      // backend only exports finished games.
      {
        format: 'XLSX',
        from: '2026-01-01',
        to: '2026-01-31',
        gameType: 'TOURNAMENT,CASH',
        roomId: '1,2',
        variantId: '10',
        q: 'fish',
      },
    ]);
    // The Excel file is written in the language of the interface.
    expect(exportsOf(calls, 'games')[0]!.headers.get('Accept-Language')).toBe('en');
    expect(saved[0]!.name).toBe('poker-bankroll-games-2026-10-02.xlsx');
    expect(await saved[0]!.blob.text()).toBe('xlsx');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:file-0');
  });

  it('exports as CSV without filters when the list has none', async () => {
    const calls = stubGames({
      'GET /exports/games': () =>
        file('playedOn,room\r\n', 'text/csv;charset=UTF-8', 'poker-bankroll-games-2026-10-02.csv'),
    });
    renderApp('/games');

    await exportAs('Export games', 'CSV');

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(exportsOf(calls, 'games').map(queryOf)).toEqual([{ format: 'CSV' }]);
    expect(saved[0]!.name).toBe('poker-bankroll-games-2026-10-02.csv');
  });

  it('tells that games in play are left out, before and after exporting', async () => {
    stubGames({
      'GET /exports/games': () => file('', 'text/csv', 'poker-bankroll-games-2026-10-02.csv'),
    });
    renderApp('/games');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Export games' }));

    expect(
      await screen.findByText(
        'Only finished games are exported, with the filters of the list. Games in play are left out.',
      ),
    ).toBeInTheDocument();

    await user.click(await screen.findByRole('menuitem', { name: 'CSV', hidden: true }));

    expect(await screen.findByText('File exported')).toBeInTheDocument();
    expect(
      screen.getByText(
        'poker-bankroll-games-2026-10-02.csv has the finished games of the list. Games in play are not in the file.',
      ),
    ).toBeInTheDocument();
  });

  it('sends the language of the interface and names the file itself when the backend does not', async () => {
    await i18n.changeLanguage('es');
    const calls = stubGames({
      'GET /exports/games': () => new Response('xlsx', { headers: { 'Content-Type': XLSX } }),
    });
    renderApp('/games');

    await exportAs('Exportar partidas', 'Excel');

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(exportsOf(calls, 'games')[0]!.headers.get('Accept-Language')).toBe('es');
    expect(saved[0]!.name).toBe('poker-bankroll-games.xlsx');
    expect(await screen.findByText('Fichero exportado')).toBeInTheDocument();
  });

  it('shows why the export failed and saves nothing', async () => {
    stubGames({
      'GET /exports/games': () => problem(500, 'INTERNAL_ERROR', 'An unexpected error occurred.'),
    });
    renderApp('/games');

    await exportAs('Export games', 'Excel');

    expect(await screen.findByText('The file was not exported')).toBeInTheDocument();
    expect(screen.getByText('An unexpected error occurred.')).toBeInTheDocument();
    expect(saved).toHaveLength(0);
    expect(screen.queryByText('File exported')).not.toBeInTheDocument();
  });
});

describe('Export of movements', () => {
  const figures = {
    deposited: 100,
    withdrawn: 0,
    bonuses: 0,
    adjustments: 0,
    gamesNet: 0,
    result: 0,
    bankroll: 100,
    ticketsWon: 0,
    gamesInPlay: 0,
    investedInPlay: 0,
  };
  const SUMMARY: BankrollSummary = bankrollSummary([
    { currencyCode: 'EUR', total: figures, withoutRoom: figures, rooms: [] },
    { currencyCode: 'USD', total: figures, withoutRoom: figures, rooms: [] },
  ]);

  it('asks for every movement of the filters of the list, in the format chosen, and saves the file', async () => {
    const calls = stubApi({
      'GET /rooms': ROOMS,
      'GET /bankroll/summary': SUMMARY,
      'GET /exports/movements': () =>
        file('occurredOn\r\n', 'text/csv;charset=UTF-8', 'poker-bankroll-movements-2026-10-02.csv'),
    });
    renderApp('/bankroll?from=2026-01-01&to=2026-03-31&room=1,3&type=WITHDRAWAL&page=2');

    await exportAs('Export movements', 'CSV');

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(exportsOf(calls, 'movements').map(queryOf)).toEqual([
      {
        format: 'CSV',
        from: '2026-01-01',
        to: '2026-03-31',
        type: 'WITHDRAWAL',
        roomId: '1,3',
      },
    ]);
    expect(saved[0]!.name).toBe('poker-bankroll-movements-2026-10-02.csv');
    expect(await screen.findByText('File exported')).toBeInTheDocument();
    expect(
      screen.getByText('poker-bankroll-movements-2026-10-02.csv has the movements of the list.'),
    ).toBeInTheDocument();
  });

  it('exports every currency as Excel, and shows a failure', async () => {
    let fail = false;
    const calls = stubApi({
      'GET /rooms': ROOMS,
      'GET /bankroll/summary': SUMMARY,
      'GET /exports/movements': () =>
        fail
          ? problem(500, 'INTERNAL_ERROR', 'An unexpected error occurred.')
          : file('xlsx', XLSX, 'poker-bankroll-movements-2026-10-02.xlsx'),
    });
    renderApp('/bankroll');

    await exportAs('Export movements', 'Excel');

    await waitFor(() => expect(saved).toHaveLength(1));
    // The list holds the movements of every currency, each in its own.
    expect(exportsOf(calls, 'movements').map(queryOf)).toEqual([{ format: 'XLSX' }]);

    fail = true;
    await exportAs('Export movements', 'Excel');

    expect(await screen.findByText('The file was not exported')).toBeInTheDocument();
    expect(saved).toHaveLength(1);
  });
});
