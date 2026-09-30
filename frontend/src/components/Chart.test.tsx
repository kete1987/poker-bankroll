import { MantineProvider } from '@mantine/core';
import { render } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Chart } from './Chart';

const chartInstance = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() };

// Canvas rendering is not available in jsdom: check the wrapper talks to ECharts correctly.
vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: vi.fn(() => chartInstance),
}));

const echarts = await import('echarts/core');

describe('Chart', () => {
  beforeEach(() => {
    vi.mocked(echarts.init).mockClear();
    Object.values(chartInstance).forEach((fn) => fn.mockClear());
  });

  it('initialises ECharts with the given option on a transparent background', () => {
    render(
      <MantineProvider forceColorScheme="light">
        <Chart option={{ series: [{ type: 'line', data: [1, 2, 3] }] }} />
      </MantineProvider>,
    );

    expect(echarts.init).toHaveBeenCalledWith(expect.any(HTMLDivElement), undefined);
    expect(chartInstance.setOption).toHaveBeenCalledWith(
      { backgroundColor: 'transparent', series: [{ type: 'line', data: [1, 2, 3] }] },
      { notMerge: true },
    );
  });

  it('uses the dark ECharts theme in dark mode', () => {
    render(
      <MantineProvider forceColorScheme="dark">
        <Chart option={{}} />
      </MantineProvider>,
    );

    expect(echarts.init).toHaveBeenCalledWith(expect.any(HTMLDivElement), 'dark');
  });

  it('disposes the chart on unmount', () => {
    const { unmount } = render(
      <MantineProvider forceColorScheme="light">
        <Chart option={{}} />
      </MantineProvider>,
    );

    unmount();

    expect(chartInstance.dispose).toHaveBeenCalled();
  });
});
