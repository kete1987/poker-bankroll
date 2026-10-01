import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import './i18n';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';

import { createQueryClient } from './api/queryClient';
import { AppProviders } from './AppProviders';
import { routes } from './routes';

const router = createBrowserRouter(routes);
const queryClient = createQueryClient();

const root = document.getElementById('root');
if (!root) {
  throw new Error('Missing #root element in index.html');
}

createRoot(root).render(
  <StrictMode>
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
);
