import { Select, TextInput } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { PERIODS, periodOf, rangeOf, type DateRange, type Period } from './period';

interface PeriodFilterProps {
  range: DateRange;
  onChange: (range: DateRange) => void;
}

/**
 * Chooses a period: a predefined one, or "Custom" with its two dates. Renders its inputs side
 * by side, to be placed in a row of filters.
 */
export function PeriodFilter({ range, onChange }: PeriodFilterProps) {
  const { t } = useTranslation();
  const period = periodOf(range);
  // "Custom" is also a choice in itself: it shows the two dates before any is typed.
  const [customChosen, setCustomChosen] = useState(false);
  const showDates = period === 'custom' || customChosen;

  return (
    <>
      <Select
        label={t('filters.period')}
        w={170}
        allowDeselect={false}
        // Every period in sight, without scrolling the list.
        maxDropdownHeight={320}
        data={PERIODS.map((value) => ({ value, label: t(`filters.periods.${value}`) }))}
        value={showDates ? 'custom' : period}
        onChange={(value) => {
          const chosen = value as Period;
          setCustomChosen(chosen === 'custom');
          if (chosen !== 'custom') {
            onChange(rangeOf(chosen));
          }
        }}
      />
      {showDates && (
        <>
          <TextInput
            type="date"
            label={t('filters.from')}
            w={160}
            value={range.from ?? ''}
            max={range.to}
            onChange={(event) =>
              onChange({ ...range, from: event.currentTarget.value || undefined })
            }
          />
          <TextInput
            type="date"
            label={t('filters.to')}
            w={160}
            value={range.to ?? ''}
            min={range.from}
            onChange={(event) => onChange({ ...range, to: event.currentTarget.value || undefined })}
          />
        </>
      )}
    </>
  );
}
