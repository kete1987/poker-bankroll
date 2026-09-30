import { Anchor, Container, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

export function NotFoundPage() {
  const { t } = useTranslation();

  return (
    <Container size="sm" py="xl">
      <Stack gap="sm">
        <Title order={2}>{t('notFound.title')}</Title>
        <Text c="dimmed">{t('notFound.description')}</Text>
        <Anchor component={Link} to="/">
          {t('notFound.backHome')}
        </Anchor>
      </Stack>
    </Container>
  );
}
