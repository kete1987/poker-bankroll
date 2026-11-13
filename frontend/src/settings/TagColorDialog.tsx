import {
  Alert,
  Button,
  ColorSwatch,
  Group,
  Modal,
  Radio,
  SimpleGrid,
  ThemeIcon,
  Stack,
  Text,
  useMantineTheme,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCheck } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useSetTagColor } from '../api/tags';
import type { Tag, TagColor } from '../api/types';
import { useSubmit } from '../components/useSubmit';
import { TagBadge } from '../games/TagBadge';

const COLORS = [
  'RED',
  'PINK',
  'GRAPE',
  'VIOLET',
  'INDIGO',
  'BLUE',
  'CYAN',
  'TEAL',
  'GREEN',
  'LIME',
  'YELLOW',
  'ORANGE',
] as const satisfies readonly TagColor[];

export function TagColorDialog({ tag, onClose }: { tag: Tag; onClose: () => void }) {
  const { t } = useTranslation();
  const theme = useMantineTheme();
  const save = useSubmit();
  const setColor = useSetTagColor();
  const [color, updateColor] = useState<TagColor | null>(tag.color ?? null);
  return (
    <Modal
      opened
      onClose={onClose}
      title={t('settings.tags.color.title')}
      size="sm"
      closeButtonProps={{ 'aria-label': t('actions.close') }}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void save.run(async () => {
            await setColor.mutateAsync({ id: tag.id, color });
            notifications.show({
              color: 'teal',
              title: t('settings.tags.color.saved'),
              message: tag.name,
            });
            onClose();
          });
        }}
      >
        <Stack gap="md">
          {save.failure && <Alert color="red">{save.failure}</Alert>}
          <Radio.Group
            label={t('settings.tags.color.label')}
            value={color ?? 'DEFAULT'}
            onChange={(value) => updateColor(value === 'DEFAULT' ? null : (value as TagColor))}
          >
            <SimpleGrid cols={5} spacing={8} mt="xs" w={252}>
              {(['DEFAULT', ...COLORS] as const).map((value) => {
                const label = t(`tagColors.${value}`);
                const selected = (color ?? 'DEFAULT') === value;
                return (
                  <Radio.Card
                    key={value}
                    value={value}
                    aria-label={label}
                    title={label}
                    radius="xl"
                    w={44}
                    h={44}
                    style={{
                      display: 'grid',
                      placeItems: 'center',
                      borderWidth: 2,
                      borderColor: selected ? 'var(--mantine-primary-color-filled)' : 'transparent',
                    }}
                  >
                    <ColorSwatch
                      size={34}
                      color={
                        theme.colors[value === 'DEFAULT' ? 'gray' : value.toLowerCase()]?.[6] ??
                        theme.colors.gray[6]
                      }
                    >
                      {selected && (
                        <ThemeIcon size={22} radius="xl" color="dark" c="white">
                          <IconCheck size={16} stroke={3} aria-hidden />
                        </ThemeIcon>
                      )}
                    </ColorSwatch>
                  </Radio.Card>
                );
              })}
            </SimpleGrid>
            <Text size="sm" c="dimmed" mt="xs">
              {t(`tagColors.${color ?? 'DEFAULT'}`)}
            </Text>
          </Radio.Group>
          <div aria-label={t('settings.tags.color.preview')}>
            <TagBadge tag={{ name: tag.name, color }} size="lg" />
          </div>
          <Group justify="flex-end" gap="sm">
            <Button variant="subtle" color="gray" onClick={onClose} disabled={save.busy}>
              {t('actions.cancel')}
            </Button>
            <Button type="submit" loading={save.busy}>
              {t('gameForm.save')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
