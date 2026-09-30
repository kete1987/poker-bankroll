import { SegmentedControl } from '@mantine/core';
import { useTranslation } from 'react-i18next';

import { currentLanguage, isSupportedLanguage, setLanguage, SUPPORTED_LANGUAGES } from '../i18n';

export function LanguageSwitcher() {
  const { t } = useTranslation();

  return (
    <SegmentedControl
      size="xs"
      aria-label={t('header.language')}
      value={currentLanguage()}
      onChange={(value) => {
        if (isSupportedLanguage(value)) {
          void setLanguage(value);
        }
      }}
      data={SUPPORTED_LANGUAGES.map((language) => ({
        value: language,
        label: language.toUpperCase(),
      }))}
    />
  );
}
