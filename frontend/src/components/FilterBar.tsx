import { Badge, Button, Collapse, Group, Stack } from '@mantine/core';
import { IconAdjustmentsHorizontal } from '@tabler/icons-react';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { useNarrowScreen } from './useNarrowScreen';

interface FilterBarProps {
  /** Filters that are always in sight (the period). */
  primary?: ReactNode;
  /** How many of the other filters have a value, shown on the button that unfolds them. */
  activeCount: number;
  /** The other filters. */
  children: ReactNode;
}

/**
 * The filters of a screen. Side by side on a wide window; on a phone only the primary ones are
 * in sight and the rest unfold under a button, so they do not push the content off the screen.
 */
export function FilterBar({ primary, activeCount, children }: FilterBarProps) {
  const { t } = useTranslation();
  const narrow = useNarrowScreen();
  const [opened, setOpened] = useState(false);

  if (!narrow) {
    return (
      <Group gap="sm" align="flex-end">
        {primary}
        {children}
      </Group>
    );
  }
  return (
    <Stack gap="xs">
      <Group gap="sm" align="flex-end" wrap="nowrap">
        {primary}
        <Button
          variant={activeCount > 0 ? 'light' : 'default'}
          leftSection={<IconAdjustmentsHorizontal size={16} />}
          rightSection={
            activeCount > 0 ? (
              <Badge size="sm" circle>
                {activeCount}
              </Badge>
            ) : undefined
          }
          aria-expanded={opened}
          onClick={() => setOpened((now) => !now)}
        >
          {t('filters.more')}
        </Button>
      </Group>
      <Collapse expanded={opened}>
        {/* Stretched: each filter takes the whole width instead of its width in a row. */}
        <Stack gap="xs" align="stretch">
          {children}
        </Stack>
      </Collapse>
    </Stack>
  );
}
