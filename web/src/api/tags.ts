import apiClient from './client';
import type { Tag, TagCreate, TagUpdate } from '@/types/tag';

export const tagsApi = {
  list(): Promise<Tag[]> {
    return apiClient.get('/tags').then((r) => r.data);
  },

  create(data: TagCreate): Promise<Tag> {
    return apiClient.post('/tags', data).then((r) => r.data);
  },

  update(tagId: number, data: TagUpdate): Promise<Tag> {
    return apiClient.patch(`/tags/${tagId}`, data).then((r) => r.data);
  },

  delete(tagId: number): Promise<void> {
    return apiClient.delete(`/tags/${tagId}`).then(() => undefined);
  },
};
