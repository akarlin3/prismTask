import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  calculateNextOccurrence,
  parseRecurrenceRule,
  describeRecurrence,
} from '@/utils/recurrence';
import type { RecurrenceRule } from '@/types/task';

describe('recurrence utils', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-04-12T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('calculateNextOccurrence', () => {
    it('daily recurrence advances by 1 day', () => {
      const rule: RecurrenceRule = { type: 'daily' };
      const next = calculateNextOccurrence('2026-04-12', rule);
      // April 13, 2026 is a Monday (weekday), so no skip
      expect(next).toBe('2026-04-13');
    });

    it('daily recurrence skips weekends to Monday', () => {
      const rule: RecurrenceRule = { type: 'daily' };
      // April 17, 2026 is Friday; next day is Saturday => skip to Monday
      const next = calculateNextOccurrence('2026-04-17', rule);
      expect(next).toBe('2026-04-20'); // Monday
    });

    it('daily recurrence with interval > 1', () => {
      const rule: RecurrenceRule = { type: 'daily', interval: 3 };
      const next = calculateNextOccurrence('2026-04-12', rule);
      // April 15 is Wednesday (weekday)
      expect(next).toBe('2026-04-15');
    });

    it('weekly recurrence advances by 1 week', () => {
      const rule: RecurrenceRule = { type: 'weekly' };
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2026-04-19');
    });

    it('weekly recurrence with interval 2', () => {
      const rule: RecurrenceRule = { type: 'weekly', interval: 2 };
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2026-04-26');
    });

    it('weekly recurrence with days_of_week', () => {
      // 1=Monday, 3=Wednesday
      const rule: RecurrenceRule = { type: 'weekly', days_of_week: [1, 3] };
      // Starting from Sunday April 12, next Monday is April 13
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2026-04-13');
    });

    it('weekly with days_of_week picks the next matching day', () => {
      // 5=Friday
      const rule: RecurrenceRule = { type: 'weekly', days_of_week: [5] };
      // From Monday April 13, next Friday is April 17
      const next = calculateNextOccurrence('2026-04-13', rule);
      expect(next).toBe('2026-04-17');
    });

    it('biweekly recurrence advances by 2 weeks', () => {
      const rule: RecurrenceRule = { type: 'biweekly' };
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2026-04-26');
    });

    it('monthly recurrence advances by 1 month', () => {
      const rule: RecurrenceRule = { type: 'monthly' };
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2026-05-12');
    });

    it('monthly recurrence with interval > 1', () => {
      const rule: RecurrenceRule = { type: 'monthly', interval: 3 };
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2026-07-12');
    });

    it('monthly recurrence clamps day to end of shorter month', () => {
      const rule: RecurrenceRule = { type: 'monthly' };
      // January 31 -> February 28 (2026 is not a leap year)
      const next = calculateNextOccurrence('2026-01-31', rule);
      expect(next).toBe('2026-02-28');
    });

    it('monthly recurrence with days_of_month', () => {
      const rule: RecurrenceRule = { type: 'monthly', days_of_month: [15] };
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2026-05-15');
    });

    it('monthly with days_of_month clamps to end of month', () => {
      const rule: RecurrenceRule = { type: 'monthly', days_of_month: [31] };
      // April has 30 days -> May 31 next month => May has 31 => ok
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2026-05-31');
    });

    it('yearly recurrence advances by 1 year', () => {
      const rule: RecurrenceRule = { type: 'yearly' };
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2027-04-12');
    });

    it('yearly recurrence with interval > 1', () => {
      const rule: RecurrenceRule = { type: 'yearly', interval: 2 };
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2028-04-12');
    });

    it('weekdays recurrence skips weekends', () => {
      const rule: RecurrenceRule = { type: 'weekdays' };
      // Friday April 17 -> skip Sat/Sun -> Monday April 20
      const next = calculateNextOccurrence('2026-04-17', rule);
      expect(next).toBe('2026-04-20');
    });

    it('weekdays recurrence on a weekday goes to next day', () => {
      const rule: RecurrenceRule = { type: 'weekdays' };
      // Monday April 13 -> Tuesday April 14
      const next = calculateNextOccurrence('2026-04-13', rule);
      expect(next).toBe('2026-04-14');
    });

    it('custom recurrence treats like daily with interval', () => {
      const rule: RecurrenceRule = { type: 'custom', interval: 5 };
      const next = calculateNextOccurrence('2026-04-12', rule);
      // April 17 is Friday => but custom + skip_weekends default is handled only if skip_weekends is set
      // The code checks (rule.type === 'daily' || rule.skip_weekends) for weekend skip
      // type is 'custom', skip_weekends is not set => April 17 stays
      expect(next).toBe('2026-04-17');
    });

    it('custom recurrence with skip_weekends', () => {
      const rule: RecurrenceRule = { type: 'custom', interval: 6, skip_weekends: true };
      // April 12 + 6 = April 18, which is Saturday => skip to Monday April 20
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBe('2026-04-20');
    });

    it('respects end_date and returns null when past it', () => {
      const rule: RecurrenceRule = {
        type: 'daily',
        end_date: '2026-04-13',
      };
      // Next from April 12 would be April 13 (Monday), which is not past end_date
      const next1 = calculateNextOccurrence('2026-04-12', rule);
      expect(next1).toBe('2026-04-13');

      // Next from April 13 would be April 14, but end_date is April 13 => null
      const next2 = calculateNextOccurrence('2026-04-13', rule);
      expect(next2).toBeNull();
    });

    it('respects end_after_count and returns null when count reached', () => {
      const rule: RecurrenceRule = {
        type: 'daily',
        end_after_count: 5,
        occurrence_count: 5,
      };
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBeNull();
    });

    it('allows next occurrence when count not yet reached', () => {
      const rule: RecurrenceRule = {
        type: 'daily',
        end_after_count: 5,
        occurrence_count: 3,
      };
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).not.toBeNull();
    });

    it('returns null for unknown recurrence type', () => {
      const rule = { type: 'unknown' } as unknown as RecurrenceRule;
      const next = calculateNextOccurrence('2026-04-12', rule);
      expect(next).toBeNull();
    });
  });

  describe('parseRecurrenceRule', () => {
    it('parses valid JSON string', () => {
      const json = '{"type":"daily","interval":1}';
      const rule = parseRecurrenceRule(json);
      expect(rule).toEqual({ type: 'daily', interval: 1 });
    });

    it('parses complex recurrence rule', () => {
      const json = '{"type":"weekly","interval":2,"days_of_week":[1,3,5]}';
      const rule = parseRecurrenceRule(json);
      expect(rule).toEqual({
        type: 'weekly',
        interval: 2,
        days_of_week: [1, 3, 5],
      });
    });

    it('returns null for null input', () => {
      expect(parseRecurrenceRule(null)).toBeNull();
    });

    it('returns null for empty string', () => {
      expect(parseRecurrenceRule('')).toBeNull();
    });

    it('returns null for invalid JSON', () => {
      expect(parseRecurrenceRule('not json')).toBeNull();
    });

    it('returns null for malformed JSON', () => {
      expect(parseRecurrenceRule('{type: daily}')).toBeNull();
    });
  });

  describe('describeRecurrence', () => {
    it('returns "Daily" for simple daily rule', () => {
      expect(describeRecurrence({ type: 'daily' })).toBe('Daily');
    });

    it('returns "Every N days" for daily with interval > 1', () => {
      expect(describeRecurrence({ type: 'daily', interval: 3 })).toBe(
        'Every 3 days',
      );
    });

    it('returns "Every weekday" for weekdays type', () => {
      expect(describeRecurrence({ type: 'weekdays' })).toBe('Every weekday');
    });

    it('returns "Weekly" for simple weekly rule', () => {
      expect(describeRecurrence({ type: 'weekly' })).toBe('Weekly');
    });

    it('returns "Every N weeks" for weekly with interval > 1', () => {
      expect(describeRecurrence({ type: 'weekly', interval: 2 })).toBe(
        'Every 2 weeks',
      );
    });

    it('includes day names for weekly with days_of_week', () => {
      const desc = describeRecurrence({
        type: 'weekly',
        days_of_week: [1, 3, 5],
      });
      expect(desc).toBe('Weekly on Mon, Wed, Fri');
    });

    it('returns "Every 2 weeks" for biweekly', () => {
      expect(describeRecurrence({ type: 'biweekly' })).toBe('Every 2 weeks');
    });

    it('returns "Monthly" for simple monthly rule', () => {
      expect(describeRecurrence({ type: 'monthly' })).toBe('Monthly');
    });

    it('returns "Every N months" for monthly with interval > 1', () => {
      expect(describeRecurrence({ type: 'monthly', interval: 3 })).toBe(
        'Every 3 months',
      );
    });

    it('returns "Yearly" for simple yearly rule', () => {
      expect(describeRecurrence({ type: 'yearly' })).toBe('Yearly');
    });

    it('returns "Every N years" for yearly with interval > 1', () => {
      expect(describeRecurrence({ type: 'yearly', interval: 5 })).toBe(
        'Every 5 years',
      );
    });

    it('returns custom description for custom type', () => {
      expect(describeRecurrence({ type: 'custom', interval: 10 })).toBe(
        'Every 10 days (custom)',
      );
    });

    it('returns "Recurring" for unknown type', () => {
      const rule = { type: 'unknown' } as unknown as RecurrenceRule;
      expect(describeRecurrence(rule)).toBe('Recurring');
    });
  });
});
