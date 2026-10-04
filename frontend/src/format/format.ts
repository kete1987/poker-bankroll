import type { Language } from '../i18n';

/**
 * Formatting of dates, numbers and money for the UI language, with `Intl`. The backend sends
 * plain values (ISO dates, numbers, fractions for rates); nothing is formatted or added up there
 * for display, and nothing is computed here.
 */

/** Regional format used for a language when the browser does not say one for it. */
const DEFAULT_LOCALE: Record<Language, string> = { en: 'en-GB', es: 'es-ES' };

/**
 * Locale to format with: the browser's own when it is in the UI language (so `en-US` users get
 * their date order), otherwise the default one of the language.
 */
export function localeFor(
  language: Language,
  browserLocales: readonly string[] = browserLanguages(),
): string {
  const match = browserLocales.find(
    (locale) => locale.toLowerCase().split('-')[0] === language && locale.includes('-'),
  );
  return match ?? DEFAULT_LOCALE[language];
}

function browserLanguages(): readonly string[] {
  return typeof navigator === 'undefined' ? [] : (navigator.languages ?? [navigator.language]);
}

export interface Formatters {
  /** Amount with its currency, e.g. `1.234,50 €`. */
  money(amount: number, currencyCode: string): string;
  /** Like {@link money} with an explicit sign for gains, e.g. `+12,00 €`. */
  signedMoney(amount: number, currencyCode: string): string;
  /** Plain number, e.g. a count of games. */
  number(value: number): string;
  /** Fraction as a percentage: `0.3496` is `34,96 %`. `null` (unknown) is shown as a dash. */
  percent(fraction: number | null | undefined): string;
  /** ISO date (`2026-01-19`) as a numeric date, e.g. `19/01/2026`. */
  date(isoDate: string): string;
  /** ISO date as a long one, e.g. `19 de enero de 2026`. */
  longDate(isoDate: string): string;
  /** ISO month (`2026-01`) or date, e.g. `enero de 2026`. */
  month(isoMonth: string): string;
  /** Name of a day of the week, from 1 (Monday) to 7 (Sunday), e.g. `lunes`. */
  weekday(day: number): string;
  /** ISO time (`21:30:00`) as `21:30`. */
  time(isoTime: string): string;
  /** An instant (`2026-10-04T15:30:00Z`) as a date and time where the browser is. */
  moment(isoInstant: string): string;
  /** An exchange rate, with 4 decimals at least and 8 at most, e.g. `1,0850`. */
  rate(value: number): string;
  /** Character between the integer and decimal parts (`,` or `.`), for number inputs. */
  decimalSeparator: string;
}

/** Amounts are `NUMERIC(12,2)` in the backend, whatever the currency. */
const MAX_AMOUNT_DECIMALS = 2;

/** What is shown instead of a value that does not exist (e.g. ROI with nothing invested). */
export const NO_VALUE = '—';

export function createFormatters(locale: string): Formatters {
  const moneyFormats = new Map<string, Intl.NumberFormat>();
  const moneyFormat = (currencyCode: string, signed: boolean) => {
    const key = `${currencyCode}/${signed}`;
    let format = moneyFormats.get(key);
    if (!format) {
      // Decimals of the currency (none for JPY), but never more than the two that amounts are
      // stored with: a three-decimal currency such as KWD is shown with two.
      const decimals = Math.min(
        MAX_AMOUNT_DECIMALS,
        new Intl.NumberFormat(locale, {
          style: 'currency',
          currency: currencyCode,
        }).resolvedOptions().maximumFractionDigits ?? MAX_AMOUNT_DECIMALS,
      );
      format = new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: currencyCode,
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
        // Zero is neither a gain nor a loss.
        signDisplay: signed ? 'exceptZero' : 'auto',
        useGrouping: 'always',
      });
      moneyFormats.set(key, format);
    }
    return format;
  };
  // Always group thousands: Spanish would otherwise leave four-digit numbers ungrouped (1234,50 €).
  const numberFormat = new Intl.NumberFormat(locale, { useGrouping: 'always' });
  const percentFormat = new Intl.NumberFormat(locale, {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  // Dates have no time zone: they are formatted in UTC so the day never shifts.
  const dateFormat = new Intl.DateTimeFormat(locale, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'UTC',
  });
  const longDateFormat = new Intl.DateTimeFormat(locale, { dateStyle: 'long', timeZone: 'UTC' });
  const weekdayFormat = new Intl.DateTimeFormat(locale, { weekday: 'long', timeZone: 'UTC' });
  // As dates are shown, with the time; in the time zone of the browser.
  const momentFormat = new Intl.DateTimeFormat(locale, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
  const rateFormat = new Intl.NumberFormat(locale, {
    minimumFractionDigits: 4,
    maximumFractionDigits: 8,
    useGrouping: 'always',
  });
  const monthFormat = new Intl.DateTimeFormat(locale, {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  });

  return {
    money: (amount, currencyCode) => moneyFormat(currencyCode, false).format(amount),
    signedMoney: (amount, currencyCode) => moneyFormat(currencyCode, true).format(amount),
    number: (value) => numberFormat.format(value),
    percent: (fraction) => (fraction == null ? NO_VALUE : percentFormat.format(fraction)),
    date: (isoDate) => dateFormat.format(parseIsoDate(isoDate)),
    longDate: (isoDate) => longDateFormat.format(parseIsoDate(isoDate)),
    month: (isoMonth) => monthFormat.format(parseIsoDate(isoMonth)),
    // 1 January 2024 was a Monday.
    weekday: (day) => weekdayFormat.format(new Date(Date.UTC(2024, 0, day))),
    time: (isoTime) => isoTime.slice(0, 5),
    moment: (isoInstant) => momentFormat.format(new Date(isoInstant)),
    rate: (value) => rateFormat.format(value),
    decimalSeparator:
      new Intl.NumberFormat(locale).formatToParts(1.5).find((part) => part.type === 'decimal')
        ?.value ?? '.',
  };
}

/** `2026-01-19`, `2026-01` or `2026` as the UTC midnight that starts it. */
function parseIsoDate(value: string): Date {
  const [year, month = '1', day = '1'] = value.split('-');
  return new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
}
