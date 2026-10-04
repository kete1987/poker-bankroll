import {
  Alert,
  Badge,
  Button,
  Group,
  Loader,
  NumberInput,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
  VisuallyHidden,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconRefresh, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import {
  useCurrencySettings,
  useDeleteManualRate,
  useExchangeRateStatus,
  useManualRates,
  useRefreshExchangeRates,
  useSaveManualRate,
  useUpdateCurrencySettings,
} from '../api/currencies';
import type { Currency, CurrencyRates, ManualRate } from '../api/types';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { IconButton } from '../components/IconButton';
import { useNarrowScreen } from '../components/useNarrowScreen';
import { useSubmit } from '../components/useSubmit';
import { NO_VALUE } from '../format/format';
import { useFormat } from '../format/useFormat';
import { amountOrNull, type Amount } from '../games/amount';
import { todayIso } from '../games/gameDefaults';

/** What the selector of the base currency offers before the currencies: leaving it automatic. */
const AUTOMATIC = 'AUTOMATIC';
/** Rates are given per 1 EUR: it has none. */
const EUR = 'EUR';

/**
 * The currencies: which one amounts of several currencies are converted to, the exchange rates
 * downloaded from the ECB and the ones typed by hand.
 */
export function CurrenciesSettings({ currencies }: { currencies: Currency[] }) {
  const { t } = useTranslation();
  const settings = useCurrencySettings();
  const status = useExchangeRateStatus();
  const manual = useManualRates();

  if ([settings, status, manual].some((query) => query.isError)) {
    return <Alert color="red">{t('games.loadError')}</Alert>;
  }
  if (!settings.data || !status.data || !manual.data) {
    return <Loader />;
  }
  return (
    <Stack gap="xl">
      <BaseCurrency
        currencies={currencies}
        chosen={settings.data.baseCurrencyCode ?? null}
        automatic={settings.data.automaticBaseCurrencyCode}
      />
      <DownloadedRates
        enabled={status.data.enabled}
        lastSuccessAt={status.data.lastSuccessAt ?? null}
        lastError={status.data.lastError ?? null}
        rates={status.data.currencies}
      />
      <ManualRates currencies={currencies} rates={manual.data} />
    </Stack>
  );
}

function BaseCurrency({
  currencies,
  chosen,
  automatic,
}: {
  currencies: Currency[];
  chosen: string | null;
  automatic: string;
}) {
  const { t } = useTranslation();
  const narrow = useNarrowScreen();
  const update = useUpdateCurrencySettings();
  const save = useSubmit();

  return (
    <Stack gap="xs">
      <Title order={3} size="h4">
        {t('settings.currencies.base.title')}
      </Title>
      <Text size="sm" c="dimmed">
        {t('settings.currencies.base.help')}
      </Text>
      {save.failure && <Alert color="red">{save.failure}</Alert>}
      <Select
        label={t('settings.currencies.base.label')}
        w={narrow ? '100%' : 360}
        allowDeselect={false}
        disabled={save.busy}
        data={[
          {
            value: AUTOMATIC,
            label: t('settings.currencies.base.automatic', { currency: automatic }),
          },
          ...currencies.map((currency) => ({ value: currency.code, label: currency.code })),
        ]}
        value={chosen ?? AUTOMATIC}
        onChange={(value) =>
          void save.run(async () => {
            const saved = await update.mutateAsync({
              baseCurrencyCode: value === AUTOMATIC ? null : value,
            });
            notifications.show({
              color: 'teal',
              title: t('settings.currencies.base.saved'),
              message: saved.effectiveBaseCurrencyCode,
            });
          })
        }
      />
    </Stack>
  );
}

function DownloadedRates({
  enabled,
  lastSuccessAt,
  lastError,
  rates,
}: {
  enabled: boolean;
  lastSuccessAt: string | null;
  lastError: string | null;
  rates: CurrencyRates[];
}) {
  const { t } = useTranslation();
  const format = useFormat();
  const refresh = useRefreshExchangeRates();
  const run = useSubmit();

  const state = (rate: CurrencyRates) => {
    const missingBefore =
      rate.neededFrom && (!rate.firstRateOn || rate.firstRateOn > rate.neededFrom)
        ? (rate.firstRateOn ?? null)
        : undefined;
    if (missingBefore === undefined) {
      return (
        <Badge size="sm" variant="light" color="teal">
          {t('settings.currencies.rates.complete')}
        </Badge>
      );
    }
    return (
      <Badge size="sm" variant="light" color="yellow">
        {missingBefore
          ? t('settings.currencies.rates.missingBefore', { date: format.date(missingBefore) })
          : t('settings.currencies.rates.none')}
      </Badge>
    );
  };

  return (
    <Stack gap="xs">
      <Group justify="space-between" align="flex-end">
        <Title order={3} size="h4">
          {t('settings.currencies.rates.title')}
        </Title>
        {enabled && (
          <Button
            variant="light"
            leftSection={<IconRefresh size={16} />}
            loading={run.busy}
            onClick={() =>
              void run.run(async () => {
                const done = await refresh.mutateAsync();
                notifications.show({
                  color: done.lastError ? 'red' : 'teal',
                  title: done.lastError
                    ? t('settings.currencies.rates.failed')
                    : t('settings.currencies.rates.updated'),
                  message: done.lastError ?? undefined,
                });
              })
            }
          >
            {t('settings.currencies.rates.update')}
          </Button>
        )}
      </Group>
      <Text size="sm" c="dimmed">
        {enabled ? t('settings.currencies.rates.help') : t('settings.currencies.rates.disabled')}
      </Text>
      {enabled && (
        <Text size="sm">
          {t('settings.currencies.rates.lastDownload', {
            when: lastSuccessAt
              ? format.moment(lastSuccessAt)
              : t('settings.currencies.rates.notYet'),
          })}
        </Text>
      )}
      {run.failure && <Alert color="red">{run.failure}</Alert>}
      {lastError && (
        <Alert color="red" variant="light" title={t('settings.currencies.rates.lastError')}>
          <Text size="sm" style={{ wordBreak: 'break-word' }}>
            {lastError}
          </Text>
        </Alert>
      )}
      {rates.length === 0 ? (
        <Text size="sm" c="dimmed">
          {t('settings.currencies.rates.noneNeeded')}
        </Text>
      ) : (
        <Table verticalSpacing="xs">
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t('settings.currencies.rates.currency')}</Table.Th>
              <Table.Th>{t('settings.currencies.rates.days')}</Table.Th>
              <Table.Th>{t('settings.currencies.rates.state')}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rates.map((rate) => (
              <Table.Tr key={rate.currencyCode}>
                <Table.Th scope="row" fw={500}>
                  {rate.currencyCode}
                </Table.Th>
                <Table.Td>
                  {rate.firstRateOn && rate.lastRateOn
                    ? t('settings.currencies.rates.between', {
                        from: format.date(rate.firstRateOn),
                        to: format.date(rate.lastRateOn),
                      })
                    : NO_VALUE}
                </Table.Td>
                <Table.Td>{state(rate)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Stack>
  );
}

function ManualRates({ currencies, rates }: { currencies: Currency[]; rates: ManualRate[] }) {
  const { t } = useTranslation();
  const format = useFormat();
  const narrow = useNarrowScreen();
  const saveRate = useSaveManualRate();
  const deleteRate = useDeleteManualRate();
  const save = useSubmit();
  const [toDelete, setToDelete] = useState<ManualRate | null>(null);

  const others = currencies.filter((currency) => currency.code !== EUR);
  const [date, setDate] = useState(todayIso());
  const [currencyCode, setCurrencyCode] = useState<string | null>(others[0]?.code ?? null);
  const [rate, setRate] = useState<Amount>('');
  const [rateError, setRateError] = useState<string | null>(null);
  const [dateError, setDateError] = useState<string | null>(null);

  function submit() {
    const value = amountOrNull(rate);
    const invalid = value === null || value <= 0;
    setRateError(invalid ? t('settings.currencies.manual.errors.rate') : null);
    setDateError(date ? null : t('gameForm.errors.required'));
    if (invalid || !date || !currencyCode) {
      return;
    }
    void save.run(
      async () => {
        await saveRate.mutateAsync({ currencyCode, date, rate: value });
        notifications.show({
          color: 'teal',
          title: t('settings.currencies.manual.saved'),
          message: t('settings.currencies.manual.rate', {
            rate: format.rate(value),
            currency: currencyCode,
          }),
        });
        setRate('');
      },
      (violations) => {
        const ofRate = violations.find((violation) => violation.field === 'rate');
        setRateError(ofRate?.message ?? null);
        return Boolean(ofRate);
      },
    );
  }

  const rateText = (manualRate: ManualRate) =>
    t('settings.currencies.manual.rate', {
      rate: format.rate(manualRate.rate),
      currency: manualRate.currencyCode,
    });

  return (
    <Stack gap="xs">
      <Title order={3} size="h4">
        {t('settings.currencies.manual.title')}
      </Title>
      <Text size="sm" c="dimmed">
        {t('settings.currencies.manual.help')}
      </Text>
      {others.length > 0 && (
        <form
          noValidate
          aria-label={t('settings.currencies.manual.add')}
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          <Stack gap="xs">
            {save.failure && <Alert color="red">{save.failure}</Alert>}
            <SimpleGrid cols={{ base: 1, sm: 4 }} spacing="sm" verticalSpacing="xs">
              <TextInput
                type="date"
                label={t('settings.currencies.manual.date')}
                required
                value={date}
                error={dateError}
                onChange={(event) => {
                  setDate(event.currentTarget.value);
                  setDateError(null);
                }}
              />
              <Select
                label={t('settings.currencies.manual.currency')}
                required
                allowDeselect={false}
                data={others.map((currency) => currency.code)}
                value={currencyCode}
                onChange={setCurrencyCode}
              />
              <NumberInput
                label={t('settings.currencies.manual.value', { currency: currencyCode ?? '' })}
                required
                min={0}
                decimalScale={8}
                decimalSeparator={format.decimalSeparator}
                allowedDecimalSeparators={[',', '.']}
                allowNegative={false}
                hideControls
                value={rate}
                error={rateError}
                onChange={(value) => {
                  setRate(value);
                  setRateError(null);
                }}
              />
              <Button type="submit" loading={save.busy} mt={narrow ? 0 : 25}>
                {t('settings.currencies.manual.add')}
              </Button>
            </SimpleGrid>
          </Stack>
        </form>
      )}
      {rates.length === 0 ? (
        <Text size="sm" c="dimmed">
          {t('settings.currencies.manual.empty')}
        </Text>
      ) : (
        <Table verticalSpacing="xs" highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t('settings.currencies.manual.date')}</Table.Th>
              <Table.Th>{t('settings.currencies.manual.rateColumn')}</Table.Th>
              {/* On a phone the downloaded rate is left out: the two that matter fit. */}
              {!narrow && <Table.Th>{t('settings.currencies.manual.downloaded')}</Table.Th>}
              <Table.Th w={48}>
                <VisuallyHidden>{t('settings.tags.actions')}</VisuallyHidden>
              </Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rates.map((manualRate) => (
              <Table.Tr key={`${manualRate.currencyCode}/${manualRate.date}`}>
                <Table.Td>{format.date(manualRate.date)}</Table.Td>
                <Table.Td>{rateText(manualRate)}</Table.Td>
                {!narrow && (
                  <Table.Td>
                    {manualRate.downloadedRate != null
                      ? format.rate(manualRate.downloadedRate)
                      : NO_VALUE}
                  </Table.Td>
                )}
                <Table.Td>
                  <IconButton
                    variant="subtle"
                    color="red"
                    label={t('settings.currencies.manual.delete', {
                      rate: rateText(manualRate),
                      date: format.date(manualRate.date),
                    })}
                    onClick={() => setToDelete(manualRate)}
                  >
                    <IconTrash size={16} stroke={1.5} />
                  </IconButton>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
      {toDelete && (
        <ConfirmDialog
          title={t('settings.currencies.manual.deleteTitle')}
          confirmLabel={t('games.delete.confirmLabel')}
          destructive
          onClose={() => setToDelete(null)}
          onConfirm={async () => {
            await deleteRate.mutateAsync({
              currencyCode: toDelete.currencyCode,
              date: toDelete.date,
            });
            notifications.show({
              color: 'teal',
              title: t('settings.currencies.manual.deleted'),
              message: rateText(toDelete),
            });
          }}
        >
          {t('settings.currencies.manual.deleteConfirm', {
            rate: rateText(toDelete),
            date: format.date(toDelete.date),
          })}
        </ConfirmDialog>
      )}
    </Stack>
  );
}
