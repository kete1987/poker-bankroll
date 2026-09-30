import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { LANGUAGE_STORAGE_KEY } from './i18n';
import { renderApp, stubFetchJson } from './test/renderApp';

describe('App', () => {
  it('renders the home page in English by default', async () => {
    stubFetchJson({ status: 'UP' });
    renderApp();

    expect(
      await screen.findByRole('heading', { name: 'Welcome to poker-bankroll' }),
    ).toBeInTheDocument();
  });

  it('shows the API as online when the backend is healthy', async () => {
    const fetchMock = stubFetchJson({ status: 'UP' });
    renderApp();

    expect(
      await within(await screen.findByTestId('api-status')).findByText('Online'),
    ).toBeVisible();
    expect(fetchMock).toHaveBeenCalledWith('/api/actuator/health', expect.anything());
  });

  it('shows the API as unavailable when the backend fails', async () => {
    stubFetchJson({ status: 'DOWN' }, 503);
    renderApp();

    expect(await within(screen.getByTestId('api-status')).findByText('Unavailable')).toBeVisible();
  });

  it('switches to Spanish and remembers the choice', async () => {
    stubFetchJson({ status: 'UP' });
    renderApp();

    await userEvent.click(await screen.findByText('ES'));

    expect(
      await screen.findByRole('heading', { name: 'Bienvenido a poker-bankroll' }),
    ).toBeInTheDocument();
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('es');
    expect(document.documentElement.lang).toBe('es');
  });

  it('toggles the color scheme', async () => {
    stubFetchJson({ status: 'UP' });
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
    stubFetchJson({ status: 'UP' });
    renderApp('/does-not-exist');

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });
});
