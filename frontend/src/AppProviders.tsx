import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';

import { colorSchemeManager, theme } from './theme';

interface AppProvidersProps {
  queryClient: QueryClient;
  children: ReactNode;
}

/** Everything the UI needs around the router; shared by the app and the tests. */
export function AppProviders({ queryClient, children }: AppProvidersProps) {
  return (
    <MantineProvider
      theme={theme}
      colorSchemeManager={colorSchemeManager}
      defaultColorScheme="auto"
    >
      <Notifications position="top-right" />
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </MantineProvider>
  );
}
