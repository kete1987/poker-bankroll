import { Badge, Group, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import { useApiHealth } from '../api/health';

/** Whether the backend answers, refreshed periodically. */
export function ApiStatus() {
  const { t } = useTranslation();
  const health = useApiHealth();

  // A failed refresh keeps the previous data, so the error state must win over it.
  const isUp = !health.isError && health.data?.status === 'UP';
  const [color, label] = health.isPending
    ? ['gray', t('apiStatus.checking')]
    : isUp
      ? ['teal', t('apiStatus.up')]
      : ['red', t('apiStatus.down')];

  return (
    <Group gap={6} wrap="nowrap">
      <Text size="xs" c="dimmed">
        {t('apiStatus.label')}
      </Text>
      <Badge size="sm" color={color} variant="light" data-testid="api-status">
        {label}
      </Badge>
    </Group>
  );
}
