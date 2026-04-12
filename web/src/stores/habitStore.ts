import { create } from 'zustand';
import { format } from 'date-fns';
import type {
  Habit,
  HabitCreate,
  HabitUpdate,
  HabitCompletion,
  HabitStats,
} from '@/types/habit';
import { habitsApi } from '@/api/habits';
import { calculateStreaks, type StreakData } from '@/utils/streaks';

interface HabitState {
  habits: Habit[];
  /** habitId → completions list */
  completions: Record<number, HabitCompletion[]>;
  /** habitId → stats from backend */
  stats: Record<number, HabitStats>;
  selectedHabit: Habit | null;
  isLoading: boolean;
  error: string | null;

  fetchHabits: () => Promise<void>;
  fetchHabitWithCompletions: (habitId: number) => Promise<void>;
  fetchHabitStats: (habitId: number) => Promise<HabitStats>;
  createHabit: (data: HabitCreate) => Promise<Habit>;
  updateHabit: (habitId: number, data: HabitUpdate) => Promise<Habit>;
  deleteHabit: (habitId: number) => Promise<void>;
  toggleCompletion: (habitId: number, date: string) => Promise<void>;
  setSelectedHabit: (habit: Habit | null) => void;
  clearError: () => void;

  // Computed helpers
  getStreakData: (habitId: number) => StreakData | null;
  isTodayCompleted: (habitId: number) => boolean;
  getTodayCount: (habitId: number) => number;
  getTodayProgress: () => { completed: number; total: number };
  getWeekCompletions: (habitId: number) => boolean[];
}

