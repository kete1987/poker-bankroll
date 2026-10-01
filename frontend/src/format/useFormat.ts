import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '../i18n';
import { createFormatters, localeFor, type Formatters } from './format';

/** Formatters for the current UI language; they change when the language does. */
export function useFormat(): Formatters {
  // Subscribes the component to language changes.
  const { i18n } = useTranslation();
  const language = i18n.resolvedLanguage;

  return useMemo(() => {
    void language;
    return createFormatters(localeFor(currentLanguage()));
  }, [language]);
}
