/**
 * The languages of the user interface. Adding one means: an entry here, its locale data below and a translation file
 * public/i18n/messages.<code>.json (see README).
 */
export interface Language {
  code: string;
  /** In the language itself, so everyone finds theirs. */
  name: string;
}

export const LANGUAGES: readonly Language[] = [
  { code: 'en', name: 'English' },
  { code: 'de', name: 'Deutsch' },
];

/** Formats for dates and numbers; English is built into Angular. */
export const LOCALE_DATA: Record<string, () => Promise<{ default: unknown[] }>> = {
  de: () => import('@angular/common/locales/de'),
};

export const SOURCE_LANGUAGE = 'en';
const STORAGE_KEY = 'freezer.language';

/** The language chosen in the app, else the browser's preferred one we have, else English. */
export function preferredLanguage(): string {
  const supported = new Set(LANGUAGES.map((language) => language.code));
  let stored: string | null = null;
  try {
    stored = localStorage.getItem(STORAGE_KEY);
  } catch {
    // Storage may be blocked; the browser's languages still apply.
  }
  if (stored && supported.has(stored)) {
    return stored;
  }
  const browser = (navigator.languages ?? [navigator.language])
    .map((tag) => tag.toLowerCase().split('-')[0])
    .find((code) => supported.has(code));
  return browser ?? SOURCE_LANGUAGE;
}

/** Translations are loaded before the app starts, so switching the language starts the app again. */
export function switchLanguage(code: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, code);
  } catch {
    // Without storage the browser's language stays in effect.
  }
  window.location.reload();
}
