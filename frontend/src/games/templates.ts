import type { TFunction } from 'i18next';

import type { Game, GameRequest, GameTemplate, GameTemplateRequest } from '../api/types';
import type { Formatters } from '../format/format';
import { todayIso } from './gameDefaults';
import { variantLabel } from './labels';

/**
 * What a new game can start from: a game recorded before (duplicate) or a template. Its room,
 * type, variant, modality, name and buy-in (in `currencyCode`) are copied into the form, and the
 * tags of a game (a template has none).
 */
export type GameStart = Pick<
  Game,
  'room' | 'gameType' | 'variant' | 'modality' | 'name' | 'buyIn' | 'currencyCode'
> &
  Partial<Pick<Game, 'tags'>>;

/**
 * Name of a template: its label, or what it is made of, e.g. "Winamax · Expresso · 5,00 €" (the
 * name of its games, else its variant, else its type).
 */
export function templateLabel(
  t: TFunction,
  format: Formatters,
  template: Pick<GameTemplate, 'label' | 'name' | 'variant' | 'gameType' | 'room' | 'buyIn'> & {
    currencyCode: string;
  },
): string {
  if (template.label) {
    return template.label;
  }
  const what =
    template.name ??
    (template.variant ? variantLabel(t, template.variant) : t(`gameTypes.${template.gameType}`));
  return [template.room.name, what, format.money(template.buyIn, template.currencyCode)].join(
    ' · ',
  );
}

/** The game a template starts with one click: today, in play, with what the template says. */
export function gameOfTemplate(template: GameTemplate, today = todayIso()): GameRequest {
  return {
    playedOn: today,
    playedAt: null,
    roomId: template.room.id,
    gameType: template.gameType,
    modality: template.modality,
    variantId: template.variant?.id ?? null,
    status: 'IN_PLAY',
    name: template.name ?? null,
    buyIn: template.buyIn,
    entries: null,
    paidWithTicket: false,
    prize: null,
    bounty: null,
    ticketPrizeValue: null,
    ticketDescription: null,
    notes: null,
  };
}

/** A template that starts games like this one, with an optional label. */
export function templateOfGame(game: Game, label: string): GameTemplateRequest {
  return {
    label: label.trim() || null,
    roomId: game.room.id,
    gameType: game.gameType,
    modality: game.modality,
    variantId: game.variant?.id ?? null,
    name: game.name ?? null,
    buyIn: game.buyIn,
  };
}
