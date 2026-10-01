/**
 * What a number input holds: a number, or text while it is empty or cannot be a number yet.
 * Mantine also gives text for values such as `8.40`, to keep the zero that was typed.
 */
export type Amount = number | string;

/** The amount typed, or `null` when the input is empty (or holds no number). */
export function amountOrNull(value: Amount): number | null {
  if (typeof value === 'number') {
    return value;
  }
  const amount = value.trim() === '' ? Number.NaN : Number(value);
  return Number.isFinite(amount) ? amount : null;
}
