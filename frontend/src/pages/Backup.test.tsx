import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { BackupContents, BackupRestore } from '../api/types';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

const FILE = new File(['{"formatVersion":1}'], 'poker-bankroll-backup-2026-10-02.json', {
  type: 'application/json',
});

const EMPTY: BackupContents = {
  rooms: 0,
  variants: 0,
  games: 0,
  gamesInPlay: 0,
  movements: 0,
  templates: 0,
  from: null,
  to: null,
  empty: true,
};

const IN_FILE: BackupContents = {
  rooms: 4,
  variants: 1,
  games: 3100,
  gamesInPlay: 3,
  movements: 12,
  templates: 3,
  from: '2019-03-02',
  to: '2026-01-19',
  empty: false,
};

const INSTALLED: BackupContents = {
  rooms: 2,
  variants: 0,
  games: 57,
  gamesInPlay: 1,
  movements: 5,
  templates: 0,
  from: '2026-05-01',
  to: '2026-09-30',
  empty: false,
};

function result(overrides: Partial<BackupRestore> = {}): BackupRestore {
  return {
    dryRun: true,
    restored: false,
    formatVersion: 1,
    appVersion: '0.2.0',
    exportedAt: '2026-10-02T10:15:30Z',
    file: IN_FILE,
    current: EMPTY,
    errorCount: 0,
    errors: [],
    ...overrides,
  };
}

/** The API answering a check with `checked` and a restore with `restored`. */
function stubBackupApi(checked: unknown, restored?: unknown, more: Record<string, unknown> = {}) {
  return stubApi({
    'POST /backup/restore': (call: ApiCall) =>
      call.query.get('dryRun') === 'true'
        ? checked
        : (restored ?? { ...(checked as BackupRestore), dryRun: false, restored: true }),
    ...more,
  });
}

function restores(calls: ApiCall[]) {
  return calls.filter((call) => call.path === '/backup/restore');
}

async function chooseBackup(file: File = FILE) {
  await screen.findByRole('button', { name: /Choose (backup file|another backup)/ });
  // The second file input of the page: the first one takes the CSV of the import.
  const input = document.querySelector<HTMLInputElement>('input[type="file"][accept*="json"]')!;
  await userEvent.upload(input, file);
}

/** The table of what the file and the installation hold, cell by cell. */
function contentRows() {
  return within(screen.getByRole('table', { name: 'Content of the backup' }))
    .getAllByRole('row')
    .map((row) => Array.from(row.querySelectorAll('th, td')).map((cell) => cell.textContent));
}

/** The files the browser was asked to save. */
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

