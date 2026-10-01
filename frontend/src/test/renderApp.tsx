import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';

import { AppProviders } from '../AppProviders';
import { routes } from '../routes';

/**
 * Renders the whole app at the given URL, with fresh providers and no query retries.
 * Returns the query client too, e.g. to force a refetch.
 */
export function renderApp(url = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(routes, { initialEntries: [url] });
  const result = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...result, queryClient };
}

/** Stubs `fetch` with a JSON response. */
export function stubFetchJson(body: unknown, status = 200, contentType = 'application/json') {
  const fetchMock = vi.fn(
    async (_input: RequestInfo | URL, _init?: RequestInit) =>
      new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } }),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export interface ApiCall {
  method: string;
  /** Path below `/api`, without the query string. */
  path: string;
  query: URLSearchParams;
  /** The JSON sent, parsed. */
  body: unknown;
  /** What was sent when it is not JSON text (a file). */
  rawBody?: BodyInit | null;
  headers: Headers;
}

/** A fixed JSON body, or a function of the call that returns one (or a whole `Response`). */
type ApiHandler = unknown | ((call: ApiCall) => unknown);

/** A JSON error as the backend sends it. */
export function problem(status: number, code: string, detail: string, errors: unknown[] = []) {
  return new Response(JSON.stringify({ status, code, detail, errors }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

/**
 * Stubs `fetch` with one handler per `"METHOD /path"`. The health check answers UP and the data
 * every screen loads is empty unless overridden; anything else is a 404. Returns the calls made, to assert on what was sent.
 */
export function stubApi(handlers: Record<string, ApiHandler>) {
  const calls: ApiCall[] = [];
  const all: Record<string, ApiHandler> = {
    'GET /actuator/health': { status: 'UP' },
    // What every screen asks for, empty unless the test says otherwise.
    'GET /rooms': [],
    'GET /variants': [],
    'GET /stats/summary': { currencies: [] },
    'GET /stats/groups': (call: ApiCall) => ({
      groupBy: call.query.get('groupBy'),
      currencies: [],
    }),
    'GET /games/names': [],
    'GET /bankroll/summary': { currencies: [] },
    'GET /bankroll/movements': { items: [], page: 0, size: 25, totalItems: 0, totalPages: 0 },
    'GET /catalog': {
      currencies: [
        { code: 'EUR', symbol: '€', decimals: 2 },
        { code: 'USD', symbol: '$', decimals: 2 },
      ],
      gameTypes: ['TOURNAMENT', 'SIT_AND_GO', 'CASH'],
      modalities: ['NLHE', 'PLO'],
    },
    ...handlers,
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), 'http://localhost');
      const call: ApiCall = {
        method: (init?.method ?? 'GET').toUpperCase(),
        path: url.pathname.replace(/^\/api/, ''),
        query: url.searchParams,
        body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
        rawBody: typeof init?.body === 'string' ? undefined : init?.body,
        headers: new Headers(init?.headers),
      };
      calls.push(call);
      const key = `${call.method} ${call.path}`;
      if (!(key in all)) {
        return problem(404, 'NOT_FOUND', `No stub for ${key}`);
      }
      const handler = all[key];
      // A handler may answer later (a promise), to test what happens meanwhile.
      const result = await (typeof handler === 'function'
        ? (handler as (c: ApiCall) => unknown)(call)
        : handler);
      return result instanceof Response
        ? result
        : new Response(JSON.stringify(result), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
    }),
  );
  return calls;
}
