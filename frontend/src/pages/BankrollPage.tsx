import {
  Alert,
  Button,
  Group,
  Loader,
  Modal,
  MultiSelect,
  Pagination,
  Select,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPlus } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import {
  useBankrollSummary,
  useCreateMovement,
  useDeleteMovement,
  useMovements,
  useUpdateMovement,
} from '../api/bankroll';
import { useCatalog } from '../api/catalog';
import { useRooms } from '../api/rooms';
import type { BankrollFigures, Movement, MovementType } from '../api/types';
import { MovementForm } from '../bankroll/MovementForm';
import { MovementsTable } from '../bankroll/MovementsTable';
import { RoomsTable } from '../bankroll/RoomsTable';
import {
  MOVEMENT_TYPES,
  MOVEMENTS_PAGE_SIZE,
  useBankrollFilters,
} from '../bankroll/useBankrollFilters';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Page } from '../components/Page';
import { PeriodFilter } from '../components/PeriodFilter';
import { StatCard } from '../components/StatCard';
import { useFormat } from '../format/useFormat';

/** Figures of a currency without anything in the period. */
const NOTHING: BankrollFigures = {
  deposited: 0,
  withdrawn: 0,
  bonuses: 0,
  adjustments: 0,
  gamesNet: 0,
  result: 0,
  bankroll: 0,
  ticketsWon: 0,
  gamesInPlay: 0,
  investedInPlay: 0,
};

type Dialog = { kind: 'add' } | { kind: 'edit' | 'delete'; movement: Movement };

function toneOf(amount: number): 'positive' | 'negative' | undefined {
  return amount > 0 ? 'positive' : amount < 0 ? 'negative' : undefined;
}

/**
 * The poker bankroll, for one currency: what was put in and taken out, what was won or lost and
 * what is left, room by room, and the movements behind it. Without a period it is the bankroll
 * as it is now; with one, the figures of that period.
 */
