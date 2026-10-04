import {
  Alert,
  Group,
  Loader,
  Select,
  Stack,
  Text,
  Title,
  useComputedColorScheme,
  useMantineTheme,
} from '@mantine/core';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import { useBankrollEvolution } from '../api/bankroll';
import type { MissingExchangeRate, TimePeriod } from '../api/types';
import { Chart } from '../components/Chart';
import type { DateRange } from '../components/period';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { MissingRatesAlert } from '../currency/MissingRatesAlert';
import { evolutionIn, type MoneyView } from '../currency/view';
import { useFormat } from '../format/useFormat';
import { todayIso } from '../games/gameDefaults';
import {
  chartData,
  granularityForActivity,
  granularityForRange,
  originalSeries,
  type EvolutionChartData,
} from './evolution';
import { GRANULARITIES } from './useBankrollFilters';

interface BankrollEvolutionChartProps {
  range: DateRange;
  roomIds: number[];
  /** The currency of the screen: the only one there is, or the base one, converted. */
  view: MoneyView;
  /** Exchange rates the screen already says are missing; the chart only adds the others. */
  shownMissing?: MissingExchangeRate[];
  /** Chosen by the user; otherwise it follows the length of the period. */
  granularity?: TimePeriod;
  onGranularityChange: (granularity: TimePeriod | undefined) => void;
}

/** What the selector offers before the cuts: leaving it to the length of the period. */
const AUTO = 'AUTO';

/** Only what the API or the translations wrote goes in a tooltip; names typed by the user are escaped. */
function escapeHtml(text: string): string {
  return text.replace(
    /[&<>"']/g,
    (character) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character] ??
      character,
  );
}

interface TooltipParam {
  seriesType?: string;
  seriesId?: string;
  seriesName?: string;
  dataIndex: number;
  marker?: string;
  value?: [number, number];
}

/**
 * The bankroll over time: one thin line per room and the total, bold, on top, with the deposits
 * and withdrawals marked on it. The lines start with the bankroll there was when the period
 * starts, so they end at the bankroll of the rooms table. Converted, each point is the bankroll
 * of each currency then, at the rates of that day; the tooltip also gives each room in its own
 * currency.
 */
