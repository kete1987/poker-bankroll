import { Button, Group, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconChevronDown, IconPlayerPlay } from '@tabler/icons-react';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../api/client';
import { useCreateGame } from '../api/games';
import type { GameTemplate } from '../api/types';
import { IconButton } from '../components/IconButton';
import { useFormat } from '../format/useFormat';
import { gameOfTemplate, templateLabel } from './templates';

interface QuickStartProps {
  /** The templates that can start games now (usable ones). */
  templates: GameTemplate[];
  /** Opens the form of a new game filled from the template, to change something first. */
  onCustomize: (template: GameTemplate) => void;
}

/**
 * One button per template: a click records the game in play, today, with no form. The small part
 * next to it opens the form filled from the template instead, to change something before saving.
 */
export function QuickStart({ templates, onCustomize }: QuickStartProps) {
  const { t } = useTranslation();
  const format = useFormat();
  const createGame = useCreateGame();
  // Templates whose game is being recorded: a second click on one of them does nothing. A ref
  // too, as a double click arrives before the state of the first is rendered.
  const starting = useRef(new Set<number>());
  const [pending, setPending] = useState<ReadonlySet<number>>(new Set());

  function setStarting(id: number, running: boolean) {
    if (running) {
      starting.current.add(id);
    } else {
      starting.current.delete(id);
    }
    setPending(new Set(starting.current));
  }

  async function start(template: GameTemplate, label: string) {
    if (starting.current.has(template.id)) {
      return;
    }
    setStarting(template.id, true);
    try {
      await createGame.mutateAsync(gameOfTemplate(template));
      notifications.show({
        color: 'teal',
        title: t('templates.quickStart.started', { template: label }),
        message: t('games.saved.inPlay'),
      });
    } catch (error) {
      notifications.show({
        color: 'red',
        title: t('templates.quickStart.notStarted', { template: label }),
        message: error instanceof ApiError ? error.message : t('errors.unexpected'),
      });
    } finally {
      setStarting(template.id, false);
    }
  }

  return (
    <Stack gap={6} component="section" aria-labelledby="quick-start-title">
      <Text size="sm" fw={500} id="quick-start-title">
        {t('templates.quickStart.title')}
      </Text>
      {/* As many per line as fit: on a phone they wrap, nothing is scrolled sideways. */}
      <Group gap="xs">
        {templates.map((template) => {
          const label = templateLabel(t, format, template);
          return (
            <Group key={template.id} gap={0} wrap="nowrap" maw="100%">
              <Button
                variant="light"
                leftSection={<IconPlayerPlay size={14} />}
                loading={pending.has(template.id)}
                aria-label={t('templates.quickStart.start', { template: label })}
                onClick={() => void start(template, label)}
                styles={{
                  root: { borderTopRightRadius: 0, borderBottomRightRadius: 0, minWidth: 0 },
                  label: { overflow: 'hidden', textOverflow: 'ellipsis' },
                }}
              >
                {label}
              </Button>
              <IconButton
                variant="light"
                size={36}
                label={t('templates.quickStart.customize', { template: label })}
                onClick={() => onCustomize(template)}
                style={{
                  borderTopLeftRadius: 0,
                  borderBottomLeftRadius: 0,
                  borderLeft: '1px solid var(--mantine-color-body)',
                  flexShrink: 0,
                }}
              >
                <IconChevronDown size={16} />
              </IconButton>
            </Group>
          );
        })}
      </Group>
    </Stack>
  );
}
