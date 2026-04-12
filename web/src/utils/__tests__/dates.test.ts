import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { formatDate, formatDateTime, formatRelative, isOverdue, formatTime } from '@/utils/dates';

describe('dates utils', () => {
  beforeEach(() => {
    // Fix "now" to 2026-04-12 12:00:00 UTC
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-04-12T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('formatDate', () => {
    it('returns empty string for null', () => {
      expect(formatDate(null)).toBe('');
    });

    it('returns "Today" for today\'s date', () => {
      expect(formatDate('2026-04-12')).toBe('Today');
    });

    it('returns "Tomorrow" for tomorrow\'s date', () => {
      expect(formatDate('2026-04-13')).toBe('Tomorrow');
    });

    it('returns "Yesterday" for yesterday\'s date', () => {
      expect(formatDate('2026-04-11')).toBe('Yesterday');
    });

    it('returns day name for dates within the next 6 days', () => {
      // April 14, 2026 is a Tuesday
      expect(formatDate('2026-04-14')).toBe('Tuesday');
      // April 15, 2026 is a Wednesday
      expect(formatDate('2026-04-15')).toBe('Wednesday');
      // April 18, 2026 is a Saturday (6 days away)
      expect(formatDate('2026-04-18')).toBe('Saturday');
    });

    it('returns "MMM d, yyyy" for dates further than a week', () => {
      expect(formatDate('2026-04-25')).toBe('Apr 25, 2026');
      expect(formatDate('2026-01-01')).toBe('Jan 1, 2026');
      expect(formatDate('2027-06-15')).toBe('Jun 15, 2027');
    });

    it('returns "MMM d, yyyy" for dates more than a week in the past', () => {
      expect(formatDate('2026-04-01')).toBe('Apr 1, 2026');
      expect(formatDate('2025-12-25')).toBe('Dec 25, 2025');
    });
  });

  describe('formatDateTime', () => {
    it('returns empty string for null', () => {
      expect(formatDateTime(null)).toBe('');
    });

    it('returns formatted date and time', () => {
      expect(formatDateTime('2026-04-12T14:30:00')).toBe('Apr 12, 2026 2:30 PM');
    });

    it('formats morning times correctly', () => {
      expect(formatDateTime('2026-01-15T09:05:00')).toBe('Jan 15, 2026 9:05 AM');
    });

    it('formats midnight correctly', () => {
      expect(formatDateTime('2026-06-01T00:00:00')).toBe('Jun 1, 2026 12:00 AM');
    });

    it('formats noon correctly', () => {
      expect(formatDateTime('2026-06-01T12:00:00')).toBe('Jun 1, 2026 12:00 PM');
    });
  });

  describe('formatRelative', () => {
    it('returns empty string for null', () => {
      expect(formatRelative(null)).toBe('');
    });

    it('returns relative time for past dates', () => {
      const result = formatRelative('2026-04-11T12:00:00');
      expect(result).toMatch(/ago/);
    });

    it('returns relative time for future dates', () => {
      const result = formatRelative('2026-04-15T12:00:00');
      expect(result).toMatch(/in /);
    });

    it('returns relative time for a date hours ago', () => {
      const result = formatRelative('2026-04-12T06:00:00');
      expect(result).toMatch(/hours? ago/);
    });
  });

  describe('isOverdue', () => {
    it('returns false for null', () => {
      expect(isOverdue(null)).toBe(false);
    });

    it('returns false for today', () => {
      expect(isOverdue('2026-04-12')).toBe(false);
    });

    it('returns true for past dates', () => {
      expect(isOverdue('2026-04-10')).toBe(true);
      expect(isOverdue('2026-04-11')).toBe(true);
      expect(isOverdue('2025-01-01')).toBe(true);
    });

    it('returns false for future dates', () => {
      expect(isOverdue('2026-04-13')).toBe(false);
      expect(isOverdue('2026-12-31')).toBe(false);
    });
  });

  describe('formatTime', () => {
    it('returns empty string for null', () => {
      expect(formatTime(null)).toBe('');
    });

    it('converts 24-hour afternoon time to 12-hour format', () => {
      expect(formatTime('14:30')).toBe('2:30 PM');
    });

    it('converts morning time correctly', () => {
      expect(formatTime('09:00')).toBe('9:00 AM');
    });

    it('handles midnight (00:00)', () => {
      expect(formatTime('00:00')).toBe('12:00 AM');
    });

    it('handles noon (12:00)', () => {
      expect(formatTime('12:00')).toBe('12:00 PM');
    });

    it('handles 12:30 PM', () => {
      expect(formatTime('12:30')).toBe('12:30 PM');
    });

    it('handles 1:00 AM', () => {
      expect(formatTime('01:00')).toBe('1:00 AM');
    });

    it('handles 11:59 PM', () => {
      expect(formatTime('23:59')).toBe('11:59 PM');
    });
  });
});
