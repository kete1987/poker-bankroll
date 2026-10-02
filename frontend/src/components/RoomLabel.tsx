import { Avatar, Group, Text } from '@mantine/core';

import { roomLogoUrl, useRooms } from '../api/rooms';
import { useNarrowScreen } from './useNarrowScreen';

interface RoomLabelProps {
  /** The room as any response names it: its id and name are enough. */
  room: { id: number; name: string };
}

/**
 * A room wherever it is listed: its logo and its name. A room without logo (or whose logo fails
 * to load) shows its initial instead, in a colour derived from its name.
 */
export function RoomLabel({ room }: RoomLabelProps) {
  // Whether a room has a logo comes with the list of rooms, which every screen shares.
  const rooms = useRooms();
  // On a phone a long name takes two lines instead of pushing the figures next to it away.
  const narrow = useNarrowScreen();
  const logoVersion = rooms.data?.find((candidate) => candidate.id === room.id)?.logoVersion;

  return (
    <Group gap="xs" wrap="nowrap">
      <Avatar
        size="sm"
        radius="sm"
        name={room.name}
        color="initials"
        src={logoVersion ? roomLogoUrl(room.id, logoVersion) : null}
        // The name is right next to it: the image adds nothing for a screen reader.
        alt=""
        imageProps={{ loading: 'lazy' }}
        // A logo is shown whole, not cropped to the square: many are wider than tall.
        styles={{ image: { objectFit: 'contain' } }}
      >
        {room.name.trim().charAt(0).toUpperCase()}
      </Avatar>
      <Text span size="sm" style={{ whiteSpace: narrow ? 'normal' : 'nowrap' }}>
        {room.name}
      </Text>
    </Group>
  );
}
