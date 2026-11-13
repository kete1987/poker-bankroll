import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { Movement } from '../api/types';
import { game, page, ROOMS, template, VARIANTS } from '../test/fixtures';
import { onANarrowScreen } from '../test/narrowScreen';
import { renderApp, stubApi, type ApiCall } from '../test/renderApp';

vi.mock('../components/Chart', () => ({ Chart: () => <div /> }));

async function chooseSize(size: number) {
  await userEvent.click(screen.getByRole('combobox', { name: 'Rows per page' }));
  await userEvent.click(await screen.findByRole('option', { name: String(size), hidden: true }));
}

function lastQuery(calls: ApiCall[], path: string) {
  return calls.filter((call) => call.path === path && call.query.get('status') !== 'IN_PLAY').at(-1)
    ?.query;
}

describe('Page sizes and reference-list pagination', () => {
  it('reads a game size from the URL, resets the page and keeps filters, sorting and the size when clearing filters', async () => {
    const calls = stubApi({
      'GET /rooms': ROOMS,
      'GET /variants': VARIANTS,
      'GET /games': (call: ApiCall) =>
        call.query.get('status') === 'IN_PLAY'
          ? page([])
          : page([game({ name: 'Demo' })], { totalItems: 500, totalPages: 5 }),
    });
    renderApp('/games?size=50&page=3&room=1&q=Demo&sort=net,asc');
    expect(await screen.findByRole('combobox', { name: 'Rows per page' })).toHaveValue('50');
    expect(lastQuery(calls, '/games')?.get('page')).toBe('2');
    await chooseSize(100);
    await waitFor(() => {
      expect(Object.fromEntries(lastQuery(calls, '/games')!)).toMatchObject({
        page: '0',
        size: '100',
        roomId: '1',
        q: 'Demo',
        sort: 'net,asc',
      });
    });
    await userEvent.click(screen.getByRole('button', { name: 'Page 2' }));
    await waitFor(() => expect(lastQuery(calls, '/games')?.get('page')).toBe('1'));
    expect(lastQuery(calls, '/games')?.get('size')).toBe('100');
    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() => {
      const query = lastQuery(calls, '/games')!;
      expect(query.get('size')).toBe('100');
      expect(query.get('page')).toBe('0');
      expect(query.has('q')).toBe(false);
      expect(query.has('roomId')).toBe(false);
    });
  });

  it.each(['-1', '201', '31', 'abc'])(
    'falls back to 25 for unsupported game size %s, even with only one page',
    async (size) => {
      const calls = stubApi({
        'GET /games': (call: ApiCall) =>
          page(call.query.get('status') === 'IN_PLAY' ? [] : [game({})]),
      });
      renderApp(`/games?size=${size}`);
      expect(await screen.findByRole('combobox', { name: 'Rows per page' })).toHaveValue('25');
      expect(lastQuery(calls, '/games')?.get('size')).toBe('25');
      expect(screen.queryByRole('button', { name: 'Page 1' })).not.toBeInTheDocument();
      await chooseSize(10);
      await waitFor(() => expect(lastQuery(calls, '/games')?.get('size')).toBe('10'));
    },
  );

  it('changes movement page size without losing its date, room or type filters', async () => {
    const movement: Movement = {
      id: 1,
      occurredOn: '2026-01-19',
      room: { id: 1, name: 'Winamax' },
      currencyCode: 'EUR',
      type: 'DEPOSIT',
      amount: 50,
      signedAmount: 50,
      notes: null,
      createdAt: '2026-01-19T00:00:00Z',
      updatedAt: '2026-01-19T00:00:00Z',
    };
    const calls = stubApi({
      'GET /rooms': ROOMS,
      'GET /bankroll/movements': {
        items: [movement],
        page: 2,
        size: 25,
        totalItems: 90,
        totalPages: 4,
      },
    });
    renderApp('/bankroll?page=3&from=2026-01-01&room=1&type=DEPOSIT');
    expect(await screen.findByRole('combobox', { name: 'Rows per page' })).toHaveValue('25');
    await chooseSize(50);
    await waitFor(() =>
      expect(Object.fromEntries(lastQuery(calls, '/bankroll/movements')!)).toMatchObject({
        size: '50',
        page: '0',
        from: '2026-01-01',
        roomId: '1',
        type: 'DEPOSIT',
      }),
    );
  });

  it('paginates tags while still detecting a merge into a tag on another page, and clamps after deletion', async () => {
    let tags = Array.from({ length: 26 }, (_, i) => ({
      id: i + 1,
      name: `Tag ${i + 1}`,
      games: 0,
    }));
    stubApi({
      'GET /tags': () => tags,
      'DELETE /tags/26': () => {
        tags = tags.filter((tag) => tag.id !== 26);
        return new Response(null, { status: 204 });
      },
    });
    renderApp('/settings?tab=tags&page=2');
    await screen.findByRole('cell', { name: 'Tag 26' });
    expect(screen.queryByRole('cell', { name: 'Tag 1' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Rename Tag 26' }));
    let dialog = within(await screen.findByRole('dialog', { name: 'Rename tag' }));
    await userEvent.clear(dialog.getByRole('textbox', { name: 'Name' }));
    await userEvent.type(dialog.getByRole('textbox', { name: 'Name' }), 'Tag 1');
    expect(dialog.getByText(/There is already a tag called Tag 1:/)).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Cancel' }));
    await userEvent.click(screen.getByRole('button', { name: 'Delete Tag 26' }));
    dialog = within(await screen.findByRole('dialog', { name: 'Delete tag' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Delete' }));
    await screen.findByRole('cell', { name: 'Tag 1' });
    expect(screen.queryByRole('button', { name: 'Page 2' })).not.toBeInTheDocument();
    await chooseSize(10);
    expect(screen.getByRole('button', { name: 'Page 1' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByRole('cell', { name: 'Tag 11' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Page 2' }));
    expect(await screen.findByRole('cell', { name: 'Tag 11' })).toBeInTheDocument();
  });

  it.each([false, true])(
    'paginates templates in the desktop/mobile layout (mobile=%s)',
    async (mobile) => {
      if (mobile) onANarrowScreen();
      const templates = Array.from({ length: 26 }, (_, i) =>
        template({ id: i + 1, label: `Template ${i + 1}` }),
      );
      const calls = stubApi({
        'GET /rooms': ROOMS,
        'GET /variants': VARIANTS,
        'GET /game-templates': templates,
      });
      renderApp('/settings?tab=templates&page=2');
      await screen.findByText('Template 26');
      expect(screen.queryByText('Template 1')).not.toBeInTheDocument();
      await chooseSize(50);
      expect(await screen.findByText('Template 1')).toBeInTheDocument();
      expect(screen.getByText('Template 26')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Page 2' })).not.toBeInTheDocument();
      expect(calls.filter((call) => call.path === '/game-templates')).toHaveLength(1);
    },
  );
});
