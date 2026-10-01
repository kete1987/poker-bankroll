import type { GameStatus, GameType } from '../api/types';

/** What the add-game form starts with: the choices of the last game recorded on this browser. */
export interface GameDefaults {
  roomId?: number;
  gameType?: GameType;
  status?: GameStatus;
}

const STORAGE_KEY = 'poker-bankroll.game-defaults';
const GAME_TYPES: readonly string[] = ['TOURNAMENT', 'SIT_AND_GO', 'CASH'];
const STATUSES: readonly string[] = ['IN_PLAY', 'FINISHED'];

export function loadGameDefaults(): GameDefaults {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}');
    if (typeof stored !== 'object' || stored === null) {
      return {};
    }
    const { roomId, gameType, status } = stored as Record<string, unknown>;
    return {
      roomId: typeof roomId === 'number' ? roomId : undefined,
      gameType: GAME_TYPES.includes(gameType as string) ? (gameType as GameType) : undefined,
      status: STATUSES.includes(status as string) ? (status as GameStatus) : undefined,
    };
  } catch {
    // Unreadable or unavailable storage: start without remembered choices.
    return {};
  }
}

export function saveGameDefaults(defaults: GameDefaults): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(defaults));
  } catch {
    // Storage may be unavailable (private mode): the choices are just not remembered.
  }
}

/** Today in the time zone of the browser, as an ISO date. */
export function todayIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}
