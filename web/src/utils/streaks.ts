import {
  format,
  subDays,
  startOfWeek,
  endOfWeek,
  eachDayOfInterval,
  differenceInCalendarWeeks,
  isAfter,
  isBefore,
  parseISO,
  startOfDay,
} from 'date-fns';

export interface StreakData {
  currentStreak: number;
  longestStreak: number;
  completionRate7Day: number;
  completionRate30Day: number;
  completionRate90Day: number;
  bestDay: string;
  worstDay: string;
  totalCompletions: number;
}

interface CompletionEntry {
  date: string; // YYYY-MM-DD
  count: number;
}

const DAY_NAMES = [
  'Sunday',
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
];

function toDateStr(d: Date): string {
  return format(d, 'yyyy-MM-dd');
}

function isActiveDayOfWeek(date: Date, activeDays: number[] | null): boolean {
  if (!activeDays || activeDays.length === 0) return true;
  // activeDays uses 1=Mon..7=Sun (ISO), JS getDay() returns 0=Sun..6=Sat
  const jsDay = date.getDay();
  const isoDay = jsDay === 0 ? 7 : jsDay;
  return activeDays.includes(isoDay);
}

function countActiveDaysInRange(
  start: Date,
  end: Date,
  activeDays: number[] | null,
): number {
  if (isAfter(start, end)) return 0;
  const days = eachDayOfInterval({ start, end });
  return days.filter((d) => isActiveDayOfWeek(d, activeDays)).length;
}

export function calculateStreaks(
  completions: CompletionEntry[],
  frequency: 'daily' | 'weekly',
  activeDays: number[] | null,
  targetCount: number,
): StreakData {
  const today = startOfDay(new Date());
  const completionMap = new Map<string, number>();

  for (const c of completions) {
    const existing = completionMap.get(c.date) || 0;
    completionMap.set(c.date, existing + c.count);
  }

  const totalCompletions = completions.reduce((sum, c) => sum + c.count, 0);

  // Day-of-week totals for best/worst day
  const dayTotals = [0, 0, 0, 0, 0, 0, 0]; // Sun-Sat
  for (const c of completions) {
    const date = parseISO(c.date);
    dayTotals[date.getDay()] += c.count;
  }

  // Filter to only active days for best/worst
  const activeDayIndices: number[] = [];
  if (activeDays && activeDays.length > 0) {
    for (const iso of activeDays) {
      activeDayIndices.push(iso === 7 ? 0 : iso); // ISO 7=Sun → JS 0
    }
  } else {
    for (let i = 0; i < 7; i++) activeDayIndices.push(i);
  }

  let bestDayIdx = activeDayIndices[0];
  let worstDayIdx = activeDayIndices[0];
  for (const idx of activeDayIndices) {
    if (dayTotals[idx] > dayTotals[bestDayIdx]) bestDayIdx = idx;
    if (dayTotals[idx] < dayTotals[worstDayIdx]) worstDayIdx = idx;
  }

  if (frequency === 'weekly') {
    return calculateWeeklyStreaks(
      completionMap,
      targetCount,
      today,
      totalCompletions,
      DAY_NAMES[bestDayIdx],
      DAY_NAMES[worstDayIdx],
    );
  }

  // Daily frequency
  let currentStreak = 0;
  let longestStreak = 0;
  let tempStreak = 0;

  // Walk backward from today to calculate current streak
  let checkDate = today;
  // If today is not an active day, skip to the most recent active day
  while (!isActiveDayOfWeek(checkDate, activeDays) && isBefore(subDays(checkDate, 90), checkDate)) {
    checkDate = subDays(checkDate, 1);
  }

  // Allow today to be incomplete — if today is active but not yet completed,
  // check if yesterday starts the streak
  const todayStr = toDateStr(today);
  const todayCompleted = (completionMap.get(todayStr) || 0) >= targetCount;

  if (isActiveDayOfWeek(today, activeDays) && !todayCompleted) {
    checkDate = subDays(today, 1);
    while (!isActiveDayOfWeek(checkDate, activeDays) && isBefore(subDays(checkDate, 90), checkDate)) {
      checkDate = subDays(checkDate, 1);
    }
  }

  // Count current streak
  while (true) {
    if (!isActiveDayOfWeek(checkDate, activeDays)) {
      checkDate = subDays(checkDate, 1);
      continue;
    }
    const dateStr = toDateStr(checkDate);
    const count = completionMap.get(dateStr) || 0;
    if (count >= targetCount) {
      currentStreak++;
      checkDate = subDays(checkDate, 1);
    } else {
      break;
    }
    // Safety: don't go back more than 2 years
    if (differenceInCalendarWeeks(today, checkDate) > 104) break;
  }

  // Calculate longest streak by scanning all dates from earliest to latest
  const allDates = Array.from(completionMap.keys()).sort();
  if (allDates.length > 0) {
    const earliest = parseISO(allDates[0]);
    const latest = today;
    const days = eachDayOfInterval({ start: earliest, end: latest });

    tempStreak = 0;
    for (const day of days) {
      if (!isActiveDayOfWeek(day, activeDays)) continue;
      const dateStr = toDateStr(day);
      const count = completionMap.get(dateStr) || 0;
      if (count >= targetCount) {
        tempStreak++;
        longestStreak = Math.max(longestStreak, tempStreak);
      } else {
        tempStreak = 0;
      }
    }
  }

  longestStreak = Math.max(longestStreak, currentStreak);

  // Completion rates
  const completionRate7Day = calcCompletionRate(completionMap, today, 7, activeDays, targetCount);
  const completionRate30Day = calcCompletionRate(completionMap, today, 30, activeDays, targetCount);
  const completionRate90Day = calcCompletionRate(completionMap, today, 90, activeDays, targetCount);

  return {
    currentStreak,
    longestStreak,
    completionRate7Day,
    completionRate30Day,
    completionRate90Day,
    bestDay: DAY_NAMES[bestDayIdx],
    worstDay: DAY_NAMES[worstDayIdx],
    totalCompletions,
  };
}

