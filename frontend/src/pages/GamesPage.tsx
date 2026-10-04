import { Alert, Button, Group, Loader, Modal, Pagination, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPlaylistAdd, IconPlus } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { exportGames } from '../api/exports';
import {
  useAddRebuy,
  useAddReEntry,
  useCreateGame,
  useCreateGames,
  useDeleteGame,
  useFinishGame,
  useGames,
  useGamesInPlay,
  useUpdateGame,
} from '../api/games';
import { useRooms } from '../api/rooms';
import { useTemplates } from '../api/templates';
import type { Game } from '../api/types';
import { useVariants } from '../api/variants';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ExportMenu } from '../components/ExportMenu';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { Page } from '../components/Page';
import { useFormat } from '../format/useFormat';
import { BulkAddForm } from '../games/BulkAddForm';
import { FinishGameDialog } from '../games/FinishGameDialog';
import { describeGame } from '../games/labels';
import { GameCards } from '../games/GameCards';
import { GameFilters } from '../games/GameFilters';
import { GameForm } from '../games/GameForm';
import { GamesInPlay } from '../games/GamesInPlay';
import { GamesTable } from '../games/GamesTable';
import { QuickStart } from '../games/QuickStart';
import { RebuyDialog } from '../games/RebuyDialog';
import { SaveTemplateDialog } from '../games/SaveTemplateDialog';
import type { GameStart } from '../games/templates';
import { toGameQuery, useGameFilters } from '../games/useGameFilters';

/** What is open on top of the page, and for which game. */
type Dialog =
  /** A new game; filled from a game (a duplicate) or a template when `copyOf` is given. */
  | { kind: 'add'; copyOf?: GameStart; duplicate?: boolean }
  | { kind: 'addSeveral' }
  | { kind: 'edit' | 'delete' | 'finish' | 'reEntry' | 'rebuy' | 'saveTemplate'; game: Game };

/**
 * Games: the templates to start one in a click and the ones in play on top, then every finished
 * one with filters, order and pages.
 */
export function GamesPage() {
  const { t } = useTranslation();
  const format = useFormat();
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const close = () => setDialog(null);

  const narrow = useNarrowScreen();
  const { filters, update, clear, hasFilters } = useGameFilters();
  const rooms = useRooms();
  const variants = useVariants();
  const templates = useTemplates();
  const usableTemplates = templates.data?.filter((template) => template.usable) ?? [];
  const inPlay = useGamesInPlay();
  const gameQuery = toGameQuery(filters);
  const games = useGames(gameQuery);

  const createGame = useCreateGame();
  const createGames = useCreateGames();
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

  function onSavedSeveral(saved: Game[]) {
    const count = saved.length;
    const title = t('bulkAdd.done', { count, formatted: format.number(count) });
    // The games of a batch share their room, so their currency.
    const currencyCode = saved[0]?.currencyCode;
    if (currencyCode === undefined || saved.every((one) => one.status === 'IN_PLAY')) {
      notify(title, t('bulkAdd.inPlay'));
    } else {
      const netCents = saved.reduce((sum, one) => sum + Math.round(one.net * 100), 0);
      notify(
        title,
        t('games.saved.finished', { net: format.signedMoney(netCents / 100, currencyCode) }),
      );
    }
    close();
  }

  const duplicate = (game: Game) => setDialog({ kind: 'add', copyOf: game, duplicate: true });
  const saveAsTemplate = (game: Game) => setDialog({ kind: 'saveTemplate', game });

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

  const GamesList = narrow ? GameCards : GamesTable;

  return (
    <Page title={t('nav.games')}>
      <Group justify="space-between" gap="xs">
        <Group gap="xs">
          <Button leftSection={<IconPlus size={16} />} onClick={() => setDialog({ kind: 'add' })}>
            {t('games.add')}
          </Button>
          <Button
            variant="default"
            leftSection={<IconPlaylistAdd size={16} />}
            onClick={() => setDialog({ kind: 'addSeveral' })}
          >
            {t('games.addSeveral')}
          </Button>
        </Group>
        {/* Every game the filters of the table select, whatever its page and order. */}
        <ExportMenu
          label={t('export.games.label')}
          note={t('export.games.onlyFinished')}
          fallbackName="poker-bankroll-games"
          doneMessage={(file) => t('export.games.done', { file })}
          onExport={(format) =>
            exportGames(format, {
              from: gameQuery.from,
              to: gameQuery.to,
              gameType: gameQuery.gameType,
              roomId: gameQuery.roomId,
              variantId: gameQuery.variantId,
              q: gameQuery.q,
            })
          }
        />
      </Group>

      {usableTemplates.length > 0 && (
        <QuickStart
          templates={usableTemplates}
          onCustomize={(template) => setDialog({ kind: 'add', copyOf: template })}
        />
      )}

      {inPlay.data && inPlay.data.items.length > 0 && (
        <GamesInPlay
          games={inPlay.data.items}
          total={inPlay.data.totalItems}
          onFinish={(game) => setDialog({ kind: 'finish', game })}
          onReEntry={(game) => setDialog({ kind: 'reEntry', game })}
          onRebuy={(game) => setDialog({ kind: 'rebuy', game })}
          onEdit={(game) => setDialog({ kind: 'edit', game })}
          onDuplicate={duplicate}
          onSaveAsTemplate={saveAsTemplate}
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
          {/* A table has too many columns for a phone: there each game is a card. */}
          <GamesList
            games={games.data.items}
            sortField={filters.sortField}
            sortDescending={filters.sortDescending}
            onSort={(sortField, sortDescending) => update({ sortField, sortDescending })}
            onEdit={(game) => setDialog({ kind: 'edit', game })}
            onDuplicate={duplicate}
            onSaveAsTemplate={saveAsTemplate}
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
          title={
            dialog.kind === 'edit'
              ? t('games.edit')
              : dialog.kind === 'add' && dialog.duplicate
                ? t('games.duplicate')
                : t('games.add')
          }
          size="lg"
          fullScreen={narrow}
          closeButtonProps={{ 'aria-label': t('actions.close') }}
        >
          {loadFailed ? (
            <Alert color="red">{t('games.loadError')}</Alert>
          ) : formReady ? (
            dialog.kind === 'add' ? (
              <GameForm
                rooms={rooms.data}
                variants={variants.data}
                copyOf={dialog.copyOf}
                templates={templates.data}
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

      {dialog?.kind === 'addSeveral' && (
        <Modal
          opened
          onClose={close}
          title={t('bulkAdd.title')}
          size="xl"
          fullScreen={narrow}
          closeButtonProps={{ 'aria-label': t('actions.close') }}
        >
          {loadFailed ? (
            <Alert color="red">{t('games.loadError')}</Alert>
          ) : formReady ? (
            <BulkAddForm
              rooms={rooms.data}
              variants={variants.data}
              templates={templates.data}
              onSave={async (games) => (await createGames.mutateAsync({ games })).games}
              onSaved={onSavedSeveral}
              onCancel={close}
            />
          ) : (
            <Group justify="center" py="xl">
              <Loader />
            </Group>
          )}
        </Modal>
      )}

      {dialog?.kind === 'saveTemplate' && <SaveTemplateDialog game={dialog.game} onClose={close} />}

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
