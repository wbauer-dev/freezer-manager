/** Label codes are 8 characters, printed in two groups: K7Q2-M9XA. */
export function formatLabelCode(code: string): string {
  return code.length === 8 ? `${code.slice(0, 4)}-${code.slice(4)}` : code;
}

/** Reads a code as people type it, like the backend: case and dashes don't matter, I/L are 1, O is 0. */
export function normalizeLabelCode(text: string): string {
  return text.toUpperCase().replace(/[\s-]/g, '').replace(/[IL]/g, '1').replace(/O/g, '0');
}