export function BankrollEvolutionChart({
  range,
  roomIds,
  view,
  shownMissing = [],
  granularity,
  onGranularityChange,
}: BankrollEvolutionChartProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const theme = useMantineTheme();
  const narrow = useNarrowScreen();
  const dark = useComputedColorScheme('light', { getInitialValueInEffect: false }) === 'dark';

  const query = { from: range.from, to: range.to, roomId: roomIds };
  // Without a cut chosen, a period with both ends says it; one with an open end needs the dates
  // of the activity first, which the evolution by months gives.
  const fixed = granularity ?? granularityForRange(range);
  const byMonth = useBankrollEvolution('MONTH', query, fixed === undefined);
  const currencyCode = view.currencyCode;
  const chosen =
    fixed ?? (byMonth.data ? granularityForActivity(byMonth.data, view, range) : undefined);
  // When the months are what the activity asks for, those already loaded are drawn.
  const monthsWillDo = fixed === undefined && chosen === 'MONTH';
  const ofChosen = useBankrollEvolution(
    chosen ?? 'MONTH',
    query,
    chosen !== undefined && !monthsWillDo,
  );
  const evolution = monthsWillDo ? byMonth : ofChosen;
  const drawn = evolution.data?.groupBy ?? chosen;

  const data: EvolutionChartData | undefined = useMemo(() => {
    if (!evolution.data) {
      return undefined;
    }
    const ofView = evolutionIn(evolution.data, view);
    const originals = view.converted
      ? originalSeries(evolution.data, view.currencyCode)
      : undefined;
    return ofView && chartData(ofView, range, todayIso(), originals);
  }, [evolution.data, view, range]);
  const missing = view.converted
    ? (evolution.data?.converted.missingRates ?? []).filter(
        (rate) => !shownMissing.some((shown) => shown.currencyCode === rate.currencyCode),
      )
    : [];

  const option = useMemo(() => {
    if (!data) {
      return undefined;
    }
    const money = (amount: number) => format.money(amount, currencyCode);
    const signed = (amount: number) => format.signedMoney(amount, currencyCode);
    const times = data.points.map((point) => Date.parse(point.date));
    const line = (values: number[]) => values.map((value, index) => [times[index], value]);
    const totalName = t('bankroll.evolution.total');
    const totalColor = dark ? theme.colors.gray[2] : theme.colors.dark[7];
    const markers = (amountOf: (index: number) => number) =>
      data.points.flatMap((_, index) =>
        amountOf(index) > 0 ? [[times[index], data.total.values[index]]] : [],
      );
    const periodLabel = (period: string) =>
      drawn === 'MONTH'
        ? format.month(period)
        : drawn === 'WEEK'
          ? t('stats.weekOf', { date: format.date(period) })
          : drawn === 'YEAR'
            ? period
            : format.date(period);

    return {
      grid: { left: 8, right: 16, top: 40, bottom: 8, containLabel: true },
      legend: { type: 'scroll', top: 0 },
      tooltip: {
        trigger: 'axis',
        confine: true,
        axisPointer: { label: { show: false } },
        formatter: (params: TooltipParam[]) => {
          const lines = params.filter((param) => param.seriesType === 'line');
          const point = data.points[lines[0]?.dataIndex ?? -1];
          if (!point) {
            return '';
          }
          const heading =
            point.kind === 'start'
              ? t('bankroll.evolution.start', { date: format.date(point.date) })
              : point.period
                ? periodLabel(point.period.period)
                : format.date(point.date);
          const period = point.period;
          const changes = period
            ? [
                [t('bankroll.columns.deposited'), money(period.deposited)],
                [t('bankroll.columns.withdrawn'), money(period.withdrawn)],
                [t('bankroll.columns.bonuses'), money(period.bonuses)],
                [t('bankroll.columns.adjustments'), signed(period.adjustments)],
                [t('bankroll.columns.gamesNet'), signed(period.gamesNet)],
              ].map(([label, value]) => `${label}: ${value}`)
            : [];
          const bankrolls = lines.map((param) => {
            // A room in another currency: also what it has in its own.
            const original = data.rooms.find(
              (room) => `room-${room.id}` === param.seriesId,
            )?.original;
            const own = original
              ? ` (${format.money(original.values[param.dataIndex] ?? 0, original.currencyCode)})`
              : '';
            return `${param.marker ?? ''}${escapeHtml(param.seriesName ?? '')}: <strong>${money(param.value?.[1] ?? 0)}</strong>${own}`;
          });
          return [`<strong>${heading}</strong>`, ...changes, ...bankrolls].join('<br/>');
        },
      },
      xAxis: {
        type: 'time',
        splitNumber: narrow ? 3 : 6,
        axisLabel: {
          hideOverlap: true,
          formatter: (value: number) => format.date(new Date(value).toISOString().slice(0, 10)),
        },
      },
      yAxis: {
        type: 'value',
        axisLabel: { formatter: (value: number) => money(value) },
      },
      series: [
        ...data.rooms.map((room) => ({
          id: `room-${room.id}`,
          name: room.name,
          type: 'line',
          data: line(room.values),
          showSymbol: false,
          lineStyle: { width: 1.5 },
          z: 2,
        })),
        {
          id: 'total',
          name: totalName,
          type: 'line',
          data: line(data.total.values),
          // With many points the markers only add noise.
          showSymbol: data.points.length <= 60,
          symbolSize: 5,
          itemStyle: { color: totalColor },
          lineStyle: { color: totalColor, width: 3 },
          z: 3,
        },
        {
          id: 'deposits',
          name: t('bankroll.evolution.deposits'),
          type: 'scatter',
          symbol: 'triangle',
          symbolSize: 12,
          itemStyle: { color: theme.colors.teal[6] },
          data: markers((index) => data.points[index]?.period?.deposited ?? 0),
          z: 4,
        },
        {
          id: 'withdrawals',
          name: t('bankroll.evolution.withdrawals'),
          type: 'scatter',
          symbol: 'triangle',
          symbolRotate: 180,
          symbolSize: 12,
          itemStyle: { color: theme.colors.red[6] },
          data: markers((index) => data.points[index]?.period?.withdrawn ?? 0),
          z: 4,
        },
      ],
    };
  }, [data, drawn, currencyCode, format, t, theme, dark, narrow]);

  const autoLabel = drawn
    ? t('bankroll.evolution.autoAs', {
        granularity: t(`bankroll.evolution.groupBy.${drawn}`).toLocaleLowerCase(),
      })
    : t('bankroll.evolution.groupBy.AUTO');
  // The months only matter while they are asked for: once a cut is chosen, their failure is past.
  const failed = (fixed === undefined && byMonth.isError) || evolution.isError;

  return (
    <Stack gap="xs">
      <Group justify="space-between" align="flex-end">
        <Title order={3} size="h4">
          {t('bankroll.evolution.title')}
        </Title>
        <Select
          aria-label={t('stats.groupBy')}
          w={200}
          allowDeselect={false}
          data={[
            { value: AUTO, label: granularity ? t('bankroll.evolution.groupBy.AUTO') : autoLabel },
            ...GRANULARITIES.map((value) => ({
              value,
              label: t(`bankroll.evolution.groupBy.${value}`),
            })),
          ]}
          value={granularity ?? AUTO}
          onChange={(value) =>
            onGranularityChange(GRANULARITIES.find((candidate) => candidate === value) ?? undefined)
          }
        />
      </Group>
      <MissingRatesAlert missing={missing} />
      {failed ? (
        <Alert color="red">{t('games.loadError')}</Alert>
      ) : !evolution.data ? (
        <Loader />
      ) : !option ? (
        <Text c="dimmed">{t('bankroll.evolution.empty')}</Text>
      ) : (
        <div style={{ opacity: evolution.isPlaceholderData ? 0.6 : 1 }}>
          <Chart option={option} height={narrow ? 300 : 360} />
        </div>
      )}
    </Stack>
  );
}
