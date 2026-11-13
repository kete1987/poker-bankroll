import { Badge, type BadgeProps } from '@mantine/core';
import type { AriaRole } from 'react';

import type { TagRef } from '../api/types';

/** The same tag color in game tables, mobile cards, in-play games and settings. */
export function TagBadge({
  tag,
  role,
  size = 'xs',
}: {
  tag: Pick<TagRef, 'name' | 'color'>;
  role?: AriaRole;
  size?: BadgeProps['size'];
}) {
  return (
    <Badge
      role={role}
      size={size}
      variant="light"
      color={tag.color?.toLowerCase() ?? 'gray'}
      tt="none"
      maw="100%"
      h="auto"
      mih={18}
      py={2}
      styles={{ label: { whiteSpace: 'normal', overflowWrap: 'anywhere' } }}
    >
      {tag.name}
    </Badge>
  );
}
