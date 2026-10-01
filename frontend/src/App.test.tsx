import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { LANGUAGE_STORAGE_KEY } from './i18n';
import { problem, renderApp, stubApi } from './test/renderApp';

describe('App', () => {
  it('opens on the dashboard, in English by default', async () => {
    stubApi({});
    renderApp();

    expect(await screen.findByRole('heading', { name: 'Dashboard', level: 2 })).toBeInTheDocument();
    expect(document.title).toBe('Dashboard · poker-bankroll');
  });

  it('offers every section in the menu and marks the current one', async () => {
    stubApi({});
    renderApp('/games');

    const menu = screen.getByRole('navigation', { name: 'Sections' });
    expect(
      within(menu)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Dashboard', 'Games', 'Bankroll', 'Statistics', 'Import', 'Settings']);
    expect(within(menu).getByRole('link', { name: 'Games' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(menu).getByRole('link', { name: 'Dashboard' })).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('navigates between sections', async () => {
    stubApi({});
    renderApp();
    const menu = screen.getByRole('navigation', { name: 'Sections' });

    await userEvent.click(within(menu).getByRole('link', { name: 'Bankroll' }));

    expect(await screen.findByRole('heading', { name: 'Bankroll', level: 2 })).toBeInTheDocument();
    expect(within(menu).getByRole('link', { name: 'Bankroll' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(document.title).toBe('Bankroll · poker-bankroll');
  });

  it.each(['/games', '/bankroll', '/stats', '/import', '/settings'])(
    'has a page at %s',
    async (path) => {
      stubApi({});
      renderApp(path);

      expect(await screen.findByRole('heading', { level: 2 })).not.toHaveTextContent(
        'Page not found',
      );
    },
  );

  it('has a button to open the menu on narrow screens', async () => {
    stubApi({});
    renderApp();

    const burger = screen.getByRole('button', { name: 'Open or close the menu' });
    expect(burger).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(burger);
    expect(burger).toHaveAttribute('aria-expanded', 'true');

    // Choosing a section closes it again.
    await userEvent.click(screen.getByRole('link', { name: 'Games' }));
    expect(burger).toHaveAttribute('aria-expanded', 'false');
  });

  it('shows the API as online when the backend is healthy', async () => {
    const calls = stubApi({});
    renderApp();

    expect(
      await within(await screen.findByTestId('api-status')).findByText('Online'),
    ).toBeVisible();
    expect(calls.some((call) => call.path === '/actuator/health')).toBe(true);
  });

  it('shows the API as unavailable when the backend fails', async () => {
    stubApi({ 'GET /actuator/health': () => problem(503, 'INTERNAL_ERROR', 'Down') });
    renderApp();

    expect(await within(screen.getByTestId('api-status')).findByText('Unavailable')).toBeVisible();
  });

  it('shows the API as unavailable when a refresh fails after it was online', async () => {
    let healthy = true;
    stubApi({
      'GET /actuator/health': () =>
        healthy ? { status: 'UP' } : problem(503, 'INTERNAL_ERROR', 'Down'),
    });
    const { queryClient } = renderApp();
    const badge = await screen.findByTestId('api-status');
    expect(await within(badge).findByText('Online')).toBeVisible();

    healthy = false;
    await queryClient.refetchQueries({ queryKey: ['health'] });

    expect(await within(badge).findByText('Unavailable')).toBeVisible();
  });

  it('switches to Spanish and remembers the choice', async () => {
    stubApi({});
    renderApp();

    await userEvent.click(await screen.findByText('ES'));

    expect(await screen.findByRole('heading', { name: 'Panel', level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Partidas' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Estadísticas' })).toBeInTheDocument();
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('es');
    expect(document.documentElement.lang).toBe('es');
    expect(document.title).toBe('Panel · poker-bankroll');
  });

  it('toggles the color scheme', async () => {
    stubApi({});
    renderApp();

    const toggle = await screen.findByRole('button', {
      name: 'Switch between light and dark mode',
    });
    const before = document.documentElement.getAttribute('data-mantine-color-scheme');
    await userEvent.click(toggle);

    const after = document.documentElement.getAttribute('data-mantine-color-scheme');
    expect(after).not.toBe(before);
    expect(localStorage.getItem('poker-bankroll.color-scheme')).toBe(after);
  });

  it('shows a not found page for unknown routes', async () => {
    stubApi({});
    renderApp('/does-not-exist');

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the dashboard' })).toHaveAttribute(
      'href',
      '/',
    );
  });
});
