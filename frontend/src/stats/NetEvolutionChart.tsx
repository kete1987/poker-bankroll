import { useMantineTheme } from '@mantine/core';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import { Chart } from '../components/Chart';
import { useFormat } from '../format/useFormat';

/** One point of the curve: a day, a week or a month with games. */
export interface NetPoint {
  /** The period, as it is named in full. */
  label: string;
  /** The period on the axis, where there is less room. */
  axisLabel: string;
  games: number;
  net: number;
  /** Net of this period and the earlier ones of the range. */
  cumulativeNet: number;
}

interface NetEvolutionChartProps {
  points: NetPoint[];
  currencyCode: string;
}

/**
 * Cumulative net of the finished games over time, starting from zero at the beginning of the
 * period: green above zero, red below. Periods without games have no point.
 */
export function NetEvolutionChart({ points, currencyCode }: NetEvolutionChartProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const theme = useMantineTheme();

  const option = useMemo(() => {
    const gain = theme.colors.teal[6];
    const loss = theme.colors.red[6];
    const values = points.map((point) => point.cumulativeNet);
    const lowest = Math.min(0, ...values);
    const highest = Math.max(0, ...values);
    return {
      grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
      tooltip: {
        trigger: 'axis',
        formatter: (params: { dataIndex: number }[]) => {
          const point = points[params[0]?.dataIndex ?? -1];
          if (!point) {
            return '';
          }
          // Only labels and formatted numbers: nothing typed by the user goes in.
          return [
            `<strong>${point.label}</strong>`,
            `${t('stats.net.tooltipNet')}: ${format.signedMoney(point.net, currencyCode)}`,
            t('stats.net.tooltipGames', { count: point.games }),
            `${t('stats.net.tooltipCumulative')}: <strong>${format.signedMoney(point.cumulativeNet, currencyCode)}</strong>`,
          ].join('<br/>');
        },
      },
      xAxis: { type: 'category', boundaryGap: false, data: points.map((point) => point.axisLabel) },
      yAxis: {
        type: 'value',
        axisLabel: { formatter: (value: number) => format.money(value, currencyCode) },
      },
      visualMap: {
        show: false,
        dimension: 1,
        // ECharts needs closed ranges to colour a line by its value.
        pieces: [
          { gt: lowest - 1, lte: 0, color: loss },
          { gt: 0, lte: highest + 1, color: gain },
        ],
      },
      series: [
        {
          type: 'line',
          data: values,
          // With many points the markers only add noise.
          showSymbol: points.length <= 60,
          areaStyle: { opacity: 0.12 },
          markLine: {
            silent: true,
            symbol: 'none',
            label: { show: false },
            lineStyle: { color: theme.colors.gray[6], type: 'dashed' },
            data: [{ yAxis: 0 }],
          },
        },
      ],
    };
  }, [points, currencyCode, format, t, theme]);

  return <Chart option={option} height={360} />;
}
