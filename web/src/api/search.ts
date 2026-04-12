import apiClient from './client';
import type { Task } from '@/types/task';

export interface SearchResult {
  tasks: Task[];
}

export const searchApi = {
  search(query: string): Promise<Task[]> {
    return apiClient
      .get('/search', { params: { q: query } })
      .then((r) => r.data);
  },
};
