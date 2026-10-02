import {
  Alert,
  Group,
  Loader,
  SegmentedControl,
  Select,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { useTranslation } from 'react-i18next';

import { useRooms } from '../api/rooms';
import { useStatsOverTime } from '../api/stats';
import type { GameType, StatsFigures, StatsGroup } from '../api/types';
import { useVariants } from '../api/variants';
import { Page } from '../components/Page';
import { PeriodFilter } from '../components/PeriodFilter';
import { StatCard } from '../components/StatCard';
import { useFormat } from '../format/useFormat';
import { ScopeFilters } from '../games/ScopeFilters';
import { Breakdown } from '../stats/Breakdown';
import { NetEvolutionChart, type NetPoint } from '../stats/NetEvolutionChart';
import { PeriodTable, type PeriodRow } from '../stats/PeriodTable';
import {
  GRANULARITIES,
  granularityFor,
  toStatsQuery,
  useStatsFilters,
  type ChartMode,
  type Granularity,
  type StatsView,
} from '../stats/useStatsFilters';

function toneOf(amount: number): 'positive' | 'negative' | undefined {
  return amount > 0 ? 'positive' : amount < 0 ? 'negative' : undefined;
}

/** The net of each game type, by type. */
function netByGameType(
  summaries: { gameType: GameType; figures: StatsFigures }[],
): Partial<Record<GameType, number>> {
  return Object.fromEntries(summaries.map((summary) => [summary.gameType, summary.figures.net]));
}

/**
 * Statistics over time, for one currency: the net as a chart (added up, or period by period) and
 * the results of each period in a table.
 */
export function StatsPage() {
  const { t } = useTranslation();
  const format = useFormat();
  const { filters, update } = useStatsFilters();
  const granularity = filters.granularity ?? granularityFor(filters.range);
  const query = toStatsQuery(filters);

  const rooms = useRooms();
  const variants = useVariants();
  const stats = useStatsOverTime(granularity, query);

  const filterBar = (
    <>
      <PeriodFilter range={filters.range} onChange={(range) => update({ range })} />
      <ScopeFilters
        gameTypes={filters.gameTypes}
        roomIds={filters.roomIds}
        variantIds={filters.variantIds}
        rooms={rooms.data ?? []}
        variants={variants.data ?? []}
        onChange={update}
      />
    </>
  );

  if ([rooms, variants, stats].some((request) => request.isError)) {
    return (
      <Page title={t('nav.stats')}>
        <Group gap="sm" align="flex-end">
          {filterBar}
        </Group>
        <Alert color="red">{t('games.loadError')}</Alert>
      </Page>
    );
  }
  if (!stats.data) {
    return (
      <Page title={t('nav.stats')}>
        <Group gap="sm" align="flex-end">
          {filterBar}
        </Group>
        <Loader />
      </Page>
    );
  }

  // Amounts in different currencies are never added up: one currency at a time, by default the
  // one with most games in the period.
  const { summary, groups: overTime } = stats.data;
  const currencies = [...summary.currencies]
    .sort((a, b) => b.total.games - a.total.games || a.currencyCode.localeCompare(b.currencyCode))
    .map((currency) => currency.currencyCode);
  const currencyCode =
    filters.currency && currencies.includes(filters.currency) ? filters.currency : currencies[0];
  const ofCurrency = summary.currencies.find((currency) => currency.currencyCode === currencyCode);
  const total = ofCurrency?.total;
  const totalByGameType = ofCurrency?.byGameType ?? [];
  const groups: StatsGroup[] =
    overTime.currencies.find((currency) => currency.currencyCode === currencyCode)?.groups ?? [];

  // While another cut is loading the previous data stays on screen: it is named by its own cut,
  // not by the one just chosen.
  const drawn = GRANULARITIES.find((value) => value === overTime.groupBy) ?? granularity;
  const labelOf = (period: string) =>
    drawn === 'MONTH'
      ? format.month(period)
      : drawn === 'WEEK'
        ? t('stats.weekOf', { date: format.date(period) })
        : format.date(period);
  const points: NetPoint[] = groups.map((group) => ({
    label: labelOf(group.key.period ?? ''),
    // A week is named by its Monday: on the axis the date is enough.
    axisLabel:
      drawn === 'WEEK' ? format.date(group.key.period ?? '') : labelOf(group.key.period ?? ''),
    games: group.figures.games,
    net: group.figures.net,
    cumulativeNet: group.cumulativeNet ?? 0,
  }));
  // The table reads from the newest period back.
  const rows: PeriodRow[] = groups
    .map((group) => ({
      key: group.key.period ?? '',
      label: labelOf(group.key.period ?? ''),
      figures: group.figures,
      netByGameType: netByGameType(group.byGameType ?? []),
    }))
    .reverse();
  // The best and the worst are picked, not added up: every amount comes from the backend.
  const best = points.reduce<NetPoint | undefined>(
    (found, point) => (!found || point.net > found.net ? point : found),
    undefined,
  );
  const worst = points.reduce<NetPoint | undefined>(
    (found, point) => (!found || point.net < found.net ? point : found),
    undefined,
  );

  return (
    <Page title={t('nav.stats')}>
      <Group gap="sm" align="flex-end">
        {filterBar}
        {filters.view === 'evolution' && (
          <Select
            label={t('stats.groupBy')}
            w={130}
            allowDeselect={false}
            data={GRANULARITIES.map((value) => ({ value, label: t(`stats.granularity.${value}`) }))}
            value={granularity}
            onChange={(value) => update({ granularity: value as Granularity })}
          />
        )}
        {currencies.length > 1 && currencyCode && (
          <Select
            label={t('dashboard.currency')}
            w={110}
            allowDeselect={false}
            data={[...currencies].sort()}
            value={currencyCode}
            onChange={(value) => update({ currency: value ?? undefined })}
          />
        )}
      </Group>

      <SegmentedControl
        aria-label={t('stats.breakdown.view')}
        style={{ alignSelf: 'flex-start' }}
        data={[
          { value: 'evolution', label: t('stats.breakdown.evolution') },
          { value: 'breakdown', label: t('stats.breakdown.title') },
        ]}
        value={filters.view}
        onChange={(value) => update({ view: value as StatsView })}
      />

      {filters.view === 'breakdown' ? (
        currencyCode ? (
          <Breakdown
            query={query}
            currencyCode={currencyCode}
            dimension={filters.dimension}
            sort={filters.sort}
            onChange={update}
          />
        ) : (
          <Text c="dimmed">{t('dashboard.noGames')}</Text>
        )
      ) : (
        <Stack gap="xs">
          <Group justify="space-between" align="flex-end">
            <Title order={3} size="h4">
              {t('stats.net.title')}
            </Title>
            <SegmentedControl
              size="xs"
              aria-label={t('stats.chart.label')}
              data={[
                { value: 'cumulative', label: t('stats.chart.cumulative') },
                { value: 'period', label: t('stats.chart.period') },
              ]}
              value={filters.chart}
              onChange={(value) => update({ chart: value as ChartMode })}
            />
          </Group>
          {!currencyCode || !total || points.length === 0 ? (
            <Text c="dimmed">{t('dashboard.noGames')}</Text>
          ) : (
            <>
              <SimpleGrid cols={{ base: 1, xs: 2, lg: 4 }}>
                <StatCard
                  label={t('stats.net.cards.net')}
                  value={format.signedMoney(total.net, currencyCode)}
                  tone={toneOf(total.net)}
                >
                  {t('dashboard.cards.games', {
                    count: total.games,
                    formatted: format.number(total.games),
                  })}
                </StatCard>
                <StatCard label={t('dashboard.cards.roi')} value={format.percent(total.roi)}>
                  {t('stats.net.cards.invested', {
                    invested: format.money(total.invested, currencyCode),
                  })}
                </StatCard>
                {/* With a single point there is nothing to compare. */}
                {best && worst && points.length > 1 && (
                  <>
                    <StatCard
                      label={t(`stats.net.cards.best.${drawn}`)}
                      value={format.signedMoney(best.net, currencyCode)}
                      tone={toneOf(best.net)}
                    >
                      {best.label}
                    </StatCard>
                    <StatCard
                      label={t(`stats.net.cards.worst.${drawn}`)}
                      value={format.signedMoney(worst.net, currencyCode)}
                      tone={toneOf(worst.net)}
                    >
                      {worst.label}
                    </StatCard>
                  </>
                )}
              </SimpleGrid>
              <NetEvolutionChart points={points} currencyCode={currencyCode} mode={filters.chart} />
              <Title order={3} size="h4" mt="md">
                {t(`stats.table.title.${drawn}`)}
              </Title>
              <PeriodTable
                rows={rows}
                total={total}
                totalNetByGameType={netByGameType(totalByGameType)}
                currencyCode={currencyCode}
                periodLabel={t(`stats.granularity.${drawn}`)}
                page={filters.page}
                onPageChange={(page) => update({ page })}
              />
            </>
          )}
        </Stack>
      )}
    </Page>
  );
}
