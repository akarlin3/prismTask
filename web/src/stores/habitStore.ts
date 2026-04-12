import { create } from 'zustand';
import type { Habit, HabitCreate, HabitUpdate } from '@/types/habit';
import { habitsApi } from '@/api/habits';

interface HabitState {
  habits: Habit[];
  selectedHabit: Habit | null;
  isLoading: boolean;
  error: string | null;

  fetchHabits: () => Promise<void>;
  createHabit: (data: HabitCreate) => Promise<Habit>;
  updateHabit: (habitId: number, data: HabitUpdate) => Promise<Habit>;
  deleteHabit: (habitId: number) => Promise<void>;
  setSelectedHabit: (habit: Habit | null) => void;
  clearError: () => void;
}

export const useHabitStore = create<HabitState>((set) => ({
  habits: [],
  selectedHabit: null,
  isLoading: false,
  error: null,

  fetchHabits: async () => {
    set({ isLoading: true, error: null });
    try {
      const habits = await habitsApi.list();
      set({ habits, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  createHabit: async (data) => {
    const habit = await habitsApi.create(data);
    set((state) => ({ habits: [...state.habits, habit] }));
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
    set((state) => ({
      habits: state.habits.filter((h) => h.id !== habitId),
    }));
  },

  setSelectedHabit: (habit) => set({ selectedHabit: habit }),
  clearError: () => set({ error: null }),
}));
