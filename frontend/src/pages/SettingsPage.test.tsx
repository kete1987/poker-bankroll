import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Room, Variant } from '../api/types';
import { resizeImage } from '../settings/resizeImage';
import { room, VARIANTS } from '../test/fixtures';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

// Canvas is not available in jsdom: the resizing is replaced by a stand-in for its result.
vi.mock('../settings/resizeImage', () => ({ resizeImage: vi.fn() }));

const RESIZED = new Blob(['small'], { type: 'image/png' });

const ROOMS: Room[] = [
  room({ id: 2, name: 'PokerStars', currencyCode: 'USD', inUse: false, logoVersion: null }),
  room({ id: 3, name: 'Unibet', active: false, logoVersion: 'abc' }),
  room({ id: 1, name: 'Winamax', logoVersion: null }),
];

function stubSettings(handlers: Record<string, unknown> = {}) {
  return stubApi({ 'GET /rooms': ROOMS, 'GET /variants': VARIANTS, ...handlers });
}

function sent(calls: ApiCall[], method: string) {
  return calls.filter((call) => call.method === method);
}

function rowOf(name: string) {
  const row = screen.getAllByRole('row').find((candidate) => within(candidate).queryByText(name));
  expect(row).toBeDefined();
  return within(row!);
}

beforeEach(() => {
  vi.mocked(resizeImage).mockReset().mockResolvedValue(RESIZED);
  // jsdom has no object URLs: the preview of a logo not stored yet gets a fixed one.
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

describe('Settings: rooms', () => {
  it('lists every room with its currency and whether it is active', async () => {
    stubSettings();
    renderApp('/settings');

    expect(await screen.findByRole('tab', { name: 'Rooms' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    const rows = (await screen.findAllByRole('row')).slice(1);
    expect(rows.map((row) => row.textContent)).toEqual([
      expect.stringContaining('PokerStarsUSD'),
      expect.stringContaining('UnibetEUR'),
      expect.stringContaining('WinamaxEUR'),
    ]);
    expect(screen.getByRole('switch', { name: 'Winamax is active' })).toBeChecked();
    expect(screen.getByRole('switch', { name: 'Unibet is active' })).not.toBeChecked();
    // A room with history cannot be deleted, only deactivated.
    const blocked = screen.getByRole('button', { name: 'Delete Winamax' });
    expect(blocked).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('button', { name: 'Delete PokerStars' })).toHaveAttribute(
      'aria-disabled',
      'false',
    );
    // It says why, and does nothing.
    await userEvent.hover(blocked);
    expect(
      await screen.findByText('It has games or movements: deactivate it instead.'),
    ).toBeInTheDocument();
    await userEvent.click(blocked);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('activates and deactivates a room from its row', async () => {
    const calls = stubSettings({
      'PUT /rooms/1': room({ id: 1, name: 'Winamax', active: false }),
    });
    renderApp('/settings');

    await userEvent.click(await screen.findByRole('switch', { name: 'Winamax is active' }));

    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    expect(sent(calls, 'PUT')[0]).toMatchObject({
      path: '/rooms/1',
      body: { name: 'Winamax', currencyCode: 'EUR', active: false },
    });
  });

  it('adds a room without a logo', async () => {
    const created = room({ id: 9, name: '888poker', currencyCode: 'USD', inUse: false });
    const calls = stubSettings({ 'POST /rooms': created });
    renderApp('/settings');

    await userEvent.click(await screen.findByRole('button', { name: 'Add room' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Add room' }));
    // The name is required.
    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));
    expect(await dialog.findByText('Required')).toBeInTheDocument();
    expect(sent(calls, 'POST')).toHaveLength(0);

    await userEvent.type(dialog.getByRole('textbox', { name: 'Name' }), ' 888poker ');
    await userEvent.click(dialog.getByRole('combobox', { name: 'Currency' }));
    await userEvent.click(await screen.findByRole('option', { name: 'USD', hidden: true }));
    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]?.body).toEqual({
      name: '888poker',
      currencyCode: 'USD',
      active: true,
    });
    expect(await screen.findByText('Room created')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    // No logo was chosen: none is sent.
    expect(sent(calls, 'PUT')).toHaveLength(0);
  });

  it('adds a room with a logo, stored once the room exists', async () => {
    const created = room({ id: 9, name: '888poker', inUse: false, logoVersion: null });
    const calls = stubSettings({
      'POST /rooms': created,
      'PUT /rooms/9/logo': { ...created, logoVersion: 'v1' },
    });
    renderApp('/settings');
    await userEvent.click(await screen.findByRole('button', { name: 'Add room' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Add room' }));
    const logo = within(dialog.getByRole('region', { name: 'Logo' }));
    await userEvent.type(dialog.getByRole('textbox', { name: 'Name' }), '888poker');

    const original = new File(['a very large image'], 'logo.jpg', { type: 'image/jpeg' });
    await userEvent.upload(
      document.querySelector<HTMLInputElement>('input[type="file"]')!,
      original,
    );

    // Resized and previewed, but nothing is sent until the room is saved.
    expect(await logo.findByRole('img', { name: 'Logo of 888poker' })).toHaveAttribute(
      'src',
      'blob:preview',
    );
    expect(resizeImage).toHaveBeenCalledWith(original);
    expect(sent(calls, 'POST')).toHaveLength(0);
    expect(sent(calls, 'PUT')).toHaveLength(0);

    // It can still be taken back, and chosen again.
    await userEvent.click(logo.getByRole('button', { name: 'Remove logo' }));
    expect(logo.queryByRole('img', { name: 'Logo of 888poker' })).not.toBeInTheDocument();
    await userEvent.upload(
      document.querySelector<HTMLInputElement>('input[type="file"]')!,
      original,
    );
    await logo.findByRole('img', { name: 'Logo of 888poker' });

    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    expect(sent(calls, 'POST')).toHaveLength(1);
    expect(sent(calls, 'PUT')[0]?.path).toBe('/rooms/9/logo');
    expect(await screen.findByText('Room created')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('keeps the new room when its logo cannot be stored, and says so', async () => {
    const created = room({ id: 9, name: '888poker', inUse: false, logoVersion: null });
    stubSettings({
      'POST /rooms': created,
      'PUT /rooms/9/logo': () => problem(413, 'LOGO_TOO_LARGE', 'The logo is too large.'),
    });
    renderApp('/settings');
    await userEvent.click(await screen.findByRole('button', { name: 'Add room' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Add room' }));
    await userEvent.type(dialog.getByRole('textbox', { name: 'Name' }), '888poker');
    await userEvent.upload(
      document.querySelector<HTMLInputElement>('input[type="file"]')!,
      new File(['image'], 'logo.png', { type: 'image/png' }),
    );
    await within(dialog.getByRole('region', { name: 'Logo' })).findByRole('img');

    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Room created')).toBeInTheDocument();
    expect(screen.getByText('The logo was not stored')).toBeInTheDocument();
    expect(screen.getByText('The logo is too large.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('edits a room; its currency is locked once it has history', async () => {
    const calls = stubSettings({ 'PUT /rooms/1': room({ id: 1, name: 'Winamax.es' }) });
    renderApp('/settings');

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Winamax' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Edit room' }));
    expect(dialog.getByRole('combobox', { name: 'Currency' })).toBeDisabled();
    expect(
      dialog.getByText('It cannot change: the room already has games or movements.'),
    ).toBeInTheDocument();

    await userEvent.clear(dialog.getByRole('textbox', { name: 'Name' }));
    await userEvent.type(dialog.getByRole('textbox', { name: 'Name' }), 'Winamax.es');
    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    expect(sent(calls, 'PUT')[0]?.body).toEqual({
      name: 'Winamax.es',
      currencyCode: 'EUR',
      active: true,
    });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('shows why a room could not be saved', async () => {
    stubSettings({
      'PUT /rooms/1': () =>
        problem(409, 'ROOM_NAME_TAKEN', 'There is already a room called Unibet.'),
    });
    renderApp('/settings');

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Winamax' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Edit room' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));

    expect(await dialog.findByText('There is already a room called Unibet.')).toBeInTheDocument();
  });

  it('shows the validation errors of the backend next to their fields', async () => {
    stubSettings({
      'PUT /rooms/1': () =>
        problem(400, 'VALIDATION_FAILED', 'The request contains invalid data.', [
          { field: 'name', code: 'Size', message: 'Too long' },
        ]),
    });
    renderApp('/settings');

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Winamax' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Edit room' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));

    expect(await dialog.findByText('Too long')).toBeInTheDocument();
    expect(dialog.getByRole('textbox', { name: 'Name' })).toBeInvalid();
    expect(dialog.queryByText('The request contains invalid data.')).not.toBeInTheDocument();
  });

  it('does not let a room be edited while its switch is being saved', async () => {
    let finish: (value: unknown) => void = () => {};
    const pending = new Promise((resolve) => {
      finish = resolve;
    });
    stubSettings({ 'PUT /rooms/1': () => pending });
    renderApp('/settings');

    await userEvent.click(await screen.findByRole('switch', { name: 'Winamax is active' }));

    // The dialog would open with the room as it was before the change.
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Edit Winamax' })).toBeDisabled(),
    );
    expect(screen.getByRole('switch', { name: 'Unibet is active' })).toBeDisabled();

    finish(room({ id: 1, name: 'Winamax', active: false }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Edit Winamax' })).toBeEnabled());
  });

  it('deletes an unused room after asking', async () => {
    const calls = stubSettings({ 'DELETE /rooms/2': () => new Response(null, { status: 204 }) });
    renderApp('/settings');

    await userEvent.click(await screen.findByRole('button', { name: 'Delete PokerStars' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Delete room' }));
    expect(
      dialog.getByText('Delete the room PokerStars? This cannot be undone.'),
    ).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(sent(calls, 'DELETE')).toHaveLength(1));
    expect(sent(calls, 'DELETE')[0]?.path).toBe('/rooms/2');
    expect(await screen.findByText('Room deleted')).toBeInTheDocument();
  });

  it('uploads a logo resized in the browser', async () => {
    const calls = stubSettings({
      'PUT /rooms/1/logo': room({ id: 1, name: 'Winamax', logoVersion: 'v2' }),
    });
    renderApp('/settings');
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Winamax' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Edit room' }));
    const logo = within(dialog.getByRole('region', { name: 'Logo' }));
    expect(logo.queryByRole('button', { name: 'Remove logo' })).not.toBeInTheDocument();

    const original = new File(['a very large image'], 'logo.jpg', { type: 'image/jpeg' });
    const input = document.querySelector<HTMLInputElement>('input[type="file"]');
    await userEvent.upload(input!, original);

    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    // What is sent is the resized image, not the file that was chosen.
    expect(resizeImage).toHaveBeenCalledWith(original);
    expect(sent(calls, 'PUT')[0]?.path).toBe('/rooms/1/logo');
    expect(await logo.findByRole('img', { name: 'Logo of Winamax' })).toHaveAttribute(
      'src',
      '/api/rooms/1/logo?v=v2',
    );
    expect(logo.getByRole('button', { name: 'Change logo' })).toBeInTheDocument();
    expect(logo.getByRole('button', { name: 'Remove logo' })).toBeInTheDocument();
  });

  it('removes a logo', async () => {
    const calls = stubSettings({
      'DELETE /rooms/3/logo': () => new Response(null, { status: 204 }),
    });
    renderApp('/settings');
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Unibet' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Edit room' }));
    const logo = within(dialog.getByRole('region', { name: 'Logo' }));

    await userEvent.click(logo.getByRole('button', { name: 'Remove logo' }));

    await waitFor(() => expect(sent(calls, 'DELETE')).toHaveLength(1));
    expect(sent(calls, 'DELETE')[0]?.path).toBe('/rooms/3/logo');
    expect(await logo.findByRole('button', { name: 'Upload logo' })).toBeInTheDocument();
    expect(logo.queryByRole('button', { name: 'Remove logo' })).not.toBeInTheDocument();
  });

  it('says so when the file is not an image, or the backend rejects it', async () => {
    stubSettings({
      'PUT /rooms/1/logo': () =>
        problem(415, 'LOGO_UNSUPPORTED_TYPE', 'The logo must be a PNG, JPEG or WebP image.'),
    });
    renderApp('/settings');
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Winamax' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Edit room' }));
    const input = document.querySelector<HTMLInputElement>('input[type="file"]');

    vi.mocked(resizeImage).mockRejectedValueOnce(new Error('not an image'));
    await userEvent.upload(input!, new File(['text'], 'notes.png', { type: 'image/png' }));
    expect(await dialog.findByText('The file could not be read as an image.')).toBeInTheDocument();

    await userEvent.upload(input!, new File(['image'], 'logo.png', { type: 'image/png' }));
    expect(
      await dialog.findByText('The logo must be a PNG, JPEG or WebP image.'),
    ).toBeInTheDocument();
  });
});

describe('Settings: variants', () => {
  const OWN: Variant = {
    id: 21,
    gameType: 'SIT_AND_GO',
    code: null,
    name: 'Hyper Turbo',
    builtIn: false,
    active: true,
    inUse: false,
  };

  it('lists the variants per game type; built-in ones can only be switched', async () => {
    stubSettings();
    renderApp('/settings?tab=variants');

    const tournaments = within(await screen.findByRole('region', { name: 'Tournament' }));
    expect(tournaments.getByText('KO')).toBeInTheDocument();
    expect(tournaments.getByRole('switch', { name: 'KO is active' })).toBeChecked();
    expect(tournaments.getByRole('switch', { name: 'Space KO is active' })).not.toBeChecked();
    expect(tournaments.queryByRole('button', { name: 'Edit KO' })).not.toBeInTheDocument();

    const sitAndGo = within(screen.getByRole('region', { name: 'Sit & Go / Spin' }));
    expect(sitAndGo.getByText('Hyper Turbo')).toBeInTheDocument();
    expect(sitAndGo.getByText('Yours')).toBeInTheDocument();
    expect(sitAndGo.getByRole('button', { name: 'Edit Hyper Turbo' })).toBeInTheDocument();
    expect(
      within(screen.getByRole('region', { name: 'Cash' })).getByText('No variants.'),
    ).toBeInTheDocument();
  });

  it('activates and deactivates a variant; a built-in one sends no name', async () => {
    const calls = stubSettings({
      'PUT /variants/10': { ...VARIANTS[0], active: false },
      'PUT /variants/21': { ...OWN, active: false },
    });
    renderApp('/settings?tab=variants');

    await userEvent.click(await screen.findByRole('switch', { name: 'KO is active' }));
    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    expect(sent(calls, 'PUT')[0]).toMatchObject({
      path: '/variants/10',
      body: { name: null, active: false },
    });

    await userEvent.click(screen.getByRole('switch', { name: 'Hyper Turbo is active' }));
    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(2));
    expect(sent(calls, 'PUT')[1]).toMatchObject({
      path: '/variants/21',
      body: { name: 'Hyper Turbo', active: false },
    });
  });

  it('adds a variant of a game type', async () => {
    const calls = stubSettings({
      'POST /variants': { ...OWN, id: 30, gameType: 'CASH', name: 'Zoom' },
    });
    renderApp('/settings?tab=variants');

    await userEvent.click(await screen.findByRole('button', { name: 'Add variant' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Add variant' }));
    await userEvent.click(dialog.getByRole('combobox', { name: 'Game type' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Cash', hidden: true }));
    await userEvent.type(dialog.getByRole('textbox', { name: 'Name' }), 'Zoom');
    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'POST')).toHaveLength(1));
    expect(sent(calls, 'POST')[0]?.body).toEqual({ gameType: 'CASH', name: 'Zoom' });
    expect(await screen.findByText('Variant created')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('renames a variant of the user; its game type cannot change', async () => {
    const calls = stubSettings({ 'PUT /variants/21': { ...OWN, name: 'Hyper' } });
    renderApp('/settings?tab=variants');

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Hyper Turbo' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Edit variant' }));
    expect(dialog.getByRole('combobox', { name: 'Game type' })).toBeDisabled();
    await userEvent.clear(dialog.getByRole('textbox', { name: 'Name' }));
    await userEvent.type(dialog.getByRole('textbox', { name: 'Name' }), 'Hyper');
    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent(calls, 'PUT')).toHaveLength(1));
    expect(sent(calls, 'PUT')[0]?.body).toEqual({ name: 'Hyper', active: true });
  });

  it('deletes an unused variant after asking, and not one in use', async () => {
    const calls = stubSettings({
      'GET /variants': [OWN, { ...OWN, id: 22, name: 'Used', inUse: true }],
      'DELETE /variants/21': () => new Response(null, { status: 204 }),
    });
    renderApp('/settings?tab=variants');

    const blocked = await screen.findByRole('button', { name: 'Delete Used' });
    expect(blocked).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(blocked);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Delete Hyper Turbo' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Delete variant' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(sent(calls, 'DELETE')).toHaveLength(1));
    expect(sent(calls, 'DELETE')[0]?.path).toBe('/variants/21');
  });
});

describe('Settings page', () => {
  it('remembers the tab in the URL and says so when the data cannot be loaded', async () => {
    stubSettings();
    const first = renderApp('/settings');
    await userEvent.click(await screen.findByRole('tab', { name: 'Variants' }));
    expect(await screen.findByRole('button', { name: 'Add variant' })).toBeInTheDocument();
    expect(rowOf('KO')).toBeDefined();
    first.unmount();

    stubSettings({ 'GET /rooms': () => problem(500, 'INTERNAL_ERROR', 'Boom') });
    renderApp('/settings');
    expect(
      await screen.findByText(
        'The data could not be loaded. Check that the API is available and reload the page.',
      ),
    ).toBeInTheDocument();
  });
});
