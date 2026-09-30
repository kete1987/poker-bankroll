import { describe, expect, it } from 'vitest';

import { resources, SUPPORTED_LANGUAGES } from '.';

function keysOf(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null) {
    return [prefix];
  }
  return Object.entries(value).flatMap(([key, child]) =>
    keysOf(child, prefix ? `${prefix}.${key}` : key),
  );
}

describe('locales', () => {
  const englishKeys = keysOf(resources.en.translation).sort();

  it.each(SUPPORTED_LANGUAGES)('%s has exactly the same keys as English', (language) => {
    expect(keysOf(resources[language].translation).sort()).toEqual(englishKeys);
  });

  it.each(SUPPORTED_LANGUAGES)('%s has no empty translations', (language) => {
    const values = keysOf(resources[language].translation).map((key) =>
      key
        .split('.')
        .reduce<unknown>(
          (node, part) => (node as Record<string, unknown>)[part],
          resources[language].translation,
        ),
    );
    expect(values.every((value) => typeof value === 'string' && value.trim() !== '')).toBe(true);
  });
});
