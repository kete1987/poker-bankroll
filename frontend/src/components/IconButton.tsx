import { ActionIcon, Tooltip, type ActionIconProps, type ElementProps } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import type { ReactNode, Ref } from 'react';

/** Long enough for the pointer to cross a table without a tooltip flashing on every row. */
const OPEN_DELAY_MS = 400;

export interface IconButtonProps
  extends ActionIconProps, ElementProps<'button', keyof ActionIconProps | 'aria-label'> {
  /** What the button does: its accessible name, and the text of its tooltip. */
  label: string;
  /** Shown in the tooltip instead of the label, e.g. why the button does nothing. */
  tooltip?: ReactNode;
  ref?: Ref<HTMLButtonElement>;
}

/**
 * A button that only shows an icon: the label given once names it for screen readers and shows
 * in a tooltip on hover and on keyboard focus. Any other prop goes to the `ActionIcon`, so it
 * also works as the target of a `Menu`.
 *
 * Where the pointer cannot hover (a phone), there is no tooltip: a tap focuses the button, and
 * the tooltip would stay over the screen until something else is tapped.
 */
export function IconButton({ label, tooltip, ref, ...others }: IconButtonProps) {
  const cannotHover =
    useMediaQuery('(hover: none)', undefined, { getInitialValueInEffect: false }) ?? false;
  return (
    <Tooltip
      label={tooltip ?? label}
      disabled={cannotHover}
      openDelay={OPEN_DELAY_MS}
      events={{ hover: true, focus: true, touch: false }}
      // Labels naming a game can be long: they wrap instead of crossing the screen.
      multiline
      maw={280}
      withArrow
    >
      <ActionIcon ref={ref} aria-label={label} {...others} />
    </Tooltip>
  );
}