export function BankrollPage() {
  const { t } = useTranslation();
  const format = useFormat();
  const { filters, update } = useBankrollFilters();
  const { range, roomIds } = filters;
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const close = () => setDialog(null);

  const rooms = useRooms();
  const catalog = useCatalog();
  // The currencies there are do not depend on the period.
  const everything = useBankrollSummary({ roomId: roomIds });
  const summary = useBankrollSummary({ from: range.from, to: range.to, roomId: roomIds });

  const currencies = (everything.data?.currencies ?? []).map((currency) => currency.currencyCode);
  const currencyCode =
    filters.currency && currencies.includes(filters.currency) ? filters.currency : currencies[0];

  const movements = useMovements({
    from: range.from,
    to: range.to,
    type: filters.type,
    roomId: roomIds,
    currency: currencyCode,
    page: filters.page,
    size: MOVEMENTS_PAGE_SIZE,
  });
  const createMovement = useCreateMovement();
  const updateMovement = useUpdateMovement();
  const deleteMovement = useDeleteMovement();

  // A page past the end (its last movement was deleted, an old link): go to the last one.
  const pageCount = movements.data?.totalPages ?? 0;
  const pastTheEnd =
    movements.data !== undefined &&
    !movements.isPlaceholderData &&
    movements.data.items.length === 0 &&
    filters.page > 0;
  useEffect(() => {
    if (pastTheEnd) {
      update({ page: Math.max(pageCount - 1, 0) });
    }
  }, [pastTheEnd, pageCount, update]);

  const hasPeriod = Boolean(range.from || range.to);
  const failed = [rooms, catalog, everything, summary, movements].some((query) => query.isError);
  const ofCurrency = summary.data?.currencies.find(
    (currency) => currency.currencyCode === currencyCode,
  );
  const total = ofCurrency?.total ?? NOTHING;
  const money = (amount: number) => format.money(amount, currencyCode ?? 'EUR');
  const signed = (amount: number) => format.signedMoney(amount, currencyCode ?? 'EUR');

  return (
    <Page title={t('nav.bankroll')}>
      <Group gap="sm" align="flex-end">
        <PeriodFilter range={range} onChange={(next) => update({ range: next })} />
        <MultiSelect
          label={t('filters.room')}
          miw={180}
          maw={360}
          clearable
          placeholder={roomIds.length === 0 ? t('filters.any') : undefined}
          data={(rooms.data ?? []).map((room) => ({ value: String(room.id), label: room.name }))}
          value={roomIds.map(String)}
          onChange={(values) => update({ roomIds: values.map(Number) })}
        />
        {currencies.length > 1 && currencyCode && (
          <Select
            label={t('dashboard.currency')}
            w={110}
            allowDeselect={false}
            data={currencies}
            value={currencyCode}
            onChange={(value) => update({ currency: value ?? undefined })}
          />
        )}
      </Group>

      {failed ? (
        <Alert color="red">{t('games.loadError')}</Alert>
      ) : !summary.data || !everything.data ? (
        <Loader />
      ) : (
        <>
          <SimpleGrid cols={{ base: 1, xs: 3 }}>
            <StatCard
              label={hasPeriod ? t('bankroll.cards.change') : t('bankroll.cards.bankroll')}
              value={hasPeriod ? signed(total.bankroll) : money(total.bankroll)}
              tone={hasPeriod ? toneOf(total.bankroll) : undefined}
            >
              {total.adjustments !== 0 &&
                t('bankroll.cards.adjustments', { amount: signed(total.adjustments) })}
            </StatCard>
            <StatCard label={t('bankroll.cards.deposited')} value={money(total.deposited)}>
              {t('bankroll.cards.withdrawn', { amount: money(total.withdrawn) })}
            </StatCard>
            <StatCard
              label={t('bankroll.cards.result')}
              value={signed(total.result)}
              tone={toneOf(total.result)}
            >
              {t('bankroll.cards.resultDetail', {
                games: signed(total.gamesNet),
                bonuses: money(total.bonuses),
              })}
            </StatCard>
          </SimpleGrid>

          {ofCurrency && (
            <Stack gap="xs">
              <Title order={3} size="h4">
                {t('bankroll.rooms')}
              </Title>
              <RoomsTable
                bankroll={ofCurrency}
                bankrollLabel={
                  hasPeriod ? t('bankroll.columns.change') : t('bankroll.columns.bankroll')
                }
              />
            </Stack>
          )}
        </>
      )}

      <Stack gap="xs">
        <Group justify="space-between" align="flex-end">
          <Title order={3} size="h4">
            {t('bankroll.movements')}
          </Title>
          <Group gap="sm" align="flex-end">
            <Select
              aria-label={t('bankroll.filters.type')}
              placeholder={t('bankroll.filters.anyType')}
              w={190}
              clearable
              data={MOVEMENT_TYPES.map((type) => ({
                value: type,
                label: t(`movementTypes.${type}`),
              }))}
              value={filters.type ?? null}
              onChange={(value) => update({ type: (value as MovementType | null) ?? undefined })}
            />
            <Button leftSection={<IconPlus size={16} />} onClick={() => setDialog({ kind: 'add' })}>
              {t('bankroll.add')}
            </Button>
          </Group>
        </Group>
        {failed ? null : !movements.data || pastTheEnd ? (
          <Loader />
        ) : movements.data.items.length === 0 ? (
          <Text c="dimmed">{t('bankroll.noMovements')}</Text>
        ) : (
          <Stack gap="sm" style={{ opacity: movements.isPlaceholderData ? 0.6 : 1 }}>
            <MovementsTable
              movements={movements.data.items}
              onEdit={(movement) => setDialog({ kind: 'edit', movement })}
              onDelete={(movement) => setDialog({ kind: 'delete', movement })}
            />
            <Group justify="space-between">
              <Text size="sm" c="dimmed">
                {t('bankroll.total', {
                  count: movements.data.totalItems,
                  formatted: format.number(movements.data.totalItems),
                })}
              </Text>
              {pageCount > 1 && (
                <Pagination
                  total={pageCount}
                  value={filters.page + 1}
                  onChange={(page) => update({ page: page - 1 })}
                  getControlProps={(control) => ({
                    'aria-label': t(`games.list.pages.${control}`),
                  })}
                  getItemProps={(page) => ({ 'aria-label': t('games.list.pages.page', { page }) })}
                />
              )}
            </Group>
          </Stack>
        )}
      </Stack>

      {(dialog?.kind === 'add' || dialog?.kind === 'edit') && (
        <Modal
          opened
          onClose={close}
          title={dialog.kind === 'add' ? t('bankroll.add') : t('bankroll.edit')}
          size="lg"
          closeButtonProps={{ 'aria-label': t('actions.close') }}
        >
          {rooms.data && catalog.data ? (
            <MovementForm
              key={dialog.kind === 'edit' ? dialog.movement.id : 'new'}
              rooms={rooms.data}
              currencies={catalog.data.currencies}
              movement={dialog.kind === 'edit' ? dialog.movement : undefined}
              defaultCurrency={currencyCode}
              onSave={(movement) =>
                dialog.kind === 'edit'
                  ? updateMovement.mutateAsync({ id: dialog.movement.id, movement })
                  : createMovement.mutateAsync(movement)
              }
              onSaved={(saved) => {
                notifications.show({
                  color: 'teal',
                  title: t('bankroll.saved'),
                  message: `${t(`movementTypes.${saved.type}`)}: ${format.signedMoney(saved.signedAmount, saved.currencyCode)}`,
                });
                close();
              }}
              onCancel={close}
            />
          ) : (
            <Group justify="center" py="xl">
              <Loader />
            </Group>
          )}
        </Modal>
      )}

      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          title={t('bankroll.delete.title')}
          confirmLabel={t('games.delete.confirmLabel')}
          destructive
          onClose={close}
          onConfirm={async () => {
            await deleteMovement.mutateAsync(dialog.movement.id);
            notifications.show({
              color: 'teal',
              title: t('bankroll.delete.done'),
              message: undefined,
            });
          }}
        >
          {t('bankroll.delete.confirm', {
            type: t(`movementTypes.${dialog.movement.type}`),
            amount: format.money(dialog.movement.amount, dialog.movement.currencyCode),
            date: format.date(dialog.movement.occurredOn),
          })}
        </ConfirmDialog>
      )}
    </Page>
  );
}
