import {
  IconCards,
  IconChartBar,
  IconLayoutDashboard,
  IconSettings,
  IconTransfer,
  IconWallet,
} from '@tabler/icons-react';

/** Sections of the app, in the order of the menu. `path` is also the route. */
export const NAVIGATION = [
  { path: '/', labelKey: 'nav.dashboard', icon: IconLayoutDashboard },
  { path: '/games', labelKey: 'nav.games', icon: IconCards },
  { path: '/bankroll', labelKey: 'nav.bankroll', icon: IconWallet },
  { path: '/stats', labelKey: 'nav.stats', icon: IconChartBar },
  { path: '/import', labelKey: 'nav.import', icon: IconTransfer },
  { path: '/settings', labelKey: 'nav.settings', icon: IconSettings },
] as const;

export type NavigationItem = (typeof NAVIGATION)[number];

/** A section is current on its own path and on the ones below it (the dashboard only on `/`). */
export function isCurrentSection(item: NavigationItem, pathname: string): boolean {
  return item.path === '/'
    ? pathname === '/'
    : pathname === item.path || pathname.startsWith(`${item.path}/`);
}
