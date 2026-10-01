import { Card, Stack, Text } from '@mantine/core';
import type { ReactNode } from 'react';

interface StatCardProps {
  label: string;
  /** The figure, already formatted. */
  value: string;
  /** Green for a gain, red for a loss. */
  tone?: 'positive' | 'negative';
  /** Smaller lines under the figure. */
  children?: ReactNode;
}

/** One headline figure of the dashboard, with what explains it underneath. */
export function StatCard({ label, value, tone, children }: StatCardProps) {
  return (
    <Card withBorder padding="md" component="section" aria-label={label}>
      <Stack gap={4}>
        <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
          {label}
        </Text>
        <Text
          fz={28}
          fw={700}
          lh={1.2}
          c={tone === 'positive' ? 'teal' : tone === 'negative' ? 'red' : undefined}
          style={{ whiteSpace: 'nowrap' }}
        >
          {value}
        </Text>
        <Text size="xs" c="dimmed" component="div">
          {children}
        </Text>
      </Stack>
    </Card>
  );
}
