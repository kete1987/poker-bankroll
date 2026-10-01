import { Alert, Group, Loader, Select, Stack, Text, Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import { useRooms } from '../api/rooms';
import { useStatsGroups, useStatsSummary } from '../api/stats';
import type { StatsGroup } from '../api/types';
import { useVariants } from '../api/variants';
import { Page } from '../components/Page';
import { PeriodFilter } from '../components/PeriodFilter';
import { useFormat } from '../format/useFormat';
import { ScopeFilters } from '../games/ScopeFilters';
import { NetEvolutionChart, type NetPoint } from '../stats/NetEvolutionChart';
import {
  GRANULARITIES,
  granularityFor,
  toStatsQuery,
  useStatsFilters,
  type Granularity,
} from '../stats/useStatsFilters';

/** Statistics over time, for one currency: for now, the evolution of the net. */
export function StatsPage() {
  const { t } = useTranslation();
  const format = useFormat();
  const { filters, update } = useStatsFilters();
  const granularity = filters.granularity ?? granularityFor(filters.range);
  const query = toStatsQuery(filters);

  const rooms = useRooms();
  const variants = useVariants();
  const summary = useStatsSummary(query);
  const overTime = useStatsGroups(granularity, query);

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

  if ([rooms, variants, summary, overTime].some((request) => request.isError)) {
    return (
      <Page title={t('nav.stats')}>
        <Group gap="sm" align="flex-end">
          {filterBar}
        </Group>
        <Alert color="red">{t('games.loadError')}</Alert>
      </Page>
    );
  }
  if (!summary.data || !overTime.data) {
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
  const currencies = [...summary.data.currencies]
    .sort((a, b) => b.total.games - a.total.games || a.currencyCode.localeCompare(b.currencyCode))
    .map((currency) => currency.currencyCode);
  const currencyCode =
    filters.currency && currencies.includes(filters.currency) ? filters.currency : currencies[0];
  const total = summary.data.currencies.find(
    (currency) => currency.currencyCode === currencyCode,
  )?.total;
  const groups: StatsGroup[] =
    overTime.data.currencies.find((currency) => currency.currencyCode === currencyCode)?.groups ??
    [];

  const labelOf = (period: string) =>
    granularity === 'MONTH'
      ? format.month(period)
      : granularity === 'WEEK'
        ? t('stats.weekOf', { date: format.date(period) })
        : format.date(period);
  const points: NetPoint[] = groups.map((group) => ({
    label: labelOf(group.key.period ?? ''),
    // A week is named by its Monday: on the axis the date is enough.
    axisLabel:
      granularity === 'WEEK'
        ? format.date(group.key.period ?? '')
        : labelOf(group.key.period ?? ''),
    games: group.figures.games,
    net: group.figures.net,
    cumulativeNet: group.cumulativeNet ?? 0,
  }));
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
        <Select
          label={t('stats.groupBy')}
          w={130}
          allowDeselect={false}
          data={GRANULARITIES.map((value) => ({ value, label: t(`stats.granularity.${value}`) }))}
          value={granularity}
          onChange={(value) => update({ granularity: value as Granularity })}
        />
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

      <Stack gap="xs">
        <Title order={3} size="h4">
          {t('stats.net.title')}
        </Title>
        {!currencyCode || !total || points.length === 0 ? (
          <Text c="dimmed">{t('dashboard.noGames')}</Text>
        ) : (
          <>
            <NetEvolutionChart points={points} currencyCode={currencyCode} />
            <Text size="sm" data-testid="net-summary">
              {t('stats.net.summary', {
                net: format.signedMoney(total.net, currencyCode),
                count: total.games,
                formatted: format.number(total.games),
              })}
              {best && worst && points.length > 1 && (
                <>
                  {' · '}
                  {t(`stats.net.best.${granularity}`, {
                    label: best.label,
                    net: format.signedMoney(best.net, currencyCode),
                  })}
                  {' · '}
                  {t(`stats.net.worst.${granularity}`, {
                    label: worst.label,
                    net: format.signedMoney(worst.net, currencyCode),
                  })}
                </>
              )}
            </Text>
          </>
        )}
      </Stack>
    </Page>
  );
}
