import { Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import { Page } from '../components/Page';
import type { NavigationItem } from '../layout/navigation';

/** Placeholder of a section that is not built yet. */
export function ComingSoonPage({ section }: { section: NavigationItem }) {
  const { t } = useTranslation();

  return (
    <Page title={t(section.labelKey)}>
      <Text c="dimmed">{t('page.comingSoon')}</Text>
    </Page>
  );
}
