import { describe, expect, it } from 'vitest';

import type { BankrollEvolution, CurrencyEvolution, EvolutionPeriod } from '../api/types';
import {
  chartData,
  granularityForActivity,
  granularityForDays,
  granularityForRange,
} from './evolution';

function period(overrides: Partial<EvolutionPeriod> & { period: string }): EvolutionPeriod {
  return {
    startsOn: overrides.period,
    endsOn: overrides.period,
    deposited: 0,
    withdrawn: 0,
    bonuses: 0,
    adjustments: 0,
    gamesNet: 0,
    bankroll: 0,
    ...overrides,
  };
}

const EUR: CurrencyEvolution = {
  currencyCode: 'EUR',
  total: {
    startingBankroll: 50,
    periods: [
      period({ period: '2026-01-05', deposited: 100, gamesNet: -10, bankroll: 140 }),
      period({ period: '2026-01-12', gamesNet: 20, bankroll: 160 }),
      period({ period: '2026-01-20', withdrawn: 30, bankroll: 130 }),
    ],
  },
  rooms: [
    {
      room: { id: 3, name: 'Unibet' },
      active: true,
      series: {
        startingBankroll: 0,
        periods: [period({ period: '2026-01-12', gamesNet: 20, bankroll: 20 })],
      },
    },
    {
      room: { id: 1, name: 'Winamax' },
      active: true,
      series: {
        startingBankroll: 50,
        periods: [
          period({ period: '2026-01-05', deposited: 100, gamesNet: -10, bankroll: 140 }),
          period({ period: '2026-01-20', withdrawn: 30, bankroll: 110 }),
        ],
      },
    },
  ],
};

describe('granularity', () => {
  it('is days up to three months, weeks up to two years and months beyond', () => {
    expect(granularityForDays(92)).toBe('DAY');
    expect(granularityForDays(93)).toBe('WEEK');
    expect(granularityForDays(731)).toBe('WEEK');
    expect(granularityForDays(732)).toBe('MONTH');
  });

  it('comes from the length of a period with both ends, and from the activity otherwise', () => {
    expect(granularityForRange({ from: '2026-01-01', to: '2026-01-31' })).toBe('DAY');
    expect(granularityForRange({ from: '2026-01-01', to: '2026-12-31' })).toBe('WEEK');
    expect(granularityForRange({ from: '2026-01-01' })).toBeUndefined();
    expect(granularityForRange({})).toBeUndefined();

    const byMonth: BankrollEvolution = {
      groupBy: 'MONTH',
      currencies: [
        {
          currencyCode: 'EUR',
          total: {
            startingBankroll: 0,
            periods: [
              period({ period: '2025-11', startsOn: '2025-11-01', endsOn: '2025-11-30' }),
              period({ period: '2026-01', startsOn: '2026-01-01', endsOn: '2026-01-31' }),
            ],
          },
          rooms: [],
        },
      ],
    };
    expect(granularityForActivity(byMonth, 'EUR', {})).toBe('DAY');
    // An open end only: the other one is the period's.
    expect(granularityForActivity(byMonth, 'EUR', { from: '2025-01-01' })).toBe('WEEK');
    expect(granularityForActivity(byMonth, 'EUR', { to: '2028-12-31' })).toBe('MONTH');
    // Nothing in the currency.
    expect(granularityForActivity(byMonth, 'USD', {})).toBe('MONTH');
  });
});

describe('chart data', () => {
  it('starts at the range, has a point per period of the total and goes on to its end', () => {
    const data = chartData(EUR, { from: '2026-01-01', to: '2026-01-31' }, '2026-10-04')!;

    expect(data.points.map((point) => [point.date, point.kind])).toEqual([
      ['2026-01-01', 'start'],
      ['2026-01-05', 'period'],
      ['2026-01-12', 'period'],
      ['2026-01-20', 'period'],
      ['2026-01-31', 'end'],
    ]);
    expect(data.total.values).toEqual([50, 140, 160, 130, 130]);
    // A room keeps its bankroll through the periods it has nothing in.
    expect(data.rooms).toEqual([
      { id: 3, name: 'Unibet', values: [0, 0, 20, 20, 20] },
      { id: 1, name: 'Winamax', values: [50, 140, 140, 110, 110] },
    ]);
  });

  it('draws a period at its end, but never after the range or today', () => {
    const months: CurrencyEvolution = {
      currencyCode: 'EUR',
      total: {
        startingBankroll: 0,
        periods: [
          period({ period: '2026-09', startsOn: '2026-09-01', endsOn: '2026-09-30', bankroll: 5 }),
          period({ period: '2026-10', startsOn: '2026-10-01', endsOn: '2026-10-31', bankroll: 8 }),
        ],
      },
      rooms: [],
    };

    // Without a start, the lines start where the first period does.
    expect(chartData(months, {}, '2026-10-04')!.points.map((point) => point.date)).toEqual([
      '2026-09-01',
      '2026-09-30',
      '2026-10-04',
    ]);
    expect(
      chartData(months, { to: '2026-10-02' }, '2026-10-04')!.points.map((point) => point.date),
    ).toEqual(['2026-09-01', '2026-09-30', '2026-10-02']);
  });

  it('draws a period dated after today at its first day, so the line ends with the bankroll now', () => {
    const ahead: CurrencyEvolution = {
      currencyCode: 'EUR',
      total: {
        startingBankroll: 0,
        periods: [
          period({ period: '2026-09', startsOn: '2026-09-01', endsOn: '2026-09-30', bankroll: 5 }),
          period({ period: '2026-11', startsOn: '2026-11-01', endsOn: '2026-11-30', bankroll: 9 }),
        ],
      },
      rooms: [],
    };

    const data = chartData(ahead, {}, '2026-10-04')!;
    expect(data.points.map((point) => point.date)).toEqual([
      '2026-09-01',
      '2026-09-30',
      '2026-11-01',
    ]);
    expect(data.total.values).toEqual([0, 5, 9]);
  });

  it('is nothing without a point to start from', () => {
    const empty: CurrencyEvolution = {
      currencyCode: 'EUR',
      total: { startingBankroll: 0, periods: [] },
      rooms: [],
    };
    expect(chartData(empty, {}, '2026-10-04')).toBeUndefined();
    // With a start, the bankroll there was, flat.
    const flat = chartData(
      { ...empty, total: { startingBankroll: 40, periods: [] } },
      { from: '2026-01-01', to: '2026-01-31' },
      '2026-10-04',
    )!;
    expect(flat.points.map((point) => point.date)).toEqual(['2026-01-01', '2026-01-31']);
    expect(flat.total.values).toEqual([40, 40]);
  });
});
