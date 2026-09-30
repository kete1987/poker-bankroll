import { MantineProvider } from '@mantine/core';
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
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </MantineProvider>
  );
}
