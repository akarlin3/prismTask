import apiClient from './client';
import type { Task, TaskCreate, TaskUpdate, SubtaskCreate } from '@/types/task';

export const tasksApi = {
  getByProject(projectId: number): Promise<Task[]> {
    return apiClient
      .get(`/projects/${projectId}/tasks`)
      .then((r) => r.data);
  },

  get(taskId: number): Promise<Task> {
    return apiClient.get(`/tasks/${taskId}`).then((r) => r.data);
  },

  create(projectId: number, data: TaskCreate): Promise<Task> {
    return apiClient
      .post(`/projects/${projectId}/tasks`, data)
      .then((r) => r.data);
  },

  update(taskId: number, data: TaskUpdate): Promise<Task> {
    return apiClient.patch(`/tasks/${taskId}`, data).then((r) => r.data);
  },

  delete(taskId: number): Promise<void> {
    return apiClient.delete(`/tasks/${taskId}`).then(() => undefined);
  },

  createSubtask(taskId: number, data: SubtaskCreate): Promise<Task> {
    return apiClient
      .post(`/tasks/${taskId}/subtasks`, data)
      .then((r) => r.data);
  },

  getToday(): Promise<Task[]> {
    return apiClient.get('/tasks/today').then((r) => r.data);
  },

  getOverdue(): Promise<Task[]> {
    return apiClient.get('/tasks/overdue').then((r) => r.data);
  },

  getUpcoming(days?: number): Promise<Task[]> {
    const params = days ? { days } : {};
    return apiClient.get('/tasks/upcoming', { params }).then((r) => r.data);
  },
};
