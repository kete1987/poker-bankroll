import type { BankrollFigures, BankrollSummary, RoomBankroll } from '../api/types';
import type { MoneyView } from '../currency/view';

/** Figures in a currency. */
export interface FiguresIn {
  currencyCode: string;
  figures: BankrollFigures;
}

/** What the table lists: each room and the movements without a room in their own currency, and the total. */
export interface RoomsTableData {
  rooms: (RoomBankroll & { currencyCode: string })[];
  /** The movements that belong to no room, of each currency that has any. */
  withoutRoom: FiguresIn[];
  /** In the currency of the view: converted when the rooms are in several. */
  total: FiguresIn;
  /** The rows are in several currencies, or in another one than the total. */
  mixed: boolean;
}

export function hasMovements(figures: BankrollFigures): boolean {
  return [figures.deposited, figures.withdrawn, figures.bonuses, figures.adjustments].some(
    (amount) => amount !== 0,
  );
}

/**
 * The rows of the table for a view: the rooms of its currency or, converted, the rooms of every
 * currency in their own money (in the order of the converted list, by name) and the total
 * converted.
 */
export function roomsTableData(summary: BankrollSummary, view: MoneyView): RoomsTableData {
  if (!view.converted) {
    const currency = summary.currencies.find((one) => one.currencyCode === view.currencyCode);
    const currencyCode = view.currencyCode;
    return {
      rooms: (currency?.rooms ?? []).map((room) => ({ ...room, currencyCode })),
      withoutRoom:
        currency && hasMovements(currency.withoutRoom)
          ? [{ currencyCode, figures: currency.withoutRoom }]
          : [],
      total: { currencyCode, figures: currency?.total ?? summary.converted.total },
      mixed: false,
    };
  }
  const roomsById = new Map(
    summary.currencies.flatMap((currency) =>
      currency.rooms.map((room) => [
        room.room.id,
        { ...room, currencyCode: currency.currencyCode },
      ]),
    ),
  );
  return {
    rooms: summary.converted.rooms.flatMap((room) => {
      const original = roomsById.get(room.room.id);
      return original ? [original] : [];
    }),
    withoutRoom: summary.currencies
      .filter((currency) => hasMovements(currency.withoutRoom))
      .map((currency) => ({ currencyCode: currency.currencyCode, figures: currency.withoutRoom })),
    total: { currencyCode: summary.converted.currencyCode, figures: summary.converted.total },
    mixed: true,
  };
}
