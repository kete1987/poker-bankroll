import { describe, expect, it } from 'vitest';

import { APP_VERSION, formatVersion } from './version';

describe('formatVersion', () => {
  it.each([
    ['0.1.0', 'v0.1.0'],
    ['1.2.3-rc.1', 'v1.2.3-rc.1'],
    ['edge-abc1234', 'edge-abc1234'],
    ['dev', 'dev'],
  ])('%s is shown as %s', (version, shown) => {
    expect(formatVersion(version)).toBe(shown);
  });

  it('is "dev" for local builds', () => {
    expect(APP_VERSION).toBe('dev');
  });
});