describe('backup, in the Import / Export section', () => {
  it('is a part of the section, after the import of games', async () => {
    stubBackupApi(result());
    renderApp('/import');

    expect(
      await screen.findByRole('heading', { name: 'Import / Export', level: 2 }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent)).toEqual([
      'Import games from a CSV file',
      'Backup of everything',
    ]);
    expect(screen.getByRole('link', { name: 'Backups and when to use each kind' })).toHaveAttribute(
      'href',
      expect.stringContaining('docs/backups.md'),
    );
    // Nothing to restore until a file is chosen.
    expect(screen.queryByRole('button', { name: 'Restore this backup' })).not.toBeInTheDocument();
  });

  it('downloads the backup with the name the backend gives it', async () => {
    const calls = stubApi({
      'GET /backup': () =>
        new Response('{"formatVersion":1}', {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'Content-Disposition': 'attachment; filename="poker-bankroll-backup-2026-10-02.json"',
          },
        }),
    });
    renderApp('/import');

    await userEvent.click(await screen.findByRole('button', { name: 'Download backup' }));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]?.name).toBe('poker-bankroll-backup-2026-10-02.json');
    expect(await saved[0]?.blob.text()).toBe('{"formatVersion":1}');
    expect(calls.filter((call) => call.path === '/backup')).toHaveLength(1);
    expect(await screen.findByText('Backup downloaded')).toBeInTheDocument();
    expect(
      screen.getByText(
        'poker-bankroll-backup-2026-10-02.json has everything this installation holds.',
      ),
    ).toBeInTheDocument();
  });

  it('says why the backup was not downloaded', async () => {
    stubApi({
      'GET /backup': () => problem(500, 'INTERNAL_ERROR', 'An unexpected error occurred.'),
    });
    renderApp('/import');

    await userEvent.click(await screen.findByRole('button', { name: 'Download backup' }));

    expect(await screen.findByText('The backup was not downloaded')).toBeInTheDocument();
    expect(screen.getByText('An unexpected error occurred.')).toBeInTheDocument();
    expect(saved).toHaveLength(0);
  });

  it('checks the chosen file without restoring it and shows what it holds', async () => {
    const calls = stubBackupApi(result());
    renderApp('/import');
    await chooseBackup();

    expect(
      await screen.findByText('Backup made on 02/10/2026 with poker-bankroll v0.2.0.'),
    ).toBeInTheDocument();
    expect(contentRows()).toEqual([
      ['', 'In the file', 'In this installation'],
      ['Rooms', '4', '0'],
      ['Variants of your own', '1', '0'],
      ['Games', '3,100', '0'],
      ['Games in play', '3', '0'],
      ['Bankroll movements', '12', '0'],
      ['Game templates', '3', '0'],
      ['Dates of the games', 'From 02/03/2019 to 19/01/2026', '—'],
    ]);
    expect(screen.getByText('poker-bankroll-backup-2026-10-02.json')).toBeInTheDocument();
    expect(
      screen.getByText('This installation is empty: nothing will be deleted.'),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Restoring deletes everything this installation holds'),
    ).not.toBeInTheDocument();

    // The file itself was sent, as JSON, only to be checked and without asking to replace.
    expect(restores(calls)).toHaveLength(1);
    expect(restores(calls)[0]?.query.get('dryRun')).toBe('true');
    expect(restores(calls)[0]?.query.get('replace')).toBe('false');
    expect(restores(calls)[0]?.rawBody).toBe(FILE);
    expect(restores(calls)[0]?.headers.get('Content-Type')).toBe('application/json');
    expect(screen.getByRole('button', { name: 'Restore this backup' })).toBeEnabled();
  });

  it('restores into an empty installation after a plain confirmation, then is left without file', async () => {
    const calls = stubBackupApi(result());
    renderApp('/import');
    await chooseBackup();
    await userEvent.click(await screen.findByRole('button', { name: 'Restore this backup' }));

    const dialog = within(await screen.findByRole('dialog', { name: 'Restore backup' }));
    expect(
      dialog.getByText(
        'poker-bankroll-backup-2026-10-02.json will be restored: 4 rooms, 1 variant of your own, 3,100 games, 12 bankroll movements.',
      ),
    ).toBeInTheDocument();
    // Nothing is deleted: there is nothing to tick.
    expect(dialog.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(restores(calls)).toHaveLength(1);
    // What the page has loaded (here, only the state of the API in the header).
    const loaded = () => calls.filter((call) => call.method === 'GET').length;
    const loadedBefore = loaded();
    await userEvent.click(dialog.getByRole('button', { name: 'Restore' }));

    expect(await screen.findByText('Backup restored')).toBeInTheDocument();
    expect(
      screen.getByText(
        'This installation now holds what poker-bankroll-backup-2026-10-02.json holds: 4 rooms, 1 variant of your own, 3,100 games, 12 bankroll movements.',
      ),
    ).toBeInTheDocument();
    expect(restores(calls)).toHaveLength(2);
    expect(restores(calls)[1]?.query.get('dryRun')).toBe('false');
    expect(restores(calls)[1]?.query.get('replace')).toBe('false');
    expect(restores(calls)[1]?.rawBody).toBe(FILE);
    // The file is gone: nothing to restore again by mistake.
    expect(screen.queryByRole('button', { name: 'Restore this backup' })).not.toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Content of the backup' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Choose backup file' })).toBeEnabled();
    expect(screen.getByRole('link', { name: 'See the games' })).toHaveAttribute('href', '/games');
    // Everything loaded before is loaded again: it is another installation now.
    await waitFor(() => expect(loaded()).toBeGreaterThan(loadedBefore));
  });

  it('warns about what will be deleted and only replaces it after ticking the box', async () => {
    const calls = stubBackupApi(result({ current: INSTALLED }));
    renderApp('/import');
    await chooseBackup();

    const warning = await screen.findByRole('alert');
    expect(
      within(warning).getByText('Restoring deletes everything this installation holds'),
    ).toBeInTheDocument();
    expect(
      within(warning).getByText(/2 rooms, 0 variants of your own, 57 games, 5 bankroll movements/),
    ).toBeInTheDocument();
    expect(contentRows()[3]).toEqual(['Games', '3,100', '57']);

    await userEvent.click(screen.getByRole('button', { name: 'Restore this backup' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Restore backup' }));
    expect(
      dialog.getByText(
        'Everything this installation holds now will be deleted for good: 2 rooms, 0 variants of your own, 57 games, 5 bankroll movements.',
      ),
    ).toBeInTheDocument();
    const confirm = dialog.getByRole('button', { name: 'Delete and restore' });
    expect(confirm).toBeDisabled();
    await userEvent.click(confirm);
    expect(restores(calls)).toHaveLength(1);

    await userEvent.click(
      dialog.getByRole('checkbox', {
        name: 'I understand that the data of this installation will be deleted',
      }),
    );
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);

    expect(await screen.findByText('Backup restored')).toBeInTheDocument();
    expect(restores(calls)).toHaveLength(2);
    expect(restores(calls)[1]?.query.get('dryRun')).toBe('false');
    expect(restores(calls)[1]?.query.get('replace')).toBe('true');
  });

  it('asks again for the box every time the confirmation opens, and restores nothing when cancelled', async () => {
    const calls = stubBackupApi(result({ current: INSTALLED }));
    renderApp('/import');
    await chooseBackup();

    await userEvent.click(await screen.findByRole('button', { name: 'Restore this backup' }));
    let dialog = within(await screen.findByRole('dialog', { name: 'Restore backup' }));
    await userEvent.click(dialog.getByRole('checkbox'));
    await userEvent.click(dialog.getByRole('button', { name: 'Cancel' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Restore backup' })).not.toBeInTheDocument(),
    );

    await userEvent.click(screen.getByRole('button', { name: 'Restore this backup' }));
    dialog = within(await screen.findByRole('dialog', { name: 'Restore backup' }));
    expect(dialog.getByRole('checkbox')).not.toBeChecked();
    expect(dialog.getByRole('button', { name: 'Delete and restore' })).toBeDisabled();
    expect(restores(calls)).toHaveLength(1);
    expect(screen.getByText('poker-bankroll-backup-2026-10-02.json')).toBeInTheDocument();
  });

  it('lists the errors of the file and does not offer the restore', async () => {
    stubBackupApi(
      result({
        errorCount: 203,
        errors: [
          { path: 'games[12].buyIn', code: 'DecimalMin', message: 'must be greater than 0' },
          { path: 'rooms[0].currencyCode', code: 'UNKNOWN_CURRENCY', message: 'No such currency.' },
        ],
      }),
    );
    renderApp('/import');
    await chooseBackup();

    expect(
      await screen.findByText('203 errors: nothing is restored until the file is right'),
    ).toBeInTheDocument();
    const rows = within(screen.getByRole('table', { name: 'Error' })).getAllByRole('row');
    expect(rows.map((row) => row.textContent)).toEqual([
      'WhereError',
      'games[12].buyInmust be greater than 0',
      'rooms[0].currencyCodeNo such currency.',
    ]);
    expect(screen.getByText('And 201 more errors.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Restore this backup' })).toBeDisabled();
  });

  it('shows why a file cannot be read and takes another one', async () => {
    let answer: unknown = problem(
      400,
      'BACKUP_FORMAT_TOO_NEW',
      'The backup was made by a newer version of poker-bankroll.',
    );
    const calls = stubApi({ 'POST /backup/restore': () => answer });
    renderApp('/import');
    await chooseBackup();

    expect(await screen.findByText('The file cannot be restored')).toBeInTheDocument();
    expect(
      screen.getByText('The backup was made by a newer version of poker-bankroll.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Restore this backup' })).not.toBeInTheDocument();

    answer = result();
    await chooseBackup();

    expect(await screen.findByRole('button', { name: 'Restore this backup' })).toBeEnabled();
    expect(screen.queryByText('The file cannot be restored')).not.toBeInTheDocument();
    expect(restores(calls)).toHaveLength(2);
  });

  it('keeps the file and says why when the restore is refused', async () => {
    // Empty when the file was checked, with data when it is restored.
    stubBackupApi(
      result(),
      problem(409, 'BACKUP_REPLACE_NOT_CONFIRMED', 'The installation already has data.'),
    );
    renderApp('/import');
    await chooseBackup();
    await userEvent.click(await screen.findByRole('button', { name: 'Restore this backup' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Restore backup' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Restore' }));

    expect(await dialog.findByText('The installation already has data.')).toBeInTheDocument();
    expect(screen.queryByText('Backup restored')).not.toBeInTheDocument();
    expect(screen.getByText('poker-bankroll-backup-2026-10-02.json')).toBeInTheDocument();
  });

  it('shows the errors found when restoring a file that was right when checked', async () => {
    stubBackupApi(
      result(),
      result({
        dryRun: false,
        errorCount: 1,
        errors: [{ path: 'rooms[1].logo.content', code: 'LOGO_TOO_LARGE', message: 'Too large.' }],
      }),
    );
    renderApp('/import');
    await chooseBackup();
    await userEvent.click(await screen.findByRole('button', { name: 'Restore this backup' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Restore backup' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Restore' }));

    expect(await screen.findByText('Too large.')).toBeInTheDocument();
    expect(screen.queryByText('Backup restored')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Restore this backup' })).toBeDisabled();
  });
});
