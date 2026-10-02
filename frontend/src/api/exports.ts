import { apiFetchFile, type ApiFile } from './client';
import type { GameQuery } from './games';
import type { MovementQuery } from './bankroll';
import { toQueryString } from './query';
import type { ExportFormat } from './types';

/** The filters of the games list; the status is not one: only finished games are exported. */
export type GameExportQuery = Omit<GameQuery, 'status' | 'page' | 'size' | 'sort'>;

/** The filters of the list of movements. */
export type MovementExportQuery = Omit<MovementQuery, 'page' | 'size'>;

/**
 * Every finished game the filters select, not a page of them, as a file: CSV in the format of
 * the import, or Excel in the language of the interface.
 */
export function exportGames(format: ExportFormat, query: GameExportQuery): Promise<ApiFile> {
  return apiFetchFile(`/exports/games?${toQueryString({ format, ...query })}`);
}

/** Every movement the filters select, not a page of them, as a CSV or Excel file. */
export function exportMovements(
  format: ExportFormat,
  query: MovementExportQuery,
): Promise<ApiFile> {
  return apiFetchFile(`/exports/movements?${toQueryString({ format, ...query })}`);
}
