import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type { GameImport } from '../api/types';
import { page } from '../test/fixtures';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

const FILE = new File(['playedOn,room,gameType,buyIn\n'], 'games.csv', { type: 'text/csv' });

function result(overrides: Partial<GameImport> = {}): GameImport {
  return {
    dryRun: true,
    imported: false,
    rows: 3100,
    games: 3100,
    gamesByType: [
      { gameType: 'TOURNAMENT', games: 2000 },
      { gameType: 'SIT_AND_GO', games: 1100 },
    ],
    from: '2019-03-02',
    to: '2026-01-19',
    totals: [
      { currencyCode: 'EUR', games: 3000, net: 1234.5 },
      { currencyCode: 'USD', games: 100, net: -20 },
    ],
    newRooms: [],
    newVariants: [],
    errorCount: 0,
    errors: [],
    ...overrides,
  };
}

/** The API answering a check with `checked` and an import with `imported`. */
function stubImportApi(
  checked: unknown,
  imported: unknown = result({ dryRun: false, imported: true }),
) {
  return stubApi({
    'GET /games': page([]),
    'POST /imports/games': (call: ApiCall) =>
      call.query.get('dryRun') === 'true' ? checked : imported,
  });
}

function imports(calls: ApiCall[]) {
  return calls.filter((call) => call.path === '/imports/games');
}

async function chooseFile(file: File = FILE) {
  await screen.findByRole('button', { name: /Choose (CSV|another) file/ });
  await userEvent.upload(document.querySelector<HTMLInputElement>('input[type="file"]')!, file);
}

async function confirmImport() {
  const dialog = within(await screen.findByRole('dialog', { name: 'Import games' }));
  await userEvent.click(dialog.getByRole('button', { name: 'Import' }));
}

