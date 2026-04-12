import { useEffect, useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Flame,
  Trophy,
  Target,
  Hash,
  Pencil,
} from 'lucide-react';
import {
  format,
  subDays,
  startOfWeek,
  endOfWeek,
  eachDayOfInterval,
  parseISO,
  getDay,
  startOfMonth,
  endOfMonth,
  subMonths,
  isSameMonth,
  isSameDay,
} from 'date-fns';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RTooltip,
  Cell,
} from 'recharts';
import { useHabitStore } from '@/stores/habitStore';
import { buildCompletionGrid } from '@/utils/streaks';
import { Button } from '@/components/ui/Button';
import { Spinner } from '@/components/ui/Spinner';
import { Tooltip } from '@/components/ui/Tooltip';
import { HabitModal } from './HabitModal';
import type { Habit, HabitCompletion } from '@/types/habit';
import type { StreakData } from '@/utils/streaks';

const DAY_NAMES_SHORT = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const DAY_NAMES_FULL = [
  'Sunday',
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
];

export function HabitAnalyticsScreen() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const habitId = Number(id);

  const {
    habits,
    completions,
    fetchHabits,
    fetchHabitWithCompletions,
    getStreakData,
  } = useHabitStore();

  const [loading, setLoading] = useState(true);
  const [editOpen, setEditOpen] = useState(false);

  const habit = habits.find((h) => h.id === habitId);
  const habitCompletions = completions[habitId] || [];
  const streakData = getStreakData(habitId);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        if (habits.length === 0) await fetchHabits();
        await fetchHabitWithCompletions(habitId);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [habitId, fetchHabits, fetchHabitWithCompletions, habits.length]);

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  if (!habit) {
    return (
      <div className="mx-auto max-w-4xl text-center py-16">
        <p className="text-[var(--color-text-secondary)]">Habit not found.</p>
        <Button variant="ghost" className="mt-4" onClick={() => navigate('/habits')}>
          Back to Habits
        </Button>
      </div>
    );
  }

  const habitColor = habit.color || 'var(--color-accent)';

  return (
    <div className="mx-auto max-w-4xl">
      {/* Header */}
      <div className="mb-6">
        <button
          onClick={() => navigate('/habits')}
          className="mb-3 inline-flex items-center gap-1 text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Habits
        </button>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div
              className="flex h-12 w-12 items-center justify-center rounded-full text-xl"
              style={{
                backgroundColor: habitColor + '20',
                color: habitColor,
              }}
            >
              {habit.icon || '🎯'}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
                  {habit.name}
                </h1>
                {streakData && streakData.currentStreak > 0 && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-orange-500/10 px-2 py-0.5 text-sm font-medium text-orange-500">
                    <Flame className="h-4 w-4" />
                    {streakData.currentStreak} day streak
                  </span>
                )}
              </div>
              {streakData && (
                <p className="text-sm text-[var(--color-text-secondary)]">
                  Best streak: {streakData.longestStreak} days
                </p>
              )}
            </div>
          </div>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setEditOpen(true)}
          >
            <Pencil className="h-4 w-4" />
            Edit
          </Button>
        </div>
      </div>

      {/* Stat Cards */}
      {streakData && (
        <StatCards streakData={streakData} habitColor={habitColor} />
      )}

      {/* Contribution Grid */}
      <ContributionGrid
        completions={habitCompletions}
        habitColor={habitColor}
        maxCount={habit.target_count}
      />

      {/* Charts Row */}
      <div className="mt-6 grid gap-6 md:grid-cols-2">
        <WeeklyTrendChart
          completions={habitCompletions}
          habitColor={habitColor}
        />
        <DayOfWeekChart
          completions={habitCompletions}
          habitColor={habitColor}
          streakData={streakData}
        />
      </div>

      {/* Completion Calendar */}
      <CompletionCalendar
        completions={habitCompletions}
        habitColor={habitColor}
        activeDaysJson={habit.active_days_json}
        targetCount={habit.target_count}
      />

      {/* Edit Modal */}
      {editOpen && (
        <HabitModal
          habit={habit}
          onClose={() => setEditOpen(false)}
        />
      )}
    </div>
  );
}

