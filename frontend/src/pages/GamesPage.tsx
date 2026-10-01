import { Alert, Button, Group, Loader, Modal, Pagination, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPlus } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import {
  useAddRebuy,
  useAddReEntry,
  useCreateGame,
  useDeleteGame,
  useFinishGame,
  useGames,
  useGamesInPlay,
  useUpdateGame,
} from '../api/games';
import { useRooms } from '../api/rooms';
import type { Game } from '../api/types';
import { useVariants } from '../api/variants';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Page } from '../components/Page';
import { useFormat } from '../format/useFormat';
import { FinishGameDialog } from '../games/FinishGameDialog';
import { describeGame } from '../games/labels';
import { GameFilters } from '../games/GameFilters';
import { GameForm } from '../games/GameForm';
import { GamesInPlay } from '../games/GamesInPlay';
import { GamesTable } from '../games/GamesTable';
import { RebuyDialog } from '../games/RebuyDialog';
import { toGameQuery, useGameFilters } from '../games/useGameFilters';

/** What is open on top of the page, and for which game. */
type Dialog =
  { kind: 'add' } | { kind: 'edit' | 'delete' | 'finish' | 'reEntry' | 'rebuy'; game: Game };

/** Games: the ones in play on top, then every finished one with filters, order and pages. */
export function GamesPage() {
  const { t } = useTranslation();
  const format = useFormat();
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const close = () => setDialog(null);

  const { filters, update, clear, hasFilters } = useGameFilters();
  const rooms = useRooms();
  const variants = useVariants();
  const inPlay = useGamesInPlay();
  const games = useGames(toGameQuery(filters));

  const createGame = useCreateGame();
  const updateGame = useUpdateGame();
  const deleteGame = useDeleteGame();
  const finishGame = useFinishGame();
  const addReEntry = useAddReEntry();
  const addRebuy = useAddRebuy();

  function notify(title: string, message?: string) {
    notifications.show({ color: 'teal', title, message });
  }

  function netOf(game: Game): string {
    return t('games.saved.finished', { net: format.signedMoney(game.net, game.currencyCode) });
  }

  function onSaved(game: Game, addAnother: boolean) {
    notify(
      t('games.saved.title'),
      game.status === 'IN_PLAY' ? t('games.saved.inPlay') : netOf(game),
    );
    if (!addAnother) {
      close();
    }
  }

  const formReady = rooms.data && variants.data;
  const loadFailed = rooms.isError || variants.isError;
  const pageCount = games.data?.totalPages ?? 0;

  // A page past the end (the last game of the last page was deleted, an old link): go to the last.
  const pastTheEnd =
    games.data !== undefined &&
    !games.isPlaceholderData &&
    games.data.items.length === 0 &&
    filters.page > 0;
  useEffect(() => {
    if (pastTheEnd) {
      update({ page: Math.max(pageCount - 1, 0) });
    }
  }, [pastTheEnd, pageCount, update]);

  return (
    <Page title={t('nav.games')}>
      <Group>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setDialog({ kind: 'add' })}>
          {t('games.add')}
        </Button>
      </Group>

      {inPlay.data && inPlay.data.items.length > 0 && (
        <GamesInPlay
          games={inPlay.data.items}
          total={inPlay.data.totalItems}
          onFinish={(game) => setDialog({ kind: 'finish', game })}
          onReEntry={(game) => setDialog({ kind: 'reEntry', game })}
          onRebuy={(game) => setDialog({ kind: 'rebuy', game })}
          onEdit={(game) => setDialog({ kind: 'edit', game })}
          onDelete={(game) => setDialog({ kind: 'delete', game })}
        />
      )}

      <GameFilters
        filters={filters}
        rooms={rooms.data ?? []}
        variants={variants.data ?? []}
        hasFilters={hasFilters}
        onChange={update}
        onClear={clear}
      />

      {games.isError ? (
        <Alert color="red">{t('games.loadError')}</Alert>
      ) : games.isPending || pastTheEnd ? (
        <Loader />
      ) : games.data.items.length === 0 ? (
        <Text c="dimmed">{hasFilters ? t('games.list.noMatches') : t('games.list.empty')}</Text>
      ) : (
        <Stack gap="sm" style={{ opacity: games.isPlaceholderData ? 0.6 : 1 }}>
          <GamesTable
            games={games.data.items}
            sortField={filters.sortField}
            sortDescending={filters.sortDescending}
            onSort={(sortField, sortDescending) => update({ sortField, sortDescending })}
            onEdit={(game) => setDialog({ kind: 'edit', game })}
            onDelete={(game) => setDialog({ kind: 'delete', game })}
          />
          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {t('games.list.total', {
                count: games.data.totalItems,
                formatted: format.number(games.data.totalItems),
              })}
            </Text>
            {pageCount > 1 && (
              <Pagination
                total={pageCount}
                value={filters.page + 1}
                onChange={(page) => update({ page: page - 1 })}
                getControlProps={(control) => ({ 'aria-label': t(`games.list.pages.${control}`) })}
                getItemProps={(page) => ({ 'aria-label': t('games.list.pages.page', { page }) })}
              />
            )}
          </Group>
        </Stack>
      )}

      {(dialog?.kind === 'add' || dialog?.kind === 'edit') && (
        <Modal
          opened
          onClose={close}
          title={dialog.kind === 'add' ? t('games.add') : t('games.edit')}
          size="lg"
          closeButtonProps={{ 'aria-label': t('actions.close') }}
        >
          {loadFailed ? (
            <Alert color="red">{t('games.loadError')}</Alert>
          ) : formReady ? (
            dialog.kind === 'add' ? (
              <GameForm
                rooms={rooms.data}
                variants={variants.data}
                onSave={(game) => createGame.mutateAsync(game)}
                onSaved={onSaved}
                onCancel={close}
              />
            ) : (
              <GameForm
                key={dialog.game.id}
                rooms={rooms.data}
                variants={variants.data}
                game={dialog.game}
                onSave={(game) => updateGame.mutateAsync({ id: dialog.game.id, game })}
                onSaved={onSaved}
                onCancel={close}
              />
            )
          ) : (
            <Group justify="center" py="xl">
              <Loader />
            </Group>
          )}
        </Modal>
      )}

      {dialog?.kind === 'finish' && (
        <FinishGameDialog
          game={dialog.game}
          onClose={close}
          onFinish={async (result) => {
            const finished = await finishGame.mutateAsync({ id: dialog.game.id, result });
            notify(t('games.finish.done'), netOf(finished));
          }}
        />
      )}

      {dialog?.kind === 'rebuy' && (
        <RebuyDialog
          game={dialog.game}
          onClose={close}
          onRebuy={async (rebuy) => {
            const updated = await addRebuy.mutateAsync({ id: dialog.game.id, rebuy });
            notify(
              t('games.rebuy.done'),
              t('games.rebuy.total', { amount: format.money(updated.buyIn, updated.currencyCode) }),
            );
          }}
        />
      )}

      {dialog?.kind === 'reEntry' && (
        <ConfirmDialog
          title={t('games.reEntry.title')}
          confirmLabel={t('games.actions.reEntry')}
          onClose={close}
          onConfirm={async () => {
            const updated = await addReEntry.mutateAsync(dialog.game.id);
            notify(t('games.reEntry.done'), t('games.reEntry.total', { count: updated.entries }));
          }}
        >
          {t('games.reEntry.confirm', {
            game: describeGame(t, dialog.game),
            amount: format.money(dialog.game.buyIn, dialog.game.currencyCode),
          })}
        </ConfirmDialog>
      )}

      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title={t('games.delete.title')}
          confirmLabel={t('games.delete.confirmLabel')}
          destructive
          onClose={close}
          onConfirm={async () => {
            await deleteGame.mutateAsync(dialog.game.id);
            notify(t('games.delete.done'));
          }}
        >
          {t('games.delete.confirm', {
            game: describeGame(t, dialog.game),
            date: format.date(dialog.game.playedOn),
            room: dialog.game.room.name,
          })}
        </ConfirmDialog>
      )}
    </Page>
  );
}
