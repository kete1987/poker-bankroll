import { createTheme, localStorageColorSchemeManager } from '@mantine/core';

export const theme = createTheme({
  primaryColor: 'teal',
  defaultRadius: 'md',
});

/** Light/dark choice: follows the OS until the user picks one, then it is remembered. */
export const colorSchemeManager = localStorageColorSchemeManager({
  key: 'poker-bankroll.color-scheme',
});
