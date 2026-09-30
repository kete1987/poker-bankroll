import { Button, Container, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';

/** Shown when rendering a route throws. */
export function ErrorPage() {
  const { t } = useTranslation();

  return (
    <Container size="sm" py="xl">
      <Stack gap="sm" align="flex-start">
        <Title order={2}>{t('error.title')}</Title>
        <Text c="dimmed">{t('error.description')}</Text>
        <Button onClick={() => window.location.reload()}>{t('error.reload')}</Button>
      </Stack>
    </Container>
  );
}
