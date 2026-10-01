import type { ReactElement } from 'react';
import type { RouteObject } from 'react-router';

import { NAVIGATION, type NavigationItem } from './layout/navigation';
import { RootLayout } from './layout/RootLayout';
import { ComingSoonPage } from './pages/ComingSoonPage';
import { DashboardPage } from './pages/DashboardPage';
import { ErrorPage } from './pages/ErrorPage';
import { GamesPage } from './pages/GamesPage';
import { NotFoundPage } from './pages/NotFoundPage';

/** Sections that already have their page; the others show a placeholder. */
const PAGES: Partial<Record<NavigationItem['path'], ReactElement>> = {
  '/': <DashboardPage />,
  '/games': <GamesPage />,
};

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <RootLayout />,
    errorElement: <ErrorPage />,
    children: [
      ...NAVIGATION.map((section) => ({
        path: section.path,
        element: PAGES[section.path] ?? <ComingSoonPage section={section} />,
      })),
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
