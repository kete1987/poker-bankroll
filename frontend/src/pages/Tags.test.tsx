import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { Game, StatsGroup, StatsGroups } from '../api/types';
import { game, page, ROOMS, TAGS, VARIANTS } from '../test/fixtures';
import { onANarrowScreen } from '../test/narrowScreen';
import { problem, renderApp, stubApi, type ApiCall } from '../test/renderApp';

// Canvas rendering is not available in jsdom: the chart is replaced by what it was asked to draw.
vi.mock('../components/Chart', () => ({
  Chart: ({ option }: { option: unknown }) => (
    <div data-testid="chart" data-option={JSON.stringify(option)} />
  ),
}));

const TAGGED = game({
  id: 7,
  name: 'Kill The Fish',
  tags: [
    { id: 30, name: 'Challenge' },
    { id: 31, name: 'Friends' },
  ],
});

function stubGames(finished: Game[] = [TAGGED], handlers: Record<string, unknown> = {}) {
  return stubApi({
    'GET /rooms': ROOMS,
    'GET /variants': VARIANTS,
    'GET /tags': TAGS,
    'GET /games': (call: ApiCall) =>
      call.query.get('status') === 'IN_PLAY' ? page([]) : page(finished),
    ...handlers,
  });
}

/** The parameters of the last request of the finished games, lists joined by commas. */
function lastListQuery(calls: ApiCall[]) {
  const call = calls
    .filter((one) => one.path === '/games' && one.query.get('status') === 'FINISHED')
    .at(-1);
  return (
    call &&
    Object.fromEntries(
      [...new Set(call.query.keys())].map((key) => [key, call.query.getAll(key).join(',')]),
    )
  );
}

function tagsOf(container: HTMLElement) {
  return within(within(container).getByRole('list', { name: 'Tags' }))
    .getAllByRole('listitem')
    .map((item) => item.textContent);
}

