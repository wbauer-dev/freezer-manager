/// <reference types="@angular/localize" />
import { loadTranslations } from '@angular/localize';
import { registerLocaleData } from '@angular/common';
import { LOCALE_ID } from '@angular/core';

import { LOCALE_DATA, SOURCE_LANGUAGE, preferredLanguage } from './app/core/i18n/languages';

/**
 * Loads the translations before anything else: texts are translated when the code that contains them is loaded,
 * so the app itself is imported afterwards.
 */
async function start(): Promise<void> {
  const language = await loadLanguage(preferredLanguage());
  $localize.locale = language;
  document.documentElement.lang = language;

  const [{ bootstrapApplication }, { appConfig }, { App }] = await Promise.all([
    import('@angular/platform-browser'),
    import('./app/app.config'),
    import('./app/app'),
  ]);
  await bootstrapApplication(App, {
    providers: [...appConfig.providers, { provide: LOCALE_ID, useValue: language }],
  });
}

/** Loads translations and formats; if that fails, the app starts in English rather than not at all. */
async function loadLanguage(language: string): Promise<string> {
  if (language === SOURCE_LANGUAGE) {
    return language;
  }
  try {
    const [response, localeData] = await Promise.all([
      fetch(`/i18n/messages.${language}.json`),
      LOCALE_DATA[language]?.(),
    ]);
    if (!response.ok) {
      throw new Error(`Translations for ${language}: HTTP ${response.status}`);
    }
    loadTranslations((await response.json()).translations);
    if (localeData) {
      registerLocaleData(localeData.default, language);
    }
    return language;
  } catch (error) {
    console.error('Starting in English:', error);
    return SOURCE_LANGUAGE;
  }
}

start().catch((error) => console.error(error));
