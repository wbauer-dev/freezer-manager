import source from '../../../locale/messages.json';
import german from '../../../../public/i18n/messages.de.json';
import { LANGUAGES, SOURCE_LANGUAGE } from './languages';

/**
 * The English texts are extracted from the code (npm run i18n:extract). Every other language must translate all of
 * them and keep their placeholders.
 */
const TRANSLATIONS: Record<string, { translations: Record<string, string> }> = { de: german };

function placeholders(text: string): string[] {
  return [...text.matchAll(/\{\$[A-Za-z0-9_]+\}|\{[A-Z][A-Z0-9_]*\}/g)]
    .map((match) => match[0])
    .sort();
}

describe('translations', () => {
  it('exist for every language of the app', () => {
    const translated = LANGUAGES.map((language) => language.code).filter(
      (code) => code !== SOURCE_LANGUAGE,
    );
    expect(Object.keys(TRANSLATIONS).sort()).toEqual(translated.sort());
  });

  for (const [language, file] of Object.entries(TRANSLATIONS)) {
    it(`${language} has every message`, () => {
      expect(Object.keys(file.translations).sort()).toEqual(
        Object.keys(source.translations).sort(),
      );
    });

    it(`${language} keeps the placeholders`, () => {
      for (const [id, text] of Object.entries(source.translations as Record<string, string>)) {
        expect(placeholders(file.translations[id] ?? ''), id).toEqual(placeholders(text));
      }
    });
  }
});
