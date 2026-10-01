/**
 * Reading filters from the URL. Anything that makes no sense there (typed by hand, an old link)
 * is ignored instead of being sent to the backend, which would answer with an error.
 */

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Largest id or page worth sending: beyond it the backend could not even read the number. */
const MAX_INTEGER = 2_147_483_647;

/** A real day of the calendar (`2026-02-31` has the right shape but does not exist), or nothing. */
export function parseDate(value: string | null): string | undefined {
  if (value === null || !ISO_DATE.test(value)) {
    return undefined;
  }
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
    ? value
    : undefined;
}

/** A positive integer the backend can read, or nothing. */
export function parsePositiveInteger(value: string | null): number | undefined {
  if (value === null || !/^\d+$/.test(value)) {
    return undefined;
  }
  const number = Number(value);
  return number > 0 && number <= MAX_INTEGER ? number : undefined;
}

/** The valid values of a comma-separated parameter, each one once and in the order given. */
export function parseList<T>(value: string | null, parseOne: (text: string) => T | undefined): T[] {
  const values = (value ?? '')
    .split(',')
    .map((text) => parseOne(text.trim()))
    .filter((item): item is T => item !== undefined);
  return [...new Set(values)];
}

/** One of the given values, or nothing. */
export function parseOneOf<T extends string>(
  value: string | null,
  allowed: readonly T[],
): T | undefined {
  return (allowed as readonly string[]).includes(value ?? '') ? (value as T) : undefined;
}
