import { describe, expect, it } from 'vitest';

import { createFormatters, localeFor, NO_VALUE } from './format';

/** Intl separates numbers and symbols with non-breaking spaces; compare with plain ones. */
function plain(text: string): string {
  return text.replace(/[  ]/g, ' ');
}

describe('localeFor', () => {
  it('uses the regional format of the browser when it is in the UI language', () => {
    expect(localeFor('en', ['en-US', 'es-ES'])).toBe('en-US');
    expect(localeFor('es', ['en-US', 'es-MX'])).toBe('es-MX');
  });

  it('falls back to the default format of the language', () => {
    expect(localeFor('en', ['es-ES'])).toBe('en-GB');
    expect(localeFor('es', ['en-US', 'fr'])).toBe('es-ES');
    expect(localeFor('es', ['es'])).toBe('es-ES');
    expect(localeFor('en', [])).toBe('en-GB');
  });
});

describe('formatters', () => {
  const es = createFormatters('es-ES');
  const en = createFormatters('en-GB');

  it('formats money in the currency of the amount', () => {
    expect(plain(es.money(1234.5, 'EUR'))).toBe('1.234,50 €');
    expect(plain(es.money(-3.2, 'USD'))).toBe('-3,20 US$');
    expect(en.money(1234.5, 'EUR')).toBe('€1,234.50');
    expect(en.money(1234.5, 'USD')).toBe('US$1,234.50');
  });

  it('uses the decimals of the currency, two at most', () => {
    expect(en.money(1234, 'JPY')).toBe('JP¥1,234');
    expect(plain(en.money(1234.5, 'KWD'))).toBe('KWD 1,234.50');
  });

  it('shows the sign of gains, but not of zero', () => {
    expect(plain(es.signedMoney(12, 'EUR'))).toBe('+12,00 €');
    expect(plain(es.signedMoney(-12, 'EUR'))).toBe('-12,00 €');
    expect(plain(es.signedMoney(0, 'EUR'))).toBe('0,00 €');
    expect(en.signedMoney(12, 'EUR')).toBe('+€12.00');
  });

  it('formats numbers and rates', () => {
    expect(plain(es.number(12345))).toBe('12.345');
    expect(plain(es.number(2105))).toBe('2.105');
    expect(en.number(12345)).toBe('12,345');
    expect(plain(es.percent(0.3496))).toBe('34,96 %');
    expect(en.percent(-0.0853)).toBe('-8.53%');
    expect(es.percent(null)).toBe(NO_VALUE);
    expect(es.percent(undefined)).toBe(NO_VALUE);
  });

  it('formats dates without shifting the day', () => {
    expect(es.date('2026-01-19')).toBe('19/01/2026');
    expect(en.date('2026-01-19')).toBe('19/01/2026');
    expect(createFormatters('en-US').date('2026-01-19')).toBe('01/19/2026');
    expect(es.longDate('2026-01-19')).toBe('19 de enero de 2026');
    expect(en.longDate('2026-12-31')).toBe('31 December 2026');
  });

  it('names the days of the week from Monday', () => {
    expect(es.weekday(1)).toBe('lunes');
    expect(es.weekday(7)).toBe('domingo');
    expect(en.weekday(3)).toBe('Wednesday');
  });

  it('formats months and times', () => {
    expect(es.month('2026-01')).toBe('enero de 2026');
    expect(en.month('2026-01')).toBe('January 2026');
    expect(es.time('21:30:00')).toBe('21:30');
    expect(es.decimalSeparator).toBe(',');
    expect(en.decimalSeparator).toBe('.');
  });

  it('formats exchange rates with four decimals at least', () => {
    expect(es.rate(1.085)).toBe('1,0850');
    expect(en.rate(1.12345678)).toBe('1.12345678');
    expect(plain(es.rate(18345.5))).toBe('18.345,5000');
  });

  it('formats an instant as a date and a time where the browser is', () => {
    const instant = '2026-10-04T15:30:00Z';
    const local = new Date(instant);
    const expected = `${String(local.getDate()).padStart(2, '0')}/${String(local.getMonth() + 1).padStart(2, '0')}/2026`;
    expect(es.moment(instant)).toContain(expected);
    expect(es.moment(instant)).toContain(`${String(local.getHours()).padStart(2, '0')}:30`);
  });
});
