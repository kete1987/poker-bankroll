import { AppShell, Burger, Group, NavLink, ScrollArea, Text, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { useTranslation } from 'react-i18next';
import { Link, Outlet, useLocation } from 'react-router';

import { ApiStatus } from '../components/ApiStatus';
import { ColorSchemeToggle } from '../components/ColorSchemeToggle';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import { APP_VERSION, formatVersion } from '../version';
import { isCurrentSection, NAVIGATION } from './navigation';

/** Shell of every page: header, menu of sections (a drawer on narrow screens) and the page itself. */
export function RootLayout() {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const [menuOpened, menu] = useDisclosure(false);

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 220, breakpoint: 'sm', collapsed: { mobile: !menuOpened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger
              opened={menuOpened}
              onClick={menu.toggle}
              hiddenFrom="sm"
              size="sm"
              aria-label={t('nav.toggleMenu')}
              aria-expanded={menuOpened}
            />
            <Title order={1} size="h4">
              <Link
                to="/"
                onClick={menu.close}
                style={{ color: 'inherit', textDecoration: 'none' }}
              >
                {t('app.name')}
              </Link>
            </Title>
          </Group>
          <Group gap="sm" wrap="nowrap">
            <LanguageSwitcher />
            <ColorSchemeToggle />
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="xs">
        <AppShell.Section grow component={ScrollArea}>
          <nav aria-label={t('nav.label')}>
            {NAVIGATION.map((item) => {
              const current = isCurrentSection(item, pathname);
              return (
                <NavLink
                  key={item.path}
                  component={Link}
                  to={item.path}
                  label={t(item.labelKey)}
                  leftSection={<item.icon size={18} stroke={1.5} />}
                  active={current}
                  aria-current={current ? 'page' : undefined}
                  onClick={menu.close}
                />
              );
            })}
          </nav>
        </AppShell.Section>
        <AppShell.Section>
          <Group justify="space-between" px="xs" py={4}>
            <ApiStatus />
            <Text size="xs" c="dimmed" data-testid="app-version">
              {formatVersion(APP_VERSION)}
            </Text>
          </Group>
        </AppShell.Section>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