describe('Tags of the games', () => {
  it('are shown under the name of each game', async () => {
    stubGames([TAGGED, game({ id: 8, name: 'Untagged' })]);
    renderApp('/games');

    const row = (await screen.findByText('Kill The Fish')).closest('tr')!;
    expect(tagsOf(row)).toEqual(['Challenge', 'Friends']);
    const untagged = screen.getByText('Untagged').closest('tr')!;
    expect(within(untagged).queryByRole('list', { name: 'Tags' })).not.toBeInTheDocument();
  });

  it('filter the games, from the URL too, with the other filters', async () => {
    const calls = stubGames();
    renderApp('/games?tag=31,x,31');
    await screen.findByText('Kill The Fish');

    // Invalid values are ignored.
    await waitFor(() => expect(lastListQuery(calls)).toMatchObject({ tagId: '31' }));
    expect(document.querySelector('input[type="hidden"][value="31"]')).not.toBeNull();

    await userEvent.click(screen.getByRole('combobox', { name: 'Tag' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Challenge', hidden: true }));
    await waitFor(() => expect(lastListQuery(calls)).toMatchObject({ tagId: '31,30', page: '0' }));

    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(lastListQuery(calls)).not.toHaveProperty('tagId'));
  });

  it('have no filter while there are none', async () => {
    stubGames([game({ name: 'Kill The Fish' })], { 'GET /tags': [] });
    renderApp('/games');
    await screen.findByText('Kill The Fish');

    expect(screen.queryByRole('combobox', { name: 'Tag' })).not.toBeInTheDocument();
  });

  it('are kept when a game is edited, and can be changed', async () => {
    const calls = stubGames([TAGGED], { 'PUT /games/7': TAGGED });
    renderApp('/games');
    await screen.findByText('Kill The Fish');

    await userEvent.click(screen.getByRole('button', { name: 'Edit Kill The Fish' }));
    const form = within(await screen.findByRole('dialog', { name: 'Edit game' }));
    await form.findByRole('textbox', { name: 'Buy-in' });
    await userEvent.type(form.getByLabelText('Tags'), 'Series{Enter}');
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(calls.find((call) => call.method === 'PUT')?.body).toMatchObject({
        tags: ['Challenge', 'Friends', 'Series'],
      }),
    );
  });

  it('show the error of the backend on their field', async () => {
    stubGames([TAGGED], {
      'PUT /games/7': () =>
        problem(400, 'VALIDATION_FAILED', 'The request contains invalid data.', [
          { field: 'tags[2]', code: 'TagName', message: 'A tag has from 1 to 40 characters.' },
        ]),
    });
    renderApp('/games');
    await screen.findByText('Kill The Fish');

    await userEvent.click(screen.getByRole('button', { name: 'Edit Kill The Fish' }));
    const form = within(await screen.findByRole('dialog', { name: 'Edit game' }));
    await form.findByRole('textbox', { name: 'Buy-in' });
    await userEvent.click(form.getByRole('button', { name: 'Save' }));

    expect(await form.findByText('A tag has from 1 to 40 characters.')).toBeInTheDocument();
  });

  describe('on a phone', () => {
    onANarrowScreen();

    it('are shown on the cards, and their filter counts as one', async () => {
      stubGames();
      renderApp('/games?tag=30');

      const name = await screen.findByText('Kill The Fish');
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      const card = name.closest<HTMLElement>(
        '[data-game-card], .mantine-Card-root, .mantine-Paper-root',
      )!;
      expect(tagsOf(card)).toEqual(['Challenge', 'Friends']);
      expect(
        within(screen.getByRole('button', { name: /Filters/ })).getByText('1'),
      ).toBeInTheDocument();
    });
  });
});

describe('Statistics by tag', () => {
  function stubStats() {
    const groups: StatsGroup[] = [
      { key: { tag: { id: 30, name: 'Challenge' } }, figures: figures(8, 12) },
      { key: { tag: null }, figures: figures(20, -4) },
    ];
    return stubApi({
      'GET /tags': TAGS,
      'GET /stats/summary': {
        currencies: [
          {
            currencyCode: 'EUR',
            total: figures(25, 8),
            byGameType: [],
            inPlay: { games: 0, invested: 0 },
          },
        ],
      },
      'GET /stats/groups': (call: ApiCall): StatsGroups => ({
        groupBy: call.query.get('groupBy') as StatsGroups['groupBy'],
        currencies: [
          { currencyCode: 'EUR', groups: call.query.get('groupBy') === 'TAG' ? groups : [] },
        ],
      }),
    });
  }

  function figures(games: number, net: number) {
    return {
      games,
      entries: games,
      winningGames: 0,
      invested: games,
      won: games + net,
      bounties: 0,
      ticketsWon: 0,
      net,
    };
  }

  it('breaks the games down by tag, saying that a game counts in each of its tags', async () => {
    const calls = stubStats();
    renderApp('/stats?view=breakdown&by=tag&tag=30');

    expect(await screen.findByRole('columnheader', { name: 'Tag' })).toBeInTheDocument();
    expect(screen.getAllByRole('rowheader').map((cell) => cell.textContent)).toEqual([
      'Challenge',
      'No tag',
    ]);
    expect(screen.getByText(/A game with several tags counts in each of them/)).toBeInTheDocument();
    const groupsCall = calls.find(
      (call) => call.path === '/stats/groups' && call.query.get('groupBy') === 'TAG',
    );
    expect(groupsCall?.query.getAll('tagId').join(',')).toBe('30');
  });
});

describe('Settings: tags', () => {
  function stubTags(handlers: Record<string, unknown> = {}) {
    return stubApi({ 'GET /tags': TAGS, ...handlers });
  }

  function rowOf(name: string) {
    return within(screen.getByRole('cell', { name }).closest('tr')!);
  }

  it('lists the tags with their games', async () => {
    stubTags();
    renderApp('/settings?tab=tags');

    expect(await screen.findByRole('cell', { name: 'Challenge' })).toBeInTheDocument();
    expect(rowOf('Challenge').getByText('12')).toBeInTheDocument();
    expect(rowOf('Friends').getByText('1')).toBeInTheDocument();
  });

  it('says how to create one when there are none', async () => {
    stubTags({ 'GET /tags': [] });
    renderApp('/settings?tab=tags');

    expect(await screen.findByText(/There are no tags yet/)).toBeInTheDocument();
  });

  it('renames a tag', async () => {
    const calls = stubTags({ 'PUT /tags/31': { id: 31, name: 'With friends', games: 1 } });
    renderApp('/settings?tab=tags');
    await screen.findByRole('cell', { name: 'Friends' });

    await userEvent.click(rowOf('Friends').getByRole('button', { name: 'Rename Friends' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Rename tag' }));
    const name = dialog.getByRole('textbox', { name: 'Name' });
    await userEvent.clear(name);
    await userEvent.type(name, ' With friends ');
    await userEvent.click(dialog.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(calls.find((call) => call.method === 'PUT')?.body).toEqual({ name: 'With friends' }),
    );
    expect(await screen.findByText('Tag renamed')).toBeInTheDocument();
  });

  it('says before renaming that a tag will be merged into another with that name', async () => {
    const calls = stubTags({ 'PUT /tags/31': { id: 30, name: 'Challenge', games: 13 } });
    renderApp('/settings?tab=tags');
    await screen.findByRole('cell', { name: 'Friends' });

    await userEvent.click(rowOf('Friends').getByRole('button', { name: 'Rename Friends' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Rename tag' }));
    const name = dialog.getByRole('textbox', { name: 'Name' });
    await userEvent.clear(name);
    await userEvent.type(name, 'challenge');

    expect(
      dialog.getByText(
        'There is already a tag called Challenge: the games of Friends will get Challenge, and Friends will be deleted.',
      ),
    ).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Merge' }));

    await waitFor(() =>
      expect(calls.find((call) => call.method === 'PUT')?.body).toEqual({ name: 'challenge' }),
    );
    expect(await screen.findByText('Tags merged')).toBeInTheDocument();
  });

  it('deletes a tag after saying how many games lose it', async () => {
    const calls = stubTags({ 'DELETE /tags/30': () => new Response(null, { status: 204 }) });
    renderApp('/settings?tab=tags');
    await screen.findByRole('cell', { name: 'Challenge' });

    await userEvent.click(rowOf('Challenge').getByRole('button', { name: 'Delete Challenge' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Delete tag' }));
    expect(
      dialog.getByText('Delete the tag Challenge? 12 games lose it; the games are kept.'),
    ).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(calls.some((call) => call.method === 'DELETE')).toBe(true));
    expect(await screen.findByText('Tag deleted')).toBeInTheDocument();
  });
});
