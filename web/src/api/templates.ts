import apiClient from './client';
import type {
  TaskTemplate,
  TemplateCreate,
  TemplateUpdate,
  TemplateUseRequest,
  TemplateUseResponse,
} from '@/types/template';

export const templatesApi = {
  list(category?: string, sortBy?: string): Promise<TaskTemplate[]> {
    const params: Record<string, string> = {};
    if (category) params.category = category;
    if (sortBy) params.sort_by = sortBy;
    return apiClient.get('/templates', { params }).then((r) => r.data);
  },

  get(id: number): Promise<TaskTemplate> {
    return apiClient.get(`/templates/${id}`).then((r) => r.data);
  },

  create(data: TemplateCreate): Promise<TaskTemplate> {
    return apiClient.post('/templates', data).then((r) => r.data);
  },

  createFromTask(taskId: number): Promise<TaskTemplate> {
    return apiClient.post(`/templates/from-task/${taskId}`).then((r) => r.data);
  },

  update(id: number, data: TemplateUpdate): Promise<TaskTemplate> {
    return apiClient.patch(`/templates/${id}`, data).then((r) => r.data);
  },

  delete(id: number): Promise<void> {
    return apiClient.delete(`/templates/${id}`).then(() => undefined);
  },

  use(id: number, data?: TemplateUseRequest): Promise<TemplateUseResponse> {
    return apiClient.post(`/templates/${id}/use`, data || {}).then((r) => r.data);
  },
};
