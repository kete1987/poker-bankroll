import { em } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';

/** Below this width (the `sm` breakpoint of the theme) the app is laid out for a phone. */
const NARROW_BELOW_PX = 768;

/**
 * Whether the window is as narrow as a phone. Tables with many columns then become cards or
 * drop their secondary columns, and filters fold away: nothing should need horizontal scrolling.
 */
export function useNarrowScreen(): boolean {
  return (
    useMediaQuery(`(max-width: ${em(NARROW_BELOW_PX - 1)})`, undefined, {
      // Known on the first render: there is no server, so nothing to hydrate against.
      getInitialValueInEffect: false,
    }) ?? false
  );
}
