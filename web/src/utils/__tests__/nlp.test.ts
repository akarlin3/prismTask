import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { parseQuickAdd } from '@/utils/nlp';

describe('nlp utils', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    // 2026-04-12 is a Sunday
    vi.setSystemTime(new Date('2026-04-12T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('parseQuickAdd', () => {
    describe('priority extraction', () => {
      it('extracts priority from !! as high (2)', () => {
        const result = parseQuickAdd('Buy milk !!');
        expect(result.priority).toBe(2);
        expect(result.title).toBe('Buy milk');
      });

      it('extracts priority from !urgent as 1', () => {
        const result = parseQuickAdd('Fix bug !urgent');
        expect(result.priority).toBe(1);
        expect(result.title).toBe('Fix bug');
      });

      it('extracts priority from !high as 2', () => {
        const result = parseQuickAdd('Review PR !high');
        expect(result.priority).toBe(2);
        expect(result.title).toBe('Review PR');
      });

      it('extracts priority from !medium as 3', () => {
        const result = parseQuickAdd('Update docs !medium');
        expect(result.priority).toBe(3);
        expect(result.title).toBe('Update docs');
      });

      it('extracts priority from !low as 4', () => {
        const result = parseQuickAdd('Clean desk !low');
        expect(result.priority).toBe(4);
        expect(result.title).toBe('Clean desk');
      });

      it('extracts priority from !1 as 1', () => {
        const result = parseQuickAdd('Task !1');
        expect(result.priority).toBe(1);
      });

      it('extracts priority from !4 as 4', () => {
        const result = parseQuickAdd('Task !4');
        expect(result.priority).toBe(4);
      });

      it('returns null priority when none specified', () => {
        const result = parseQuickAdd('Just a task');
        expect(result.priority).toBeNull();
      });
    });

    describe('tag extraction', () => {
      it('extracts a single tag', () => {
        const result = parseQuickAdd('Buy milk #shopping');
        expect(result.tags).toEqual(['shopping']);
        expect(result.title).toBe('Buy milk');
      });

      it('extracts multiple tags', () => {
        const result = parseQuickAdd('Exercise #fitness #health');
        expect(result.tags).toEqual(['fitness', 'health']);
        expect(result.title).toBe('Exercise');
      });

      it('extracts tags with hyphens', () => {
        const result = parseQuickAdd('Task #my-tag');
        expect(result.tags).toEqual(['my-tag']);
      });

      it('returns empty array when no tags', () => {
        const result = parseQuickAdd('Just a task');
        expect(result.tags).toEqual([]);
      });
    });

    describe('project extraction', () => {
      it('extracts project from @project', () => {
        const result = parseQuickAdd('Fix API @backend');
        expect(result.project).toBe('backend');
        expect(result.title).toBe('Fix API');
      });

      it('returns null when no project', () => {
        const result = parseQuickAdd('Just a task');
        expect(result.project).toBeNull();
      });
    });

    describe('date extraction', () => {
      it('extracts "today"', () => {
        const result = parseQuickAdd('Buy milk today');
        expect(result.dueDate).toBe('2026-04-12');
        expect(result.title).toBe('Buy milk');
      });

      it('extracts "tomorrow"', () => {
        const result = parseQuickAdd('Submit report tomorrow');
        expect(result.dueDate).toBe('2026-04-13');
        expect(result.title).toBe('Submit report');
      });

      it('extracts "next week"', () => {
        const result = parseQuickAdd('Plan meeting next week');
        expect(result.dueDate).toBe('2026-04-19');
        expect(result.title).toBe('Plan meeting');
      });

      it('extracts "next Monday"', () => {
        // Today is Sunday April 12, next Monday is April 13
        const result = parseQuickAdd('Team standup next Monday');
        expect(result.dueDate).toBe('2026-04-13');
        expect(result.title).toBe('Team standup');
      });

      it('extracts "next Friday"', () => {
        // Today is Sunday April 12, next Friday is April 17
        const result = parseQuickAdd('Review next Friday');
        expect(result.dueDate).toBe('2026-04-17');
        expect(result.title).toBe('Review');
      });

      it('extracts "in 3 days"', () => {
        const result = parseQuickAdd('Follow up in 3 days');
        expect(result.dueDate).toBe('2026-04-15');
        expect(result.title).toBe('Follow up');
      });

      it('extracts "in 2 weeks"', () => {
        const result = parseQuickAdd('Dentist in 2 weeks');
        expect(result.dueDate).toBe('2026-04-26');
        expect(result.title).toBe('Dentist');
      });

      it('extracts "Jan 15"', () => {
        // Jan 15 is in the past from April 12, so it should roll to 2027
        const result = parseQuickAdd('Pay taxes Jan 15');
        expect(result.dueDate).toBe('2027-01-15');
        expect(result.title).toBe('Pay taxes');
      });

      it('extracts "May 20"', () => {
        // May 20 is in the future
        const result = parseQuickAdd('Conference May 20');
        expect(result.dueDate).toBe('2026-05-20');
        expect(result.title).toBe('Conference');
      });

      it('extracts "5/20" (M/D format)', () => {
        const result = parseQuickAdd('Deadline 5/20');
        expect(result.dueDate).toBe('2026-05-20');
        expect(result.title).toBe('Deadline');
      });

      it('extracts ISO dates like "2026-05-15"', () => {
        const result = parseQuickAdd('Event 2026-05-15');
        expect(result.dueDate).toBe('2026-05-15');
        expect(result.title).toBe('Event');
      });

      it('returns null dueDate when no date specified', () => {
        const result = parseQuickAdd('Just a task');
        expect(result.dueDate).toBeNull();
      });
    });

    describe('time extraction', () => {
      it('extracts "at 3pm"', () => {
        const result = parseQuickAdd('Meeting at 3pm');
        expect(result.dueTime).toBe('15:00');
        expect(result.title).toBe('Meeting');
      });

      it('extracts "at 3:30pm"', () => {
        const result = parseQuickAdd('Call at 3:30pm');
        expect(result.dueTime).toBe('15:30');
      });

      it('extracts "at 9am"', () => {
        const result = parseQuickAdd('Standup at 9am');
        expect(result.dueTime).toBe('09:00');
      });

      it('extracts "at 15:00" (24-hour format)', () => {
        const result = parseQuickAdd('Sync at 15:00');
        expect(result.dueTime).toBe('15:00');
      });

      it('extracts "at noon"', () => {
        const result = parseQuickAdd('Lunch at noon');
        expect(result.dueTime).toBe('12:00');
      });

      it('extracts "at midnight"', () => {
        const result = parseQuickAdd('Deploy at midnight');
        expect(result.dueTime).toBe('00:00');
      });

      it('handles 12pm correctly', () => {
        const result = parseQuickAdd('Lunch at 12pm');
        expect(result.dueTime).toBe('12:00');
      });

      it('handles 12am correctly', () => {
        const result = parseQuickAdd('Deploy at 12am');
        expect(result.dueTime).toBe('00:00');
      });

      it('returns null dueTime when no time specified', () => {
        const result = parseQuickAdd('Just a task');
        expect(result.dueTime).toBeNull();
      });
    });

    describe('recurrence extraction', () => {
      it('extracts "daily"', () => {
        const result = parseQuickAdd('Take vitamins daily');
        expect(result.recurrenceHint).toBe('daily');
        expect(result.title).toBe('Take vitamins');
      });

      it('extracts "weekly"', () => {
        const result = parseQuickAdd('Team meeting weekly');
        expect(result.recurrenceHint).toBe('weekly');
      });

      it('extracts "monthly"', () => {
        const result = parseQuickAdd('Pay rent monthly');
        expect(result.recurrenceHint).toBe('monthly');
      });

      it('extracts "yearly"', () => {
        const result = parseQuickAdd('Renew license yearly');
        expect(result.recurrenceHint).toBe('yearly');
      });

      it('extracts "every day" as daily', () => {
        const result = parseQuickAdd('Exercise every day');
        expect(result.recurrenceHint).toBe('daily');
      });

      it('extracts "every Monday" as weekly and sets due date', () => {
        const result = parseQuickAdd('Standup every Monday');
        expect(result.recurrenceHint).toBe('weekly');
        // Next Monday from Sunday April 12 is April 13
        expect(result.dueDate).toBe('2026-04-13');
      });

      it('returns null recurrenceHint when none specified', () => {
        const result = parseQuickAdd('Just a task');
        expect(result.recurrenceHint).toBeNull();
      });
    });

    describe('title cleanup', () => {
      it('cleans up extra whitespace', () => {
        const result = parseQuickAdd('  Buy  milk   #shopping  ');
        expect(result.title).toBe('Buy milk');
      });

      it('returns trimmed title after all extractions', () => {
        const result = parseQuickAdd('Buy milk tomorrow !high #shopping @groceries');
        expect(result.title).toBe('Buy milk');
        expect(result.dueDate).toBe('2026-04-13');
        expect(result.priority).toBe(2);
        expect(result.tags).toEqual(['shopping']);
        expect(result.project).toBe('groceries');
      });
    });

    describe('combined input', () => {
      it('handles all features together', () => {
        const result = parseQuickAdd(
          'Buy milk tomorrow !high #shopping @groceries at 3pm',
        );
        expect(result.title).toBe('Buy milk');
        expect(result.dueDate).toBe('2026-04-13');
        expect(result.priority).toBe(2);
        expect(result.tags).toEqual(['shopping']);
        expect(result.project).toBe('groceries');
        expect(result.dueTime).toBe('15:00');
        expect(result.recurrenceHint).toBeNull();
      });

      it('handles recurrence with tags and priority', () => {
        const result = parseQuickAdd('Exercise daily !3 #fitness #health');
        expect(result.title).toBe('Exercise');
        expect(result.recurrenceHint).toBe('daily');
        expect(result.priority).toBe(3);
        expect(result.tags).toEqual(['fitness', 'health']);
      });

      it('handles empty input', () => {
        const result = parseQuickAdd('');
        expect(result.title).toBe('');
        expect(result.priority).toBeNull();
        expect(result.dueDate).toBeNull();
        expect(result.dueTime).toBeNull();
        expect(result.tags).toEqual([]);
        expect(result.project).toBeNull();
        expect(result.recurrenceHint).toBeNull();
      });

      it('handles input with only markers', () => {
        const result = parseQuickAdd('#shopping @groceries !!');
        expect(result.title).toBe('');
        expect(result.priority).toBe(2);
        expect(result.tags).toEqual(['shopping']);
        expect(result.project).toBe('groceries');
      });
    });
  });
});
