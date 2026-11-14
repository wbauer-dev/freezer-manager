const DAY = 24 * 60 * 60 * 1000;

/** Parses a YYYY-MM-DD date as local midnight. */
export function parseDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Today's date in the user's time zone, as used by date inputs (YYYY-MM-DD). */
export function today(): string {
  return toIsoDate(new Date());
}

export function addMonths(iso: string, months: number): string {
  const date = parseDate(iso);
  const day = date.getDate();
  date.setMonth(date.getMonth() + months);
  if (date.getDate() !== day) {
    date.setDate(0); // e.g. 31 Jan + 1 month = 28/29 Feb, like java.time
  }
  return toIsoDate(date);
}

export function daysUntil(iso: string): number {
  return Math.round((parseDate(iso).getTime() - parseDate(today()).getTime()) / DAY);
}

/** Share of the shelf life that is still left, from 1 (just frozen) to 0 (best-before reached). */
export function freshness(frozenOn: string, bestBefore: string): number {
  const total = parseDate(bestBefore).getTime() - parseDate(frozenOn).getTime();
  if (total <= 0) {
    return 0;
  }
  const left = parseDate(bestBefore).getTime() - parseDate(today()).getTime();
  return Math.min(Math.max(left / total, 0), 1);
}

/** Short date with month name, e.g. "13 Nov 2026" or "13. Nov. 2026", for the DatePipe. */
export const DATE_FORMAT = $localize`:@@format.date:d MMM y`;

/**
 * "12 days left", "4 months left", "eat today", "expired 3 days ago".
 *
 * The expiry status comes from the server, which counts days in its own time zone (decision 14). Around midnight the
 * browser's today can be a day ahead or behind, so the days are kept on the side of the status: an item that is not
 * expired never says "expired yesterday", an expired one never says "eat today".
 */
export function timeLeft(
  bestBefore: string,
  status: 'EXPIRED' | 'EXPIRING_SOON' | 'FRESH',
): string {
  const days =
    status === 'EXPIRED' ? Math.min(daysUntil(bestBefore), -1) : Math.max(daysUntil(bestBefore), 0);
  if (days < -1) {
    return $localize`:@@timeLeft.expiredDaysAgo:expired ${-days}:days: days ago`;
  }
  if (days === -1) {
    return $localize`:@@timeLeft.expiredYesterday:expired yesterday`;
  }
  if (days === 0) {
    return $localize`:@@timeLeft.today:eat today`;
  }
  if (days === 1) {
    return $localize`:@@timeLeft.oneDay:1 day left`;
  }
  if (days < 60) {
    return $localize`:@@timeLeft.days:${days}:days: days left`;
  }
  const months = Math.round(days / 30.44);
  if (months < 18) {
    return $localize`:@@timeLeft.months:${months}:months: months left`;
  }
  const years = Math.max(2, Math.round(days / 365.25));
  return $localize`:@@timeLeft.years:${years}:years: years left`;
}
