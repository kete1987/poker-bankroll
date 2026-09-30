import { afterEach, describe, expect, it, vi } from 'vitest';

import { LANGUAGE_STORAGE_KEY } from '.';

describe('i18n initialisation', () => {
  afterEach(() => {
    vi.resetModules();
  });

  it('sets <html lang> to the language detected on the first load', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'es');
    document.documentElement.lang = 'en';
    vi.resetModules();

    const { default: freshI18n } = await import('.');

    expect(freshI18n.resolvedLanguage).toBe('es');
    expect(document.documentElement.lang).toBe('es');
  });
});
