import { Avatar, Group, Text } from '@mantine/core';

interface RoomLabelProps {
  name: string;
}

/**
 * A room wherever it is listed: its logo and its name. Until rooms can have a logo (F-10) the
 * logo is a placeholder with the initial of the room, in a colour derived from its name.
 */
export function RoomLabel({ name }: RoomLabelProps) {
  return (
    <Group gap="xs" wrap="nowrap">
      <Avatar size="sm" radius="sm" name={name} color="initials" aria-hidden>
        {name.trim().charAt(0).toUpperCase()}
      </Avatar>
      <Text span size="sm" style={{ whiteSpace: 'nowrap' }}>
        {name}
      </Text>
    </Group>
  );
}
