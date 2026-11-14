import { addMonths, daysUntil, timeLeft } from './dates';

/** Pretends that it is this local time in the browser. */
function now(year: number, month: number, day: number, hour = 12): void {
  vi.setSystemTime(new Date(year, month - 1, day, hour));
}

describe('dates', () => {
  // Days are counted from local midnight, so they must survive a day with 23 or 25 hours.
  beforeAll(() => {
    vi.stubEnv('TZ', 'Europe/Vienna');
  });

  afterAll(() => {
    vi.unstubAllEnvs();
  });

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('addMonths', () => {
    it('keeps the day of the month', () => {
      expect(addMonths('2026-11-14', 3)).toBe('2027-02-14');
    });

    it('ends on the last day of a shorter month, like java.time', () => {
      expect(addMonths('2027-01-31', 1)).toBe('2027-02-28');
      expect(addMonths('2028-01-31', 1)).toBe('2028-02-29');
      expect(addMonths('2026-08-31', 1)).toBe('2026-09-30');
      expect(addMonths('2026-12-31', 2)).toBe('2027-02-28');
    });

    it('goes across years', () => {
      expect(addMonths('2026-11-30', 24)).toBe('2028-11-30');
    });
  });

  describe('daysUntil', () => {
    it('counts calendar days, whatever the time of day', () => {
      now(2026, 11, 14, 23);
      expect(daysUntil('2026-11-15')).toBe(1);
      now(2026, 11, 14, 0);
      expect(daysUntil('2026-11-14')).toBe(0);
      expect(daysUntil('2026-11-13')).toBe(-1);
    });

    it('counts across the switch to and from summer time', () => {
      expect(new Date(2026, 2, 28).getTimezoneOffset()).not.toBe(
        new Date(2026, 2, 30).getTimezoneOffset(),
      );
      now(2026, 3, 28);
      expect(daysUntil('2026-03-30')).toBe(2);
      now(2026, 10, 24);
      expect(daysUntil('2026-10-26')).toBe(2);
      expect(daysUntil('2026-10-24')).toBe(0);
    });
  });

  describe('timeLeft', () => {
    beforeEach(() => now(2026, 11, 14));

    it('says how long is left in days, months or years', () => {
      expect(timeLeft('2026-11-12', 'EXPIRED')).toBe('expired 2 days ago');
      expect(timeLeft('2026-11-13', 'EXPIRED')).toBe('expired yesterday');
      expect(timeLeft('2026-11-14', 'EXPIRING_SOON')).toBe('eat today');
      expect(timeLeft('2026-11-15', 'EXPIRING_SOON')).toBe('1 day left');
      expect(timeLeft('2026-11-16', 'EXPIRING_SOON')).toBe('2 days left');
      expect(timeLeft('2027-01-12', 'FRESH')).toBe('59 days left');
      expect(timeLeft('2027-01-13', 'FRESH')).toBe('2 months left');
      expect(timeLeft('2028-04-14', 'FRESH')).toBe('17 months left');
      expect(timeLeft('2028-05-14', 'FRESH')).toBe('2 years left');
      expect(timeLeft('2031-11-14', 'FRESH')).toBe('5 years left');
    });

    it('agrees with the status from the server when its today is another day', () => {
      // The server's today is still yesterday: due today there, overdue here.
      expect(timeLeft('2026-11-13', 'EXPIRING_SOON')).toBe('eat today');
      // The server's today is already tomorrow: overdue there, due today here.
      expect(timeLeft('2026-11-14', 'EXPIRED')).toBe('expired yesterday');
    });
  });
});
