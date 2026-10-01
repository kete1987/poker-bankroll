import { Anchor, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { Page } from '../components/Page';

export function NotFoundPage() {
  const { t } = useTranslation();

  return (
    <Page title={t('notFound.title')}>
      <Text c="dimmed">{t('notFound.description')}</Text>
      <Anchor component={Link} to="/">
        {t('notFound.backHome')}
      </Anchor>
    </Page>
  );
}
