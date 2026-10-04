import type { GameType } from '../api/types';

/** Which games: of any of these types, rooms, variants and tags (an empty list is every one). */
export interface GameScope {
  gameTypes: GameType[];
  roomIds: number[];
  variantIds: number[];
  tagIds: number[];
}

/** How many of the filters of a scope are set, for the count of a `FilterBar`. */
export function scopeFilterCount(scope: GameScope): number {
  return [scope.gameTypes, scope.roomIds, scope.variantIds, scope.tagIds].filter(
    (list) => list.length > 0,
  ).length;
}
