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
import { exportMovements } from '../api/exports';
import { useRooms } from '../api/rooms';
import type { BankrollFigures, Movement, MovementType } from '../api/types';
import { BankrollEvolutionChart } from '../bankroll/BankrollEvolutionChart';
import { MovementCards } from '../bankroll/MovementCards';
import { MovementForm } from '../bankroll/MovementForm';
import { MovementsTable } from '../bankroll/MovementsTable';
import { RoomsTable } from '../bankroll/RoomsTable';
import { roomsTableData } from '../bankroll/roomsTableData';
import {
  MOVEMENT_TYPES,
  MOVEMENTS_PAGE_SIZE,
  useBankrollFilters,
} from '../bankroll/useBankrollFilters';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { FilterBar } from '../components/FilterBar';
import { ExportMenu } from '../components/ExportMenu';
import { Page } from '../components/Page';
import { isSingleDay } from '../components/period';
import { PeriodFilter } from '../components/PeriodFilter';
import { StatCard } from '../components/StatCard';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { CurrencyAmounts } from '../currency/CurrencyAmounts';
import { MissingRatesAlert } from '../currency/MissingRatesAlert';
import { bankrollIn, moneyView } from '../currency/view';
import { useFormat } from '../format/useFormat';
import { todayIso } from '../games/gameDefaults';

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
 * The poker bankroll: what was put in and taken out, what was won or lost and what is left, room
 * by room, and the movements behind it. Without a period it is the bankroll as it is now; with
 * one, the figures of that period. Rooms in a single currency are shown in it; in several, the
 * totals are converted to the base currency by the backend and each room stays in its own.
 */
export function BankrollPage() {
  const { t } = useTranslation();
  const format = useFormat();
  const narrow = useNarrowScreen();
  const { filters, update } = useBankrollFilters();
  const { range, roomIds } = filters;
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const close = () => setDialog(null);

  const rooms = useRooms();
  const catalog = useCatalog();
  const summary = useBankrollSummary({ from: range.from, to: range.to, roomId: roomIds });

  // One currency is shown as it is; several, converted to the base currency.
  const view =
    summary.data &&
    moneyView(
      summary.data.currencies.map((currency) => currency.currencyCode),
      summary.data.converted.currencyCode,
    );
  const currencyCode = view?.currencyCode;

  // What the list of movements shows, and what its export holds: each in its own currency.
  const movementFilters = {
    from: range.from,
    to: range.to,
    type: filters.type,
    roomId: roomIds,
  };
  const movements = useMovements({
    ...movementFilters,
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
  const failed = [rooms, catalog, summary, movements].some((query) => query.isError);
  const ofView = summary.data && view ? bankrollIn(summary.data, view) : undefined;
  const total = ofView?.total ?? NOTHING;
  /** What a converted figure is made of, currency by currency. */
  const perCurrency = (amountOf: (figures: BankrollFigures) => number, signed = false) =>
    view?.converted &&
    summary.data && (
      <CurrencyAmounts
        signed={signed}
        amounts={summary.data.currencies.map((currency) => ({
          currencyCode: currency.currencyCode,
          amount: amountOf(currency.total),
        }))}
      />
    );
  const balanceRatesOn = summary.data?.converted.balanceRatesOn;
  const money = (amount: number) => format.money(amount, currencyCode ?? 'EUR');
  const signed = (amount: number) => format.signedMoney(amount, currencyCode ?? 'EUR');

  const MovementsList = narrow ? MovementCards : MovementsTable;

  return (
    <Page title={t('nav.bankroll')}>
      <FilterBar
        primary={<PeriodFilter range={range} onChange={(next) => update({ range: next })} />}
        activeCount={roomIds.length > 0 ? 1 : 0}
      >
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
      </FilterBar>

      {failed ? (
        <Alert color="red">{t('games.loadError')}</Alert>
      ) : !summary.data ? (
        <Loader />
      ) : (
        <>
          {view?.converted && <MissingRatesAlert missing={summary.data.converted.missingRates} />}
          <SimpleGrid cols={{ base: 1, xs: 3 }}>
            <StatCard
              label={hasPeriod ? t('bankroll.cards.change') : t('bankroll.cards.bankroll')}
              value={hasPeriod ? signed(total.bankroll) : money(total.bankroll)}
              tone={hasPeriod ? toneOf(total.bankroll) : undefined}
            >
              {total.adjustments !== 0 &&
                t('bankroll.cards.adjustments', { amount: signed(total.adjustments) })}
              {perCurrency((figures) => figures.bankroll, hasPeriod)}
            </StatCard>
            <StatCard label={t('bankroll.cards.deposited')} value={money(total.deposited)}>
              {t('bankroll.cards.withdrawn', { amount: money(total.withdrawn) })}
              {perCurrency((figures) => figures.deposited)}
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
              {perCurrency((figures) => figures.result, true)}
            </StatCard>
          </SimpleGrid>
          {view?.converted && (
            // Converted, a balance and the flows behind it are valued at different rates.
            <Text size="xs" c="dimmed">
              {balanceRatesOn
                ? t('bankroll.conversion.balance', {
                    currency: view.currencyCode,
                    rates:
                      balanceRatesOn === todayIso()
                        ? t('bankroll.conversion.today')
                        : t('bankroll.conversion.ofDay', { date: format.date(balanceRatesOn) }),
                  })
                : t('bankroll.conversion.period', { currency: view.currencyCode })}
            </Text>
          )}

          {/* Over a single day the bankroll only goes from where it starts to where it ends,
              which the cards already say. */}
          {view && !isSingleDay(range) && (
            <BankrollEvolutionChart
              range={range}
              roomIds={roomIds}
              view={view}
              shownMissing={summary.data.converted.missingRates}
              granularity={filters.granularity}
              onGranularityChange={(granularity) => update({ granularity })}
            />
          )}

          {view && (
            <Stack gap="xs">
              <Title order={3} size="h4">
                {t('bankroll.rooms')}
              </Title>
              <RoomsTable
                data={roomsTableData(summary.data, view)}
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
            <ExportMenu
              label={t('export.movements.label')}
              note={t('export.movements.note')}
              fallbackName="poker-bankroll-movements"
              doneMessage={(file) => t('export.movements.done', { file })}
              onExport={(format) => exportMovements(format, movementFilters)}
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
            {/* A table has too many columns for a phone: there each movement is a card. */}
            <MovementsList
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
          fullScreen={narrow}
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