function parseActiveDays(json: string | null): number[] | null {
  if (!json) return null;
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function todayStr(): string {
  return format(new Date(), 'yyyy-MM-dd');
}

export const useHabitStore = create<HabitState>((set, get) => ({
  habits: [],
  completions: {},
  stats: {},
  selectedHabit: null,
  isLoading: false,
  error: null,

  fetchHabits: async () => {
    set({ isLoading: true, error: null });
    try {
      const habits = await habitsApi.list();
      set({ habits, isLoading: false });

      // Fetch completions for each habit in parallel
      await Promise.all(
        habits.map((h) => get().fetchHabitWithCompletions(h.id)),
      );
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  fetchHabitWithCompletions: async (habitId) => {
    try {
      const habitData = await habitsApi.get(habitId);
      const completionsList = habitData.completions || [];
      set((state) => ({
        completions: {
          ...state.completions,
          [habitId]: completionsList,
        },
      }));
    } catch {
      // Silently fail for individual habit fetches
    }
  },

  fetchHabitStats: async (habitId) => {
    const statsData = await habitsApi.getStats(habitId);
    set((state) => ({
      stats: { ...state.stats, [habitId]: statsData },
    }));
    return statsData;
  },

  createHabit: async (data) => {
    const habit = await habitsApi.create(data);
    set((state) => ({
      habits: [...state.habits, habit],
      completions: { ...state.completions, [habit.id]: [] },
    }));
    return habit;
  },

  updateHabit: async (habitId, data) => {
    const updated = await habitsApi.update(habitId, data);
    set((state) => ({
      habits: state.habits.map((h) => (h.id === habitId ? updated : h)),
    }));
    return updated;
  },

  deleteHabit: async (habitId) => {
    await habitsApi.delete(habitId);
    set((state) => {
      const newCompletions = { ...state.completions };
      delete newCompletions[habitId];
      const newStats = { ...state.stats };
      delete newStats[habitId];
      return {
        habits: state.habits.filter((h) => h.id !== habitId),
        completions: newCompletions,
        stats: newStats,
      };
    });
  },

  toggleCompletion: async (habitId, date) => {
    const state = get();
    const existing = (state.completions[habitId] || []).find(
      (c) => c.date === date,
    );

    // Optimistic update
    if (existing && existing.count > 0) {
      // Remove completion optimistically
      set((s) => ({
        completions: {
          ...s.completions,
          [habitId]: (s.completions[habitId] || []).filter(
            (c) => c.date !== date,
          ),
        },
      }));
    } else {
      // Add completion optimistically
      const optimistic: HabitCompletion = {
        id: -Date.now(),
        habit_id: habitId,
        date,
        count: 1,
        created_at: new Date().toISOString(),
      };
      set((s) => ({
        completions: {
          ...s.completions,
          [habitId]: [...(s.completions[habitId] || []), optimistic],
        },
      }));
    }

    try {
      const result = await habitsApi.toggleCompletion(habitId, {
        date,
        count: 1,
      });

      // Reconcile with server response
      if (result.count === 0) {
        // Server confirmed deletion
        set((s) => ({
          completions: {
            ...s.completions,
            [habitId]: (s.completions[habitId] || []).filter(
              (c) => c.date !== date,
            ),
          },
        }));
      } else {
        // Server confirmed creation — replace optimistic entry
        set((s) => ({
          completions: {
            ...s.completions,
            [habitId]: [
              ...(s.completions[habitId] || []).filter(
                (c) => c.date !== date,
              ),
              result,
            ],
          },
        }));
      }
    } catch {
      // Revert on error by re-fetching
      await get().fetchHabitWithCompletions(habitId);
    }
  },

  setSelectedHabit: (habit) => set({ selectedHabit: habit }),
  clearError: () => set({ error: null }),

  getStreakData: (habitId) => {
    const state = get();
    const habit = state.habits.find((h) => h.id === habitId);
    const completions = state.completions[habitId];
    if (!habit || !completions) return null;

    return calculateStreaks(
      completions.map((c) => ({ date: c.date, count: c.count })),
      habit.frequency,
      parseActiveDays(habit.active_days_json),
      habit.target_count,
    );
  },

  isTodayCompleted: (habitId) => {
    const state = get();
    const habit = state.habits.find((h) => h.id === habitId);
    const completions = state.completions[habitId] || [];
    const today = todayStr();
    const todayCompletion = completions.find((c) => c.date === today);
    const count = todayCompletion?.count || 0;
    return count >= (habit?.target_count || 1);
  },

  getTodayCount: (habitId) => {
    const completions = get().completions[habitId] || [];
    const today = todayStr();
    const todayCompletion = completions.find((c) => c.date === today);
    return todayCompletion?.count || 0;
  },

  getTodayProgress: () => {
    const state = get();
    const activeHabits = state.habits.filter((h) => h.is_active);
    const today = todayStr();
    const todayDate = new Date();
    let total = 0;
    let completed = 0;

    for (const habit of activeHabits) {
      // Check if today is an active day for this habit
      const activeDays = parseActiveDays(habit.active_days_json);
      if (activeDays && activeDays.length > 0) {
        const jsDay = todayDate.getDay();
        const isoDay = jsDay === 0 ? 7 : jsDay;
        if (!activeDays.includes(isoDay)) continue;
      }
      // Weekly habits count as "due" only once a week
      if (habit.frequency === 'weekly') {
        // For weekly, always count it
        total++;
        const weekCompletions = (state.completions[habit.id] || []).reduce(
          (sum, c) => {
            const d = new Date(c.date + 'T00:00:00');
            const startOfCurrentWeek = new Date(todayDate);
            startOfCurrentWeek.setDate(
              todayDate.getDate() - ((todayDate.getDay() + 6) % 7),
            );
            startOfCurrentWeek.setHours(0, 0, 0, 0);
            if (d >= startOfCurrentWeek && d <= todayDate) return sum + c.count;
            return sum;
          },
          0,
        );
        if (weekCompletions >= habit.target_count) completed++;
      } else {
        total++;
        const todayCompletion = (state.completions[habit.id] || []).find(
          (c) => c.date === today,
        );
        if ((todayCompletion?.count || 0) >= habit.target_count) completed++;
      }
    }

    return { completed, total };
  },

  getWeekCompletions: (habitId) => {
    const completions = get().completions[habitId] || [];
    const today = new Date();
    // Build Mon-Sun for the current week
    const dayOffset = (today.getDay() + 6) % 7; // Monday=0
    const result: boolean[] = [];
    for (let i = 0; i < 7; i++) {
      const d = new Date(today);
      d.setDate(today.getDate() - dayOffset + i);
      const dateStr = format(d, 'yyyy-MM-dd');
      result.push(completions.some((c) => c.date === dateStr && c.count > 0));
    }
    return result;
  },
}));