describe('import page', () => {
  it('explains what is imported and links to the format and an example', async () => {
    stubImportApi(result());
    renderApp('/import');

    expect(await screen.findByRole('heading', { name: 'Import / Export' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'See the format' })).toHaveAttribute(
      'href',
      expect.stringContaining('docs/import.md'),
    );
    const example = screen.getByRole('link', { name: 'Download an example' });
    expect(example).toHaveAttribute('href', '/import-example.csv');
    expect(example).toHaveAttribute('download');
    // Nothing to import until a file is chosen.
    expect(screen.queryByRole('button', { name: /^Import / })).not.toBeInTheDocument();
  });

  it('checks the chosen file without importing it and shows what it holds', async () => {
    const calls = stubImportApi(
      result({
        newRooms: [{ name: 'PokerStars', currencyCode: 'USD' }],
        newVariants: [{ gameType: 'TOURNAMENT', name: 'Deep Stack' }],
      }),
    );
    renderApp('/import');
    await chooseFile();

    const games = within(await screen.findByRole('region', { name: 'Games to import' }));
    expect(games.getByText('3,100')).toBeInTheDocument();
    expect(games.getByText('Tournament: 2,000')).toBeInTheDocument();
    expect(games.getByText('Sit & Go / Spin: 1,100')).toBeInTheDocument();
    expect(games.getByText(/^From .*2019 to .*2026$/)).toBeInTheDocument();
    // One figure per currency: they are never added.
    expect(
      within(screen.getByRole('region', { name: 'Net in EUR' })).getByText('+€1,234.50'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('region', { name: 'Net in USD' })).getByText('-US$20.00'),
    ).toBeInTheDocument();
    expect(screen.getByText('PokerStars (USD)')).toBeInTheDocument();
    expect(screen.getByText('Deep Stack (Tournament)')).toBeInTheDocument();
    expect(screen.getByText('games.csv')).toBeInTheDocument();

    // The file itself was sent, as CSV, and only to be checked.
    expect(imports(calls)).toHaveLength(1);
    expect(imports(calls)[0]?.query.get('dryRun')).toBe('true');
    expect(imports(calls)[0]?.rawBody).toBe(FILE);
    expect(imports(calls)[0]?.headers.get('Content-Type')).toBe('text/csv');
    expect(screen.getByRole('button', { name: 'Import 3,100 games' })).toBeEnabled();
  });

  it('imports after confirming, then is ready for another file', async () => {
    const calls = stubImportApi(result());
    renderApp('/import');
    await chooseFile();

    await userEvent.click(await screen.findByRole('button', { name: 'Import 3,100 games' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Import games' }));
    expect(dialog.getByText('3,100 games of games.csv will be recorded.')).toBeInTheDocument();
    expect(imports(calls)).toHaveLength(1);
    await userEvent.click(dialog.getByRole('button', { name: 'Import' }));

    expect(await screen.findByText('3,100 games imported.')).toBeInTheDocument();
    expect(imports(calls)).toHaveLength(2);
    expect(imports(calls)[1]?.query.get('dryRun')).toBe('false');
    expect(imports(calls)[1]?.rawBody).toBe(FILE);
    // The file is gone: nothing to import again by mistake.
    expect(screen.queryByText('games.csv')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Import / })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Choose CSV file' })).toBeEnabled();
    expect(screen.getByRole('link', { name: 'See the games' })).toHaveAttribute('href', '/games');
  });

  it('does not import without confirming', async () => {
    const calls = stubImportApi(result());
    renderApp('/import');
    await chooseFile();

    await userEvent.click(await screen.findByRole('button', { name: 'Import 3,100 games' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Import games' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Cancel' }));

    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Import games' })).not.toBeInTheDocument(),
    );
    expect(imports(calls)).toHaveLength(1);
    expect(screen.getByText('games.csv')).toBeInTheDocument();
  });

  it('lists the errors of the rows and does not offer the import', async () => {
    stubImportApi(
      result({
        games: 3098,
        errorCount: 203,
        errors: [
          { row: 2, field: 'playedOn', code: 'INVALID_DATE', message: 'That is not a date.' },
          { row: 7, field: null, code: 'COLUMN_COUNT', message: 'The row has too many values.' },
        ],
      }),
    );
    renderApp('/import');
    await chooseFile();

    expect(
      await screen.findByText('203 errors: nothing is imported until the file is right'),
    ).toBeInTheDocument();
    const rows = within(screen.getByRole('table', { name: 'Error' })).getAllByRole('row');
    expect(rows.map((row) => row.textContent)).toEqual([
      'RowColumnError',
      '2playedOnThat is not a date.',
      '7—The row has too many values.',
    ]);
    expect(screen.getByText('And 201 more errors.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Import 3,098 games' })).toBeDisabled();
  });

  it('shows why a file cannot be read and takes another one', async () => {
    let answer: unknown = problem(400, 'IMPORT_UNKNOWN_COLUMN', 'The column is not in the format.');
    const calls = stubApi({ 'POST /imports/games': () => answer });
    renderApp('/import');
    await chooseFile();

    expect(await screen.findByText('The column is not in the format.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Import / })).not.toBeInTheDocument();

    // The same file, corrected, is chosen again.
    answer = result({ rows: 1, games: 1 });
    await chooseFile();

    expect(await screen.findByRole('button', { name: 'Import 1 game' })).toBeEnabled();
    expect(screen.queryByText('The column is not in the format.')).not.toBeInTheDocument();
    expect(imports(calls)).toHaveLength(2);
  });

  it('shows the errors found when importing a file that was right when checked', async () => {
    stubImportApi(
      result(),
      result({
        dryRun: false,
        games: 3099,
        errorCount: 1,
        errors: [{ row: 5, field: 'room', code: 'ROOM_INACTIVE', message: 'Unibet is inactive.' }],
      }),
    );
    renderApp('/import');
    await chooseFile();
    await userEvent.click(await screen.findByRole('button', { name: 'Import 3,100 games' }));
    await confirmImport();

    expect(await screen.findByText('Unibet is inactive.')).toBeInTheDocument();
    expect(screen.queryByText(/games imported/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Import 3,099 games' })).toBeDisabled();
  });
});
