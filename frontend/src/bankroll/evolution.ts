import type {
  BankrollEvolution,
  CurrencyEvolution,
  EvolutionPeriod,
  TimePeriod,
} from '../api/types';
import type { DateRange } from '../components/period';
import { evolutionIn, type MoneyView } from '../currency/view';

const DAY_MS = 24 * 60 * 60 * 1000;

/** The cut that keeps the chart readable: days up to three months, weeks up to two years, then months. */
export function granularityForDays(days: number): TimePeriod {
  return days <= 92 ? 'DAY' : days <= 731 ? 'WEEK' : 'MONTH';
}

function daysBetween(from: string, to: string): number {
  return (Date.parse(to) - Date.parse(from)) / DAY_MS + 1;
}

/**
 * The cut for a period with both ends, from its length. With an open end it depends on when there
 * was activity, which only the data says: nothing yet.
 */
export function granularityForRange(range: DateRange): TimePeriod | undefined {
  return range.from && range.to ? granularityForDays(daysBetween(range.from, range.to)) : undefined;
}

/**
 * The cut for a period with an open end, from the evolution by months in the currency of the
 * view: an open end is where the activity starts or ends.
 */
export function granularityForActivity(
  byMonth: BankrollEvolution,
  view: MoneyView,
  range: DateRange,
): TimePeriod {
  const periods = evolutionIn(byMonth, view)?.total.periods ?? [];
  const from = range.from ?? periods[0]?.startsOn;
  const to = range.to ?? periods.at(-1)?.endsOn;
  return from && to ? granularityForDays(daysBetween(from, to)) : 'MONTH';
}

/** A moment of the chart: where the range starts, the end of a period, or where the range ends. */
export interface EvolutionPoint {
  /** ISO date the point is drawn at. */
  date: string;
  kind: 'start' | 'period' | 'end';
  /** What changed the total bankroll in the period. */
  period?: EvolutionPeriod;
}

export interface EvolutionLine {
  /** Bankroll at each point. */
  values: number[];
}

/** A room whose line is converted: its bankroll at each point in its own currency. */
export interface OriginalLine extends EvolutionLine {
  currencyCode: string;
}

export interface EvolutionChartData {
  points: EvolutionPoint[];
  total: EvolutionLine;
  rooms: (EvolutionLine & { id: number; name: string; original?: OriginalLine })[];
}

/**
 * The series of the rooms in a currency other than `currencyCode`, by room id: in a converted
 * evolution, what each of those rooms has in its own money.
 */
export function originalSeries(
  evolution: BankrollEvolution,
  currencyCode: string,
): Map<number, { currencyCode: string; series: CurrencyEvolution['total'] }> {
  return new Map(
    evolution.currencies
      .filter((currency) => currency.currencyCode !== currencyCode)
      .flatMap((currency) =>
        currency.rooms.map(
          (room) =>
            [room.room.id, { currencyCode: currency.currencyCode, series: room.series }] as const,
        ),
      ),
  );
}

const earliest = (a: string, b: string) => (a < b ? a : b);
const latest = (a: string, b: string) => (a > b ? a : b);

/**
 * The lines of a currency, one point per period of its total and the same points for every room
 * (a room keeps its bankroll through the periods it has nothing in). A period is drawn at its last
 * day, or at the last day of the range or today when it ends later. The lines start with the
 * bankroll when the range starts (at the first period without one) and go on to the end of the
 * range, or today. Nothing when there is no point to start from.
 *
 * Games and movements may be dated after today, and they count in the bankroll as it is now: a
 * period that starts after today is drawn at its first day, so the last point is still that
 * bankroll.
 *
 * With `originals` (a converted evolution), the rooms in another currency also have their
 * bankroll in their own money at each point.
 */
export function chartData(
  currency: CurrencyEvolution,
  range: DateRange,
  today: string,
  originals: ReturnType<typeof originalSeries> = new Map(),
): EvolutionChartData | undefined {
  const periods = currency.total.periods;
  const start = range.from ?? periods[0]?.startsOn;
  if (!start) {
    return undefined;
  }
  const end = earliest(range.to ?? today, today);
  const points: EvolutionPoint[] = [{ date: start, kind: 'start' }];
  for (const period of periods) {
    const date = latest(start, latest(period.startsOn, earliest(period.endsOn, end)));
    points.push({ date, kind: 'period', period });
  }
  if (end > (points.at(-1)?.date ?? start)) {
    points.push({ date: end, kind: 'end' });
  }

  const valuesOf = (startingBankroll: number, byPeriod: Map<string, number>) => {
    let bankroll = startingBankroll;
    return points.map((point) => {
      const ofPeriod = point.period ? byPeriod.get(point.period.period) : undefined;
      bankroll = ofPeriod ?? bankroll;
      return bankroll;
    });
  };
  const bankrollByPeriod = (series: { periods: EvolutionPeriod[] }) =>
    new Map(series.periods.map((period) => [period.period, period.bankroll]));

  return {
    points,
    total: { values: valuesOf(currency.total.startingBankroll, bankrollByPeriod(currency.total)) },
    rooms: currency.rooms.map((room) => {
      const original = originals.get(room.room.id);
      return {
        id: room.room.id,
        name: room.room.name,
        values: valuesOf(room.series.startingBankroll, bankrollByPeriod(room.series)),
        ...(original && {
          original: {
            currencyCode: original.currencyCode,
            values: valuesOf(original.series.startingBankroll, bankrollByPeriod(original.series)),
          },
        }),
      };
    }),
  };
}
