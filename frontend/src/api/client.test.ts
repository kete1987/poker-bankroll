import { describe, expect, it, vi } from 'vitest';

import i18n from '../i18n';
import { stubFetchJson } from '../test/renderApp';
import { ApiError, apiFetch } from './client';

describe('apiFetch', () => {
  it('prefixes /api and returns the JSON body', async () => {
    const fetchMock = stubFetchJson({ id: 1 });

    await expect(apiFetch('/games/1')).resolves.toEqual({ id: 1 });
    expect(fetchMock).toHaveBeenCalledWith('/api/games/1', expect.anything());
  });

  it('sends the UI language so errors come back translated', async () => {
    const fetchMock = stubFetchJson({});
    await i18n.changeLanguage('es');

    await apiFetch('/games');

    const init = fetchMock.mock.calls[0]?.[1];
    expect(new Headers(init?.headers).get('Accept-Language')).toBe('es');
  });

  it('sets a JSON content type when sending a body', async () => {
    const fetchMock = stubFetchJson({});

    await apiFetch('/games', { method: 'POST', body: JSON.stringify({ name: 'KO' }) });

    const init = fetchMock.mock.calls[0]?.[1];
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json');
  });

  it('turns problem details into an ApiError with code and field errors', async () => {
    stubFetchJson(
      {
        status: 400,
        detail: 'The request contains invalid data.',
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'buyIn', code: 'Positive', message: 'must be greater than 0' }],
      },
      400,
      'application/problem+json',
    );

    const error = await apiFetch('/games').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'The request contains invalid data.',
      errors: [{ field: 'buyIn', code: 'Positive', message: 'must be greater than 0' }],
    });
  });

  it('falls back to a generic code when the error body is not JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('Bad gateway', { status: 502, statusText: 'Bad Gateway' })),
    );

    await expect(apiFetch('/games')).rejects.toMatchObject({
      status: 502,
      code: 'INTERNAL_ERROR',
      message: 'Bad Gateway',
    });
  });
});