// Stat Cards
function StatCards({
  streakData,
  habitColor,
}: {
  streakData: StreakData;
  habitColor: string;
}) {
  const cards = [
    {
      label: 'Current Streak',
      value: streakData.currentStreak,
      suffix: streakData.currentStreak === 1 ? 'day' : 'days',
      icon: <Flame className="h-5 w-5" />,
      iconColor: '#f97316',
    },
    {
      label: 'Longest Streak',
      value: streakData.longestStreak,
      suffix: streakData.longestStreak === 1 ? 'day' : 'days',
      icon: <Trophy className="h-5 w-5" />,
      iconColor: '#eab308',
    },
    {
      label: '30-Day Rate',
      value: Math.round(streakData.completionRate30Day * 100),
      suffix: '%',
      icon: <Target className="h-5 w-5" />,
      iconColor: habitColor,
    },
    {
      label: 'Total Completions',
      value: streakData.totalCompletions,
      suffix: '',
      icon: <Hash className="h-5 w-5" />,
      iconColor: '#22c55e',
    },
  ];

  return (
    <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
      {cards.map((card) => (
        <div
          key={card.label}
          className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4"
        >
          <div className="mb-2" style={{ color: card.iconColor }}>
            {card.icon}
          </div>
          <p className="text-2xl font-bold text-[var(--color-text-primary)]">
            {card.value}
            {card.suffix && (
              <span className="ml-1 text-sm font-normal text-[var(--color-text-secondary)]">
                {card.suffix}
              </span>
            )}
          </p>
          <p className="text-xs text-[var(--color-text-secondary)]">
            {card.label}
          </p>
        </div>
      ))}
    </div>
  );
}

