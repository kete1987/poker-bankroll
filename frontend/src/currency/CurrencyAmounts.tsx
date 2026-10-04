import { Text } from '@mantine/core';

import { useFormat } from '../format/useFormat';

export interface CurrencyAmount {
  currencyCode: string;
  amount: number;
}

interface CurrencyAmountsProps {
  /** One amount per currency, each in its own money. */
  amounts: CurrencyAmount[];
  /** With an explicit sign for gains, as results are shown. */
  signed?: boolean;
}

/**
 * What a converted figure is made of: the amount of each currency in its own money, e.g.
 * `1.180,00 € · 320,00 US$`, under the figure in the base currency.
 */
export function CurrencyAmounts({ amounts, signed = false }: CurrencyAmountsProps) {
  const format = useFormat();
  return (
    <Text size="xs" c="dimmed" component="div">
      {amounts
        .map(({ currencyCode, amount }) =>
          signed ? format.signedMoney(amount, currencyCode) : format.money(amount, currencyCode),
        )
        .join(' · ')}
    </Text>
  );
}
