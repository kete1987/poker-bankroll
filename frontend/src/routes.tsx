import type { RouteObject } from 'react-router';

import { NAVIGATION } from './layout/navigation';
import { RootLayout } from './layout/RootLayout';
import { ComingSoonPage } from './pages/ComingSoonPage';
import { ErrorPage } from './pages/ErrorPage';
import { NotFoundPage } from './pages/NotFoundPage';

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <RootLayout />,
    errorElement: <ErrorPage />,
    children: [
      // Every section starts as a placeholder; each one is replaced by its page as it is built.
      ...NAVIGATION.map((section) => ({
        path: section.path,
        element: <ComingSoonPage section={section} />,
      })),
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
