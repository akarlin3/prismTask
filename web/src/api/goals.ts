import apiClient from './client';
import type { Goal, GoalCreate, GoalUpdate, GoalDetail } from '@/types/goal';

export const goalsApi = {
  list(): Promise<Goal[]> {
    return apiClient.get('/goals').then((r) => r.data);
  },

  get(goalId: number): Promise<GoalDetail> {
    return apiClient.get(`/goals/${goalId}`).then((r) => r.data);
  },

  create(data: GoalCreate): Promise<Goal> {
    return apiClient.post('/goals', data).then((r) => r.data);
  },

  update(goalId: number, data: GoalUpdate): Promise<Goal> {
    return apiClient.patch(`/goals/${goalId}`, data).then((r) => r.data);
  },

  delete(goalId: number): Promise<void> {
    return apiClient.delete(`/goals/${goalId}`).then(() => undefined);
  },
};
