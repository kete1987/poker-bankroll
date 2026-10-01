import { currentLanguage } from '../i18n';

/** Base path of the backend. In development Vite proxies it; in production nginx does. */
export const API_BASE = '/api';

/** One validation error, as returned by the backend (`field` is null for object-level rules). */
export interface FieldViolation {
  field: string | null;
  code: string;
  message: string;
}

/** Error body of the backend: RFC 9457 problem details plus `code` and `errors`. */
interface ProblemBody {
  status?: number;
  detail?: string;
  code?: string;
  errors?: FieldViolation[];
}

/** A failed API call. Show `message` (already localized by the backend) or map `code` to i18n keys. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly errors: FieldViolation[];

  constructor(status: number, code: string, message: string, errors: FieldViolation[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}

/**
 * Calls the backend and returns the parsed JSON body.
 * Sends the UI language so error messages come back translated; throws {@link ApiError} on failure.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await request(path, init, 'application/json, application/problem+json');
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/**
 * Calls the backend for something that is not JSON (an image) and returns it as it comes.
 * Errors are the same as with {@link apiFetch}.
 */
export async function apiFetchBlob(path: string, init: RequestInit = {}): Promise<Blob> {
  const response = await request(path, init, 'image/*, application/problem+json');
  return response.blob();
}

async function request(path: string, init: RequestInit, accept: string): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set('Accept', accept);
  headers.set('Accept-Language', currentLanguage());
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return response;
}

async function toApiError(response: Response): Promise<ApiError> {
  const fallbackCode = response.status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED';
  if (response.headers.get('Content-Type')?.includes('json')) {
    try {
      const body = (await response.json()) as ProblemBody;
      return new ApiError(
        response.status,
        body.code ?? fallbackCode,
        body.detail ?? response.statusText,
        body.errors ?? [],
      );
    } catch {
      // Not the JSON it claimed to be: fall through to a generic error.
    }
  }
  return new ApiError(response.status, fallbackCode, response.statusText);
}
