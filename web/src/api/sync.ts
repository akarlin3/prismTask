import apiClient from './client';

export interface SyncPushPayload {
  tasks?: unknown[];
  projects?: unknown[];
  habits?: unknown[];
  tags?: unknown[];
}

export interface SyncPullResponse {
  tasks: unknown[];
  projects: unknown[];
  habits: unknown[];
  tags: unknown[];
  last_sync: string;
}

export const syncApi = {
  push(data: SyncPushPayload): Promise<void> {
    return apiClient.post('/sync/push', data).then(() => undefined);
  },

  pull(since?: string): Promise<SyncPullResponse> {
    const params = since ? { since } : {};
    return apiClient.get('/sync/pull', { params }).then((r) => r.data);
  },
};
