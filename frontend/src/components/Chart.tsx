import { useComputedColorScheme } from '@mantine/core';
import { BarChart, LineChart } from 'echarts/charts';
import {
  DatasetComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { useEffect, useRef } from 'react';

// Register only what the app uses, so the bundle does not include every chart type.
echarts.use([
  LineChart,
  BarChart,
  DatasetComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);

export interface ChartProps {
  option: echarts.EChartsCoreOption;
  height?: number | string;
}

/** ECharts wrapper: follows the light/dark scheme and resizes with its container. */
export function Chart({ option, height = 320 }: ChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts | null>(null);
  const colorScheme = useComputedColorScheme('light', { getInitialValueInEffect: false });

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const chart = echarts.init(container, colorScheme === 'dark' ? 'dark' : undefined);
    chartRef.current = chart;
    const resizeObserver = new ResizeObserver(() => chart.resize());
    resizeObserver.observe(container);
    return () => {
      resizeObserver.disconnect();
      chart.dispose();
      chartRef.current = null;
    };
  }, [colorScheme]);

  useEffect(() => {
    chartRef.current?.setOption({ backgroundColor: 'transparent', ...option }, { notMerge: true });
  }, [option, colorScheme]);

  return <div ref={containerRef} style={{ width: '100%', height }} />;
}