function calcCompletionRate(
  completionMap: Map<string, number>,
  today: Date,
  days: number,
  activeDays: number[] | null,
  targetCount: number,
): number {
  const start = subDays(today, days - 1);
  const totalActive = countActiveDaysInRange(start, today, activeDays);
  if (totalActive === 0) return 0;

  let completed = 0;
  const interval = eachDayOfInterval({ start, end: today });
  for (const day of interval) {
    if (!isActiveDayOfWeek(day, activeDays)) continue;
    const count = completionMap.get(toDateStr(day)) || 0;
    if (count >= targetCount) completed++;
  }

  return completed / totalActive;
}

function calculateWeeklyStreaks(
  completionMap: Map<string, number>,
  targetCount: number,
  today: Date,
  totalCompletions: number,
  bestDay: string,
  worstDay: string,
): StreakData {
  // For weekly habits, a "streak" is consecutive weeks where
  // total completions >= targetCount
  function weekTotal(weekStart: Date): number {
    const weekEnd = endOfWeek(weekStart, { weekStartsOn: 1 });
    const days = eachDayOfInterval({ start: weekStart, end: weekEnd });
    let total = 0;
    for (const d of days) {
      total += completionMap.get(toDateStr(d)) || 0;
    }
    return total;
  }

  const thisWeekStart = startOfWeek(today, { weekStartsOn: 1 });

  // Current streak
  let currentStreak = 0;
  let checkWeek = thisWeekStart;
  // If current week not met yet, start from previous week
  if (weekTotal(checkWeek) < targetCount) {
    checkWeek = subDays(checkWeek, 7);
  }
  while (true) {
    if (weekTotal(checkWeek) >= targetCount) {
      currentStreak++;
      checkWeek = subDays(checkWeek, 7);
    } else {
      break;
    }
    if (differenceInCalendarWeeks(today, checkWeek) > 104) break;
  }

  // Longest streak
  let longestStreak = 0;
  let tempStreak = 0;
  // Go back 52 weeks
  for (let i = 52; i >= 0; i--) {
    const ws = subDays(thisWeekStart, i * 7);
    if (weekTotal(ws) >= targetCount) {
      tempStreak++;
      longestStreak = Math.max(longestStreak, tempStreak);
    } else {
      tempStreak = 0;
    }
  }
  longestStreak = Math.max(longestStreak, currentStreak);

  // Completion rates for weekly: use weeks
  const completionRate7Day = weekTotal(thisWeekStart) >= targetCount ? 1 : 0;

  let weeksCompleted30 = 0;
  for (let i = 0; i < 4; i++) {
    if (weekTotal(subDays(thisWeekStart, i * 7)) >= targetCount) weeksCompleted30++;
  }
  const completionRate30Day = weeksCompleted30 / 4;

  let weeksCompleted90 = 0;
  for (let i = 0; i < 13; i++) {
    if (weekTotal(subDays(thisWeekStart, i * 7)) >= targetCount) weeksCompleted90++;
  }
  const completionRate90Day = weeksCompleted90 / 13;

  return {
    currentStreak,
    longestStreak,
    completionRate7Day,
    completionRate30Day,
    completionRate90Day,
    bestDay,
    worstDay,
    totalCompletions,
  };
}

/**
 * Build a map of date → completion count for the last N days.
 * Useful for contribution grids and calendars.
 */
export function buildCompletionGrid(
  completions: CompletionEntry[],
  days: number,
): Map<string, number> {
  const grid = new Map<string, number>();
  const today = startOfDay(new Date());
  for (let i = days - 1; i >= 0; i--) {
    grid.set(toDateStr(subDays(today, i)), 0);
  }
  for (const c of completions) {
    if (grid.has(c.date)) {
      grid.set(c.date, (grid.get(c.date) || 0) + c.count);
    }
  }
  return grid;
}
