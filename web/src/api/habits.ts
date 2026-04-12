import apiClient from './client';
import type {
  Habit,
  HabitCreate,
  HabitUpdate,
  HabitCompletion,
  HabitCompletionCreate,
  HabitWithCompletions,
  HabitStats,
} from '@/types/habit';

export const habitsApi = {
  list(): Promise<Habit[]> {
    return apiClient.get('/habits').then((r) => r.data);
  },

  get(habitId: number): Promise<HabitWithCompletions> {
    return apiClient.get(`/habits/${habitId}`).then((r) => r.data);
  },

  create(data: HabitCreate): Promise<Habit> {
    return apiClient.post('/habits', data).then((r) => r.data);
  },

  update(habitId: number, data: HabitUpdate): Promise<Habit> {
    return apiClient.patch(`/habits/${habitId}`, data).then((r) => r.data);
  },

  delete(habitId: number): Promise<void> {
    return apiClient.delete(`/habits/${habitId}`).then(() => undefined);
  },

  toggleCompletion(
    habitId: number,
    data: HabitCompletionCreate,
  ): Promise<HabitCompletion> {
    return apiClient
      .post(`/habits/${habitId}/complete`, data)
      .then((r) => r.data);
  },

  getStats(habitId: number): Promise<HabitStats> {
    return apiClient
      .get(`/habits/${habitId}/stats`)
      .then((r) => r.data);
  },
};
