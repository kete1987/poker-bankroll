import { parsePositiveInteger } from './urlParams';

export const DEFAULT_PAGE_SIZE = 25;
export const PERIOD_PAGE_SIZE = 31;

/** Keep the existing default selectable, including the 31-row table of periods. */
export function pageSizeOptions(defaultSize = DEFAULT_PAGE_SIZE): number[] {
  return [...new Set([10, 25, 50, 100, 200, defaultSize])].sort((a, b) => a - b);
}

export function parsePageSize(value: string | null, defaultSize = DEFAULT_PAGE_SIZE): number {
  const size = parsePositiveInteger(value);
  return size !== undefined && pageSizeOptions(defaultSize).includes(size) ? size : defaultSize;
}
