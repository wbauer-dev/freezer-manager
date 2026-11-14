/** Label codes are 8 characters, printed in two groups: K7Q2-M9XA. */
export function formatLabelCode(code: string): string {
  return code.length === 8 ? `${code.slice(0, 4)}-${code.slice(4)}` : code;
}

/** The characters of a label code: digits and letters without I, L, O and U (as in the backend). */
const LABEL_CODE = /^[0-9A-HJKMNP-TV-Z]{8}$/;

/**
 * The label code in a scanned QR code: a scan link (any host — stickers printed earlier may point to another address)
 * or the bare code. Null for QR codes that are not ours.
 */
export function labelCodeFromQr(text: string): string | null {
  const trimmed = text.trim();
  const link = /\/s\/([0-9A-Za-z-]+)\/?(?:[?#].*)?$/.exec(trimmed);
  const code = normalizeLabelCode(link ? link[1] : trimmed);
  return LABEL_CODE.test(code) ? code : null;
}

/** Reads a code as people type it, like the backend: case and dashes don't matter, I/L are 1, O is 0. */
export function normalizeLabelCode(text: string): string {
  return text.toUpperCase().replace(/[\s-]/g, '').replace(/[IL]/g, '1').replace(/O/g, '0');
}
