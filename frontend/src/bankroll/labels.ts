import type { TFunction } from 'i18next';

import type { Movement } from '../api/types';

/** One line that says which movement it is, e.g. to name the buttons of its row. */
export function describeMovement(t: TFunction, movement: Movement, date: string): string {
  return t('bankroll.movementName', {
    type: t(`movementTypes.${movement.type}`),
    date,
  });
}
