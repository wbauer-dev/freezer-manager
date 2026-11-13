/**
 * The tint behind a food's emoji. Each household chooses the emojis of its categories; well-known ones get a fitting
 * hue, all others a stable one derived from the emoji.
 */
const HUES: Record<string, number> = {
  '🥩': 355,
  '🥓': 12,
  '🍔': 28,
  '🍗': 36,
  '🐟': 200,
  '🍣': 14,
  '🥦': 120,
  '🍓': 340,
  '🌿': 140,
  '🥖': 40,
  '🍰': 320,
  '🍲': 22,
  '🥣': 48,
  '🧀': 50,
  '📦': 220,
  '🦌': 25,
  '🍕': 18,
  '🍦': 300,
  '🥕': 30,
  '🫐': 250,
  '🍤': 15,
  '🥟': 45,
};

export const UNKNOWN_FOOD = '❄️';

export function foodHue(emoji: string | null | undefined): number {
  if (!emoji) {
    return 210;
  }
  const known = HUES[emoji];
  if (known !== undefined) {
    return known;
  }
  let hash = 0;
  for (const char of emoji) {
    hash = (hash * 31 + char.codePointAt(0)!) % 360;
  }
  return hash;
}

/** Emojis offered for new categories; any other emoji can be typed in. */
export const EMOJI_SUGGESTIONS = [
  '🥩',
  '🥓',
  '🍔',
  '🍗',
  '🦌',
  '🐟',
  '🍣',
  '🍤',
  '🥦',
  '🥕',
  '🌽',
  '🍄',
  '🍓',
  '🫐',
  '🍒',
  '🌿',
  '🥖',
  '🥐',
  '🍰',
  '🍪',
  '🍲',
  '🥣',
  '🍝',
  '🍕',
  '🥟',
  '🧀',
  '🧈',
  '🥚',
  '🍦',
  '📦',
];
