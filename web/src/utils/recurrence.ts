import {
  addDays,
  addWeeks,
  addMonths,
  addYears,
  getDay,
  lastDayOfMonth,
  setDate,
  isWeekend,
  nextMonday,
  isBefore,
  parseISO,
  format,
} from 'date-fns';
import type { RecurrenceRule } from '@/types/task';

/**
 * Calculate the next occurrence date based on a recurrence rule.
 * Ported from backend/app/services/recurrence.py and Android RecurrenceEngine.
 *
 * Returns the next due date as an ISO string, or null if recurrence has ended.
 */
export function calculateNextOccurrence(
  currentDueDate: string,
  rule: RecurrenceRule,
): string | null {
  const current = parseISO(currentDueDate);
  const interval = rule.interval ?? 1;
  let next: Date;

  switch (rule.type) {
    case 'daily': {
      next = addDays(current, interval);
      break;
    }

    case 'weekdays': {
      // Skip weekends: advance by 1 day until we land on a weekday
      next = addDays(current, 1);
      while (isWeekend(next)) {
        next = addDays(next, 1);
      }
      break;
    }

    case 'weekly': {
      const daysOfWeek = rule.days_of_week;
      if (daysOfWeek && daysOfWeek.length > 0) {
        // Find the next matching day of week
        // days_of_week uses 0=Sunday, 1=Monday, ..., 6=Saturday (matching getDay())
        for (let i = 1; i <= 7 * interval; i++) {
          const candidate = addDays(current, i);
          if (daysOfWeek.includes(getDay(candidate))) {
            next = candidate;
            break;
          }
        }
        // Fallback if no match found
        if (!next!) {
          next = addWeeks(current, interval);
        }
      } else {
        next = addWeeks(current, interval);
      }
      break;
    }

    case 'biweekly': {
      next = addWeeks(current, 2);
      break;
    }

    case 'monthly': {
      const daysOfMonth = rule.days_of_month;
      if (daysOfMonth && daysOfMonth.length > 0) {
        // Find the next matching day-of-month
        let candidate = addMonths(current, 1);
        const targetDay = daysOfMonth[0];
        const maxDay = lastDayOfMonth(candidate).getDate();
        candidate = setDate(candidate, Math.min(targetDay, maxDay));
        next = candidate;
      } else {
        // Simple monthly: same day-of-month, clamped
        const target = addMonths(current, interval);
        const maxDay = lastDayOfMonth(target).getDate();
        const day = Math.min(current.getDate(), maxDay);
        next = setDate(target, day);
      }
      break;
    }

    case 'yearly': {
      next = addYears(current, interval);
      break;
    }

    case 'custom': {
      // Custom: treat like daily with interval
      next = addDays(current, interval);
      break;
    }

    default:
      return null;
  }

  // Handle skip weekends for daily recurrence
  if ((rule.type === 'daily' || rule.skip_weekends) && isWeekend(next)) {
    next = nextMonday(next);
  }

  // Check end conditions
  if (rule.end_date) {
    const endDate = parseISO(rule.end_date);
    if (isBefore(endDate, next)) {
      return null; // Recurrence has ended
    }
  }

  // Check max occurrence count
  if (
    rule.end_after_count != null &&
    rule.occurrence_count != null &&
    rule.occurrence_count >= rule.end_after_count
  ) {
    return null; // Max occurrences reached
  }

  return format(next, 'yyyy-MM-dd');
}

/**
 * Parse a recurrence JSON string into a RecurrenceRule object.
 * Returns null if the string is invalid or empty.
 */
export function parseRecurrenceRule(json: string | null): RecurrenceRule | null {
  if (!json) return null;
  try {
    return JSON.parse(json) as RecurrenceRule;
  } catch {
    return null;
  }
}

/**
 * Get a human-readable description of a recurrence rule.
 */
export function describeRecurrence(rule: RecurrenceRule): string {
  const interval = rule.interval ?? 1;
  const plural = interval > 1;

  switch (rule.type) {
    case 'daily':
      return plural ? `Every ${interval} days` : 'Daily';
    case 'weekdays':
      return 'Every weekday';
    case 'weekly': {
      const base = plural ? `Every ${interval} weeks` : 'Weekly';
      if (rule.days_of_week && rule.days_of_week.length > 0) {
        const dayNames = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
        const days = rule.days_of_week.map((d) => dayNames[d]).join(', ');
        return `${base} on ${days}`;
      }
      return base;
    }
    case 'biweekly':
      return 'Every 2 weeks';
    case 'monthly':
      return plural ? `Every ${interval} months` : 'Monthly';
    case 'yearly':
      return plural ? `Every ${interval} years` : 'Yearly';
    case 'custom':
      return `Every ${interval} days (custom)`;
    default:
      return 'Recurring';
  }
}
