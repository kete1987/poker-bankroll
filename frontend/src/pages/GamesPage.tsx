import { Alert, Badge, Button, Group, Loader, Modal, Table, Text, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { IconPlus } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

import { useCreateGame, useRecentGames } from '../api/games';
import { useRooms } from '../api/rooms';
import type { Game } from '../api/types';
import { useVariants } from '../api/variants';
import { Page } from '../components/Page';
import { useFormat } from '../format/useFormat';
import { GameForm } from '../games/GameForm';
import { variantLabel } from '../games/labels';

const RECENT_GAMES = 10;

/** Games: for now, recording one and the last ones added (the full list comes with UI-3). */
export function GamesPage() {
  const { t } = useTranslation();
  const format = useFormat();
  const [formOpened, form] = useDisclosure(false);
  const rooms = useRooms();
  const variants = useVariants();
  const recent = useRecentGames(RECENT_GAMES);
  const createGame = useCreateGame();

  function onSaved(game: Game, addAnother: boolean) {
    notifications.show({
      color: 'teal',
      title: t('games.saved.title'),
      message:
        game.status === 'IN_PLAY'
          ? t('games.saved.inPlay')
          : t('games.saved.finished', { net: format.signedMoney(game.net, game.currencyCode) }),
    });
    if (!addAnother) {
      form.close();
    }
  }

  return (
    <Page title={t('nav.games')}>
      <Group>
        <Button leftSection={<IconPlus size={16} />} onClick={form.open}>
          {t('games.add')}
        </Button>
      </Group>

      <Modal
        opened={formOpened}
        onClose={form.close}
        title={t('games.add')}
        size="lg"
        closeButtonProps={{ 'aria-label': t('gameForm.cancel') }}
      >
        {rooms.isError || variants.isError ? (
          <Alert color="red">{t('games.loadError')}</Alert>
        ) : rooms.data && variants.data ? (
          <GameForm
            rooms={rooms.data}
            variants={variants.data}
            onSave={(game) => createGame.mutateAsync(game)}
            onSaved={onSaved}
            onCancel={form.close}
          />
        ) : (
          <Group justify="center" py="xl">
            <Loader />
          </Group>
        )}
      </Modal>

      <Title order={3} size="h4">
        {t('games.recent.title')}
      </Title>
      {recent.isError ? (
        <Alert color="red">{t('games.loadError')}</Alert>
      ) : recent.isPending ? (
        <Loader />
      ) : recent.data.items.length === 0 ? (
        <Text c="dimmed">{t('games.recent.empty')}</Text>
      ) : (
        <Table.ScrollContainer minWidth={560}>
          <Table verticalSpacing="xs" highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t('games.columns.date')}</Table.Th>
                <Table.Th>{t('games.columns.game')}</Table.Th>
                <Table.Th>{t('games.columns.room')}</Table.Th>
                <Table.Th ta="right">{t('games.columns.buyIn')}</Table.Th>
                <Table.Th ta="right">{t('games.columns.net')}</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {recent.data.items.map((game) => (
                <Table.Tr key={game.id}>
                  <Table.Td style={{ whiteSpace: 'nowrap' }}>{format.date(game.playedOn)}</Table.Td>
                  <Table.Td>
                    <Text size="sm">{game.name ?? t(`gameTypes.${game.gameType}`)}</Text>
                    <Text size="xs" c="dimmed">
                      {[
                        game.name ? t(`gameTypes.${game.gameType}`) : null,
                        game.variant ? variantLabel(t, game.variant) : null,
                        game.modality === 'NLHE' ? null : t(`modalities.${game.modality}`),
                      ]
                        .filter(Boolean)
                        .join(' · ')}
                    </Text>
                  </Table.Td>
                  <Table.Td>{game.room.name}</Table.Td>
                  <Table.Td ta="right" style={{ whiteSpace: 'nowrap' }}>
                    {format.money(game.buyIn, game.currencyCode)}
                    {game.entries > 1 && (
                      <Text span size="xs" c="dimmed">
                        {' '}
                        ×{game.entries}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td ta="right" style={{ whiteSpace: 'nowrap' }}>
                    {game.status === 'IN_PLAY' ? (
                      <Badge variant="light" color="blue">
                        {t('gameStatus.IN_PLAY')}
                      </Badge>
                    ) : (
                      <Text
                        span
                        size="sm"
                        fw={500}
                        c={game.net > 0 ? 'teal' : game.net < 0 ? 'red' : undefined}
                      >
                        {format.signedMoney(game.net, game.currencyCode)}
                      </Text>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
    </Page>
  );
}
