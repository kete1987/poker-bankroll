import { Alert, Anchor, List } from '@mantine/core';
import { IconAlertTriangle } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import type { MissingExchangeRate } from '../api/types';
import { useFormat } from '../format/useFormat';

/**
 * Says which amounts could not be converted for lack of an exchange rate (they are left out of
 * the converted figures) and where rates are downloaded or typed. Nothing when none is missing.
 */
export function MissingRatesAlert({ missing }: { missing: MissingExchangeRate[] }) {
  const { t } = useTranslation();
  const format = useFormat();
  if (missing.length === 0) {
    return null;
  }
  return (
    <Alert
      color="yellow"
      variant="light"
      icon={<IconAlertTriangle size={18} />}
      title={t('currencies.missing.title')}
    >
      {t('currencies.missing.text')}
      <List size="sm" my={4}>
        {missing.map((rate) => (
          <List.Item key={rate.currencyCode}>
            {rate.from === rate.to
              ? t('currencies.missing.day', {
                  currency: rate.currencyCode,
                  date: format.date(rate.from),
                })
              : t('currencies.missing.days', {
                  currency: rate.currencyCode,
                  from: format.date(rate.from),
                  to: format.date(rate.to),
                })}
          </List.Item>
        ))}
      </List>
      <Anchor component={Link} to="/settings?tab=currencies" size="sm">
        {t('currencies.missing.link')}
      </Anchor>
    </Alert>
  );
}
