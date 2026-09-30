import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';

import en from '../locales/en.json';
import es from '../locales/es.json';

export const SUPPORTED_LANGUAGES = ['en', 'es'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

export const DEFAULT_LANGUAGE: Language = 'en';
export const LANGUAGE_STORAGE_KEY = 'poker-bankroll.language';

export const resources = {
  en: { translation: en },
  es: { translation: es },
} as const;

export function isSupportedLanguage(value: string | undefined): value is Language {
  return SUPPORTED_LANGUAGES.includes(value as Language);
}

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    supportedLngs: SUPPORTED_LANGUAGES,
    fallbackLng: DEFAULT_LANGUAGE,
    detection: {
      // An explicit choice (saved by setLanguage) wins over the browser language.
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: LANGUAGE_STORAGE_KEY,
      // Only persist explicit choices, so the browser language keeps applying until the user picks one.
      caches: [],
      convertDetectedLanguage: (lng: string) => lng.split('-')[0] ?? lng,
    },
    interpolation: { escapeValue: false },
  });

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng;
});

/** Changes the UI language and remembers it for the next visits. */
export async function setLanguage(language: Language): Promise<void> {
  try {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  } catch {
    // Storage may be unavailable (private mode, blocked cookies): the choice lasts for this visit only.
  }
  await i18n.changeLanguage(language);
}

/** Current UI language, always one of the supported ones. */
export function currentLanguage(): Language {
  const language = i18n.resolvedLanguage;
  return isSupportedLanguage(language) ? language : DEFAULT_LANGUAGE;
}

export default i18n;
