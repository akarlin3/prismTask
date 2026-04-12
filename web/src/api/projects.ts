import apiClient from './client';
import type {
  Project,
  ProjectCreate,
  ProjectUpdate,
  ProjectDetail,
} from '@/types/project';

export const projectsApi = {
  getByGoal(goalId: number): Promise<Project[]> {
    return apiClient
      .get(`/goals/${goalId}/projects`)
      .then((r) => r.data);
  },

  get(projectId: number): Promise<ProjectDetail> {
    return apiClient.get(`/projects/${projectId}`).then((r) => r.data);
  },

  create(goalId: number, data: ProjectCreate): Promise<Project> {
    return apiClient
      .post(`/goals/${goalId}/projects`, data)
      .then((r) => r.data);
  },

  update(projectId: number, data: ProjectUpdate): Promise<Project> {
    return apiClient
      .patch(`/projects/${projectId}`, data)
      .then((r) => r.data);
  },

  delete(projectId: number): Promise<void> {
    return apiClient.delete(`/projects/${projectId}`).then(() => undefined);
  },
};
