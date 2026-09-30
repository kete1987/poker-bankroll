import { AppShell, Group, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link, Outlet } from 'react-router';

import { ColorSchemeToggle } from '../components/ColorSchemeToggle';
import { LanguageSwitcher } from '../components/LanguageSwitcher';

/** Minimal shell; navigation comes with UI-1. */
export function RootLayout() {
  const { t } = useTranslation();

  return (
    <AppShell header={{ height: 56 }} padding="md">
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Title order={1} size="h4">
            <Link to="/" style={{ color: 'inherit', textDecoration: 'none' }}>
              {t('app.name')}
            </Link>
          </Title>
          <Group gap="sm">
            <LanguageSwitcher />
            <ColorSchemeToggle />
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