// Contribution Grid (GitHub-style heatmap)
function ContributionGrid({
  completions,
  habitColor,
  maxCount,
}: {
  completions: HabitCompletion[];
  habitColor: string;
  maxCount: number;
}) {
  const grid = useMemo(
    () =>
      buildCompletionGrid(
        completions.map((c) => ({ date: c.date, count: c.count })),
        84,
      ),
    [completions],
  );

  // Build 12 columns x 7 rows (weeks x days)
  const today = new Date();
  const startDate = subDays(today, 83);

  // Find the Monday on or before startDate
  const startDow = getDay(startDate);
  const adjustedStart = subDays(startDate, startDow === 0 ? 6 : startDow - 1);

  const weeks: { date: Date; dateStr: string; count: number }[][] = [];
  let current = adjustedStart;
  while (current <= today || weeks.length < 12) {
    const week: { date: Date; dateStr: string; count: number }[] = [];
    for (let d = 0; d < 7; d++) {
      const dateStr = format(current, 'yyyy-MM-dd');
      week.push({
        date: new Date(current),
        dateStr,
        count: grid.get(dateStr) || 0,
      });
      current = new Date(current.getTime() + 86400000);
    }
    weeks.push(week);
    if (weeks.length >= 12 && current > today) break;
  }

  // Only take up to 12 weeks
  const displayWeeks = weeks.slice(-12);

  // Month labels
  const monthLabels: { label: string; col: number }[] = [];
  let lastMonth = -1;
  displayWeeks.forEach((week, idx) => {
    const monthNum = week[0].date.getMonth();
    if (monthNum !== lastMonth) {
      monthLabels.push({ label: format(week[0].date, 'MMM'), col: idx });
      lastMonth = monthNum;
    }
  });

  function getCellColor(count: number): string {
    if (count === 0) return 'var(--color-bg-secondary)';
    const intensity = Math.min(count / Math.max(maxCount, 1), 1);
    if (intensity <= 0.25) return habitColor + '40';
    if (intensity <= 0.5) return habitColor + '70';
    if (intensity <= 0.75) return habitColor + 'A0';
    return habitColor;
  }

  return (
    <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
      <h3 className="mb-3 text-sm font-semibold text-[var(--color-text-primary)]">
        Activity
      </h3>
      <div className="overflow-x-auto">
        <div className="inline-flex flex-col gap-1">
          {/* Month labels */}
          <div className="flex gap-1 pl-8">
            {monthLabels.map((m, i) => (
              <div
                key={i}
                className="text-xs text-[var(--color-text-secondary)]"
                style={{
                  marginLeft: i === 0 ? m.col * 16 : undefined,
                  width: i < monthLabels.length - 1
                    ? (monthLabels[i + 1].col - m.col) * 16
                    : undefined,
                }}
              >
                {m.label}
              </div>
            ))}
          </div>

          {/* Grid rows */}
          {[0, 1, 2, 3, 4, 5, 6].map((dayIdx) => (
            <div key={dayIdx} className="flex items-center gap-1">
              <span className="w-6 text-right text-xs text-[var(--color-text-secondary)]">
                {dayIdx % 2 === 0 ? DAY_NAMES_SHORT[dayIdx] : ''}
              </span>
              <div className="flex gap-1">
                {displayWeeks.map((week, weekIdx) => {
                  const cell = week[dayIdx];
                  if (!cell || cell.date > today) {
                    return (
                      <div
                        key={weekIdx}
                        className="h-3 w-3 rounded-sm"
                        style={{ backgroundColor: 'transparent' }}
                      />
                    );
                  }
                  return (
                    <Tooltip
                      key={weekIdx}
                      content={`${format(cell.date, 'MMM d, yyyy')}: ${cell.count} completion${cell.count !== 1 ? 's' : ''}`}
                      delay={100}
                    >
                      <div
                        className="h-3 w-3 rounded-sm transition-colors"
                        style={{
                          backgroundColor: getCellColor(cell.count),
                        }}
                      />
                    </Tooltip>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// Weekly Trend Line Chart
function WeeklyTrendChart({
  completions,
  habitColor,
}: {
  completions: HabitCompletion[];
  habitColor: string;
}) {
  const data = useMemo(() => {
    const today = new Date();
    const weeks: { label: string; completions: number }[] = [];

    for (let i = 11; i >= 0; i--) {
      const weekStart = startOfWeek(subDays(today, i * 7), {
        weekStartsOn: 1,
      });
      const weekEnd = endOfWeek(weekStart, { weekStartsOn: 1 });
      const days = eachDayOfInterval({ start: weekStart, end: weekEnd });
      const dateStrs = new Set(days.map((d) => format(d, 'yyyy-MM-dd')));

      let count = 0;
      for (const c of completions) {
        if (dateStrs.has(c.date)) count += c.count;
      }

      weeks.push({
        label: format(weekStart, 'MMM d'),
        completions: count,
      });
    }

    return weeks;
  }, [completions]);

  return (
    <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
      <h3 className="mb-3 text-sm font-semibold text-[var(--color-text-primary)]">
        Weekly Trend
      </h3>
      <ResponsiveContainer width="100%" height={200}>
        <AreaChart data={data}>
          <CartesianGrid
            strokeDasharray="3 3"
            stroke="var(--color-border)"
            vertical={false}
          />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 10, fill: 'var(--color-text-secondary)' }}
            axisLine={false}
            tickLine={false}
            interval="preserveStartEnd"
          />
          <YAxis
            tick={{ fontSize: 10, fill: 'var(--color-text-secondary)' }}
            axisLine={false}
            tickLine={false}
            allowDecimals={false}
          />
          <RTooltip
            contentStyle={{
              backgroundColor: 'var(--color-bg-card)',
              border: '1px solid var(--color-border)',
              borderRadius: '8px',
              fontSize: 12,
            }}
          />
          <Area
            type="monotone"
            dataKey="completions"
            stroke={habitColor}
            fill={habitColor}
            fillOpacity={0.15}
            strokeWidth={2}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

// Day-of-Week Bar Chart
function DayOfWeekChart({
  completions,
  habitColor,
  streakData,
}: {
  completions: HabitCompletion[];
  habitColor: string;
  streakData: StreakData | null;
}) {
  const data = useMemo(() => {
    const totals = [0, 0, 0, 0, 0, 0, 0]; // Sun-Sat

    for (const c of completions) {
      const day = parseISO(c.date).getDay();
      totals[day] += c.count;
    }

    // Reorder to Mon-Sun
    const reordered = [
      { name: 'Mon', completions: totals[1] },
      { name: 'Tue', completions: totals[2] },
      { name: 'Wed', completions: totals[3] },
      { name: 'Thu', completions: totals[4] },
      { name: 'Fri', completions: totals[5] },
      { name: 'Sat', completions: totals[6] },
      { name: 'Sun', completions: totals[0] },
    ];

    return reordered;
  }, [completions]);

  const maxVal = Math.max(...data.map((d) => d.completions), 1);
  const bestDayName = streakData?.bestDay;
  const worstDayName = streakData?.worstDay;

  return (
    <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
      <h3 className="mb-3 text-sm font-semibold text-[var(--color-text-primary)]">
        Day of Week
      </h3>
      <ResponsiveContainer width="100%" height={200}>
        <BarChart data={data}>
          <CartesianGrid
            strokeDasharray="3 3"
            stroke="var(--color-border)"
            vertical={false}
          />
          <XAxis
            dataKey="name"
            tick={{ fontSize: 10, fill: 'var(--color-text-secondary)' }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tick={{ fontSize: 10, fill: 'var(--color-text-secondary)' }}
            axisLine={false}
            tickLine={false}
            allowDecimals={false}
          />
          <RTooltip
            contentStyle={{
              backgroundColor: 'var(--color-bg-card)',
              border: '1px solid var(--color-border)',
              borderRadius: '8px',
              fontSize: 12,
            }}
          />
          <Bar dataKey="completions" radius={[4, 4, 0, 0]}>
            {data.map((entry) => {
              // Map short names to full names for comparison
              const fullNameMap: Record<string, string> = {
                Mon: 'Monday',
                Tue: 'Tuesday',
                Wed: 'Wednesday',
                Thu: 'Thursday',
                Fri: 'Friday',
                Sat: 'Saturday',
                Sun: 'Sunday',
              };
              const fullName = fullNameMap[entry.name];
              let color = habitColor;
              if (fullName === bestDayName && entry.completions > 0)
                color = '#22c55e';
              if (
                fullName === worstDayName &&
                bestDayName !== worstDayName
              )
                color = '#ef4444';
              return (
                <Cell key={entry.name} fill={color} fillOpacity={0.8} />
              );
            })}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <div className="mt-2 flex justify-center gap-4 text-xs text-[var(--color-text-secondary)]">
        <span className="flex items-center gap-1">
          <span className="h-2 w-2 rounded-full bg-[#22c55e]" /> Best
        </span>
        <span className="flex items-center gap-1">
          <span className="h-2 w-2 rounded-full bg-[#ef4444]" /> Worst
        </span>
      </div>
    </div>
  );
}

// Completion Calendar (last 3 months)
function CompletionCalendar({
  completions,
  habitColor,
  activeDaysJson,
  targetCount,
}: {
  completions: HabitCompletion[];
  habitColor: string;
  activeDaysJson: string | null;
  targetCount: number;
}) {
  const today = new Date();

  const activeDays = useMemo(() => {
    if (!activeDaysJson) return null;
    try {
      const parsed = JSON.parse(activeDaysJson);
      return Array.isArray(parsed) ? (parsed as number[]) : null;
    } catch {
      return null;
    }
  }, [activeDaysJson]);

  const completionSet = useMemo(() => {
    const set = new Map<string, number>();
    for (const c of completions) {
      set.set(c.date, (set.get(c.date) || 0) + c.count);
    }
    return set;
  }, [completions]);

  const months = useMemo(() => {
    const result: Date[] = [];
    for (let i = 2; i >= 0; i--) {
      result.push(subMonths(today, i));
    }
    return result;
  }, []);

  function isActiveDay(date: Date): boolean {
    if (!activeDays || activeDays.length === 0) return true;
    const jsDay = date.getDay();
    const isoDay = jsDay === 0 ? 7 : jsDay;
    return activeDays.includes(isoDay);
  }

  return (
    <div className="mt-6 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
      <h3 className="mb-3 text-sm font-semibold text-[var(--color-text-primary)]">
        Completion Calendar
      </h3>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {months.map((month) => (
          <MiniCalendar
            key={format(month, 'yyyy-MM')}
            month={month}
            today={today}
            completionSet={completionSet}
            habitColor={habitColor}
            isActiveDay={isActiveDay}
            targetCount={targetCount}
          />
        ))}
      </div>
    </div>
  );
}

function MiniCalendar({
  month,
  today,
  completionSet,
  habitColor,
  isActiveDay,
  targetCount,
}: {
  month: Date;
  today: Date;
  completionSet: Map<string, number>;
  habitColor: string;
  isActiveDay: (date: Date) => boolean;
  targetCount: number;
}) {
  const monthStart = startOfMonth(month);
  const monthEnd = endOfMonth(month);
  const days = eachDayOfInterval({ start: monthStart, end: monthEnd });

  // Offset for first day (Monday = 0)
  const firstDow = getDay(monthStart);
  const offset = firstDow === 0 ? 6 : firstDow - 1;

  return (
    <div>
      <p className="mb-2 text-center text-xs font-medium text-[var(--color-text-secondary)]">
        {format(month, 'MMMM yyyy')}
      </p>
      <div className="grid grid-cols-7 gap-0.5">
        {/* Day headers */}
        {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d, i) => (
          <div
            key={i}
            className="flex h-6 items-center justify-center text-[10px] text-[var(--color-text-secondary)]"
          >
            {d}
          </div>
        ))}
        {/* Offset cells */}
        {Array.from({ length: offset }).map((_, i) => (
          <div key={`empty-${i}`} className="h-6" />
        ))}
        {/* Day cells */}
        {days.map((day) => {
          const dateStr = format(day, 'yyyy-MM-dd');
          const count = completionSet.get(dateStr) || 0;
          const completed = count >= targetCount;
          const active = isActiveDay(day);
          const isFuture = day > today;
          const isToday = isSameDay(day, today);
          const missed = !isFuture && active && !completed;

          return (
            <div
              key={dateStr}
              className={`flex h-6 items-center justify-center text-[10px] rounded-sm ${
                isToday
                  ? 'ring-1 ring-[var(--color-text-primary)]'
                  : ''
              } ${isFuture ? 'text-[var(--color-text-secondary)]/30' : ''}`}
            >
              <span className="relative">
                {day.getDate()}
                {completed && !isFuture && (
                  <span
                    className="absolute -bottom-1 left-1/2 h-1 w-1 -translate-x-1/2 rounded-full"
                    style={{ backgroundColor: habitColor }}
                  />
                )}
                {missed && !isFuture && day < today && (
                  <span className="absolute -bottom-1 left-1/2 h-1 w-1 -translate-x-1/2 rounded-full bg-red-400/50" />
                )}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
