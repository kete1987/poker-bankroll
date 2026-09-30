import { Badge, Container, Group, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import { useApiHealth } from '../api/health';

export function HomePage() {
  const { t } = useTranslation();

  return (
    <Container size="md" py="xl">
      <Stack gap="md">
        <Title order={2}>{t('home.title')}</Title>
        <Text c="dimmed">{t('home.subtitle')}</Text>
        <ApiStatus />
      </Stack>
    </Container>
  );
}

function ApiStatus() {
  const { t } = useTranslation();
  const health = useApiHealth();

  // A failed refresh keeps the previous data, so the error state must win over it.
  const isUp = !health.isError && health.data?.status === 'UP';
  const [color, label] = health.isPending
    ? ['gray', t('home.apiStatus.checking')]
    : isUp
      ? ['teal', t('home.apiStatus.up')]
      : ['red', t('home.apiStatus.down')];

  return (
    <Group gap="xs">
      <Text size="sm">{t('home.apiStatus.label')}</Text>
      <Badge color={color} variant="light" data-testid="api-status">
        {label}
      </Badge>
    </Group>
  );
}
