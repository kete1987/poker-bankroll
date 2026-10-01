import type { ReactElement } from 'react';
import type { RouteObject } from 'react-router';

import { NAVIGATION, type NavigationItem } from './layout/navigation';
import { RootLayout } from './layout/RootLayout';
import { BankrollPage } from './pages/BankrollPage';
import { DashboardPage } from './pages/DashboardPage';
import { ErrorPage } from './pages/ErrorPage';
import { GamesPage } from './pages/GamesPage';
import { ImportPage } from './pages/ImportPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { SettingsPage } from './pages/SettingsPage';
import { StatsPage } from './pages/StatsPage';

/** The page of each section. */
const PAGES: Record<NavigationItem['path'], ReactElement> = {
  '/': <DashboardPage />,
  '/bankroll': <BankrollPage />,
  '/games': <GamesPage />,
  '/stats': <StatsPage />,
  '/import': <ImportPage />,
  '/settings': <SettingsPage />,
};

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <RootLayout />,
    errorElement: <ErrorPage />,
    children: [
      ...NAVIGATION.map((section) => ({
        path: section.path,
        element: PAGES[section.path],
      })),
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
