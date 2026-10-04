import {
  Alert,
  Anchor,
  Group,
  Loader,
  MultiSelect,
  SegmentedControl,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { useBankrollSummary } from '../api/bankroll';
import { useRooms } from '../api/rooms';
import { useStatsGroups, useStatsSummary } from '../api/stats';
import type { StatsFigures } from '../api/types';
import { FilterBar } from '../components/FilterBar';
import { Page } from '../components/Page';
import { PeriodFilter } from '../components/PeriodFilter';
import { StatCard } from '../components/StatCard';
import { CurrencyAmounts } from '../currency/CurrencyAmounts';
import { MissingRatesAlert } from '../currency/MissingRatesAlert';
import { bankrollIn, groupsIn, mergeMissing, moneyView, summaryIn } from '../currency/view';
import { BankrollByRoom } from '../dashboard/BankrollByRoom';
import { ResultsTable, type ResultsRow } from '../dashboard/ResultsTable';
import { useDashboardFilters, type Breakdown } from '../dashboard/useDashboardFilters';
import { useFormat } from '../format/useFormat';
import { variantLabel } from '../games/labels';

/** Figures of a period without games. */
const NO_GAMES: StatsFigures = {
  games: 0,
  entries: 0,
  winningGames: 0,
  invested: 0,
  won: 0,
  bounties: 0,
  ticketsWon: 0,
  net: 0,
};

function toneOf(amount: number): 'positive' | 'negative' | undefined {
  return amount > 0 ? 'positive' : amount < 0 ? 'negative' : undefined;
}

/**
 * At a glance: results of the chosen period, the bankroll now, and the breakdown per game type or
 * variant and per room. Every result of the period is the net of its finished games, the same
 * figure everywhere. What is in a single currency is shown in it; when currencies are mixed,
 * everything is converted to the base currency by the backend.
 */
export function DashboardPage() {
  const { t } = useTranslation();
  const format = useFormat();
  const { filters, update } = useDashboardFilters();
  const { range, roomIds, breakdown } = filters;

  const rooms = useRooms();
  const query = { from: range.from, to: range.to, roomId: roomIds };
  const stats = useStatsSummary(query);
  const byVariant = useStatsGroups('VARIANT', query, breakdown === 'variant');
  const bankrollNow = useBankrollSummary({ roomId: roomIds });
  const byRoom = useStatsGroups('ROOM', query);

  const filterBar = () => (
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
  );

  const failed = [rooms, stats, bankrollNow, byRoom, byVariant].some((query) => query.isError);
  if (failed) {
    return (
      <Page title={t('nav.dashboard')}>
        {filterBar()}
        <Alert color="red">{t('games.loadError')}</Alert>
      </Page>
    );
  }
  if (!stats.data || !bankrollNow.data || !byRoom.data) {
    return (
      <Page title={t('nav.dashboard')}>
        {filterBar()}
        <Loader />
      </Page>
    );
  }

  // One currency is shown as it is; several, converted to the base currency.
  const view = moneyView(
    [
      ...bankrollNow.data.currencies.map((currency) => currency.currencyCode),
      ...stats.data.currencies.map((currency) => currency.currencyCode),
    ],
    stats.data.converted.currencyCode,
  );
  if (!view) {
    return (
      <Page title={t('nav.dashboard')}>
        {filterBar()}
        <Text c="dimmed">
          {t('dashboard.empty')}{' '}
          <Anchor component={Link} to="/games">
            {t('dashboard.goToGames')}
          </Anchor>
        </Text>
      </Page>
    );
  }
  const currencyCode = view.currencyCode;
  const summary = summaryIn(stats.data, view);
  const total = summary?.total ?? NO_GAMES;
  const tournaments =
    summary?.byGameType.find((row) => row.gameType === 'TOURNAMENT')?.figures ?? NO_GAMES;
  const now = bankrollIn(bankrollNow.data, view);
  // Net of the finished games of the period in each room.
  const netOfPeriod = new Map(
    groupsIn(byRoom.data, view).flatMap((group) =>
      group.key.room ? [[group.key.room.id, group.figures.net] as const] : [],
    ),
  );
  const missing = view.converted
    ? mergeMissing(
        stats.data.converted.missingRates,
        bankrollNow.data.converted.missingRates,
        byRoom.data.converted.missingRates,
        byVariant.data?.converted.missingRates,
      )
    : [];
  const money = (amount: number) => format.money(amount, currencyCode);
  const signed = (amount: number) => format.signedMoney(amount, currencyCode);

  const rows: ResultsRow[] =
    breakdown === 'type'
      ? (summary?.byGameType ?? []).map((row) => ({
          key: row.gameType,
          label: t(`gameTypes.${row.gameType}`),
          figures: row.figures,
        }))
      : groupsIn(byVariant.data, view).map((group) => {
          const gameType = group.key.gameType ? t(`gameTypes.${group.key.gameType}`) : '';
          const variant = group.key.variant
            ? variantLabel(t, group.key.variant)
            : t('dashboard.noVariant');
          return {
            key: `${group.key.gameType}/${group.key.variant?.id ?? 'none'}`,
            label: `${gameType} · ${variant}`,
            figures: group.figures,
          };
        });

  return (
    <Page title={t('nav.dashboard')}>
      {filterBar()}

      <MissingRatesAlert missing={missing} />

      <SimpleGrid cols={{ base: 1, xs: 2, lg: 4 }}>
        <StatCard
          label={t('dashboard.cards.net')}
          value={signed(total.net)}
          tone={toneOf(total.net)}
        >
          {t('dashboard.cards.netDetail', {
            won: money(total.won),
            invested: money(total.invested),
          })}
          {view.converted && (
            <CurrencyAmounts
              signed
              amounts={stats.data.currencies.map((currency) => ({
                currencyCode: currency.currencyCode,
                amount: currency.total.net,
              }))}
            />
          )}
        </StatCard>
        <StatCard label={t('dashboard.cards.roi')} value={format.percent(total.roi)}>
          {t('dashboard.cards.games', {
            count: total.games,
            formatted: format.number(total.games),
          })}
        </StatCard>
        <StatCard
          label={t('dashboard.cards.itm')}
          value={format.percent(tournaments.inTheMoneyRate)}
        >
          {t('dashboard.cards.itmDetail', {
            count: tournaments.games,
            inTheMoney: format.number(tournaments.gamesInTheMoney ?? 0),
            formatted: format.number(tournaments.games),
          })}
        </StatCard>
        <StatCard label={t('dashboard.cards.bankroll')} value={money(now?.total.bankroll ?? 0)}>
          {view.converted && (
            <CurrencyAmounts
              amounts={bankrollNow.data.currencies.map((currency) => ({
                currencyCode: currency.currencyCode,
                amount: currency.total.bankroll,
              }))}
            />
          )}
        </StatCard>
      </SimpleGrid>

      {now && now.total.gamesInPlay > 0 && (
        <Alert color="blue" variant="light">
          {t('dashboard.inPlay', {
            count: now.total.gamesInPlay,
            invested: money(now.total.investedInPlay),
          })}{' '}
          <Anchor component={Link} to="/games" size="sm">
            {t('dashboard.goToGames')}
          </Anchor>
        </Alert>
      )}

      <Stack gap="xs">
        <Group justify="space-between" align="flex-end">
          <Title order={3} size="h4">
            {t('dashboard.results')}
          </Title>
          <SegmentedControl
            size="xs"
            aria-label={t('dashboard.breakdown.label')}
            data={[
              { value: 'type', label: t('dashboard.breakdown.type') },
              { value: 'variant', label: t('dashboard.breakdown.variant') },
            ]}
            value={breakdown}
            onChange={(value) => update({ breakdown: value as Breakdown })}
          />
        </Group>
        {total.games === 0 ? (
          <Text c="dimmed">{t('dashboard.noGames')}</Text>
        ) : breakdown === 'variant' && !byVariant.data ? (
          <Loader />
        ) : (
          <ResultsTable rows={rows} total={total} currencyCode={currencyCode} />
        )}
        {total.ticketsWon > 0 && (
          <Text size="sm" c="dimmed">
            {t('dashboard.ticketsWon', { value: money(total.ticketsWon) })}
          </Text>
        )}
      </Stack>

      {now && (
        <Stack gap="xs">
          <Title order={3} size="h4">
            {t('dashboard.bankroll.title')}
          </Title>
          <BankrollByRoom now={now} netOfPeriod={netOfPeriod} totalNetOfPeriod={total.net} />
        </Stack>
      )}
    </Page>
  );
}
