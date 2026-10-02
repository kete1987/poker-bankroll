import { afterEach, beforeEach } from 'vitest';

/**
 * Makes the tests of a file run as on a phone: every `max-width` media query matches, which is
 * what `useNarrowScreen` asks. jsdom has no layout, so this is the only thing a width changes.
 * Call it at the top of a `describe`.
 */
export function onANarrowScreen() {
  const original = window.matchMedia;
  beforeEach(() => {
    window.matchMedia = (query: string) => ({
      ...original(query),
      matches: query.includes('max-width'),
    });
  });
  afterEach(() => {
    window.matchMedia = original;
  });
}
