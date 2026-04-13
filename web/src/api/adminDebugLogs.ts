import apiClient from './client';

export interface DebugLogSummary {
  id: string;
  user_id: number | null;
  user_email: string | null;
  filename: string;
  timestamp: string;
  size_bytes: number;
  device_info: string | null;
  app_version: string | null;
  category: string;
  severity: string;
  status: string;
}

export interface DebugLogDetail {
  id: string;
  user_id: number | null;
  user_email: string | null;
  filename: string;
  timestamp: string;
  content: string | null;
  metadata: Record<string, unknown>;
}

export interface DebugLogStats {
  total_logs: number;
  logs_this_week: number;
  unique_users: number;
  storage_used_bytes: number;
}

export interface PaginatedDebugLogs {
  items: DebugLogSummary[];
  total: number;
  page: number;
  per_page: number;
  total_pages: number;
}

export interface ListDebugLogsParams {
  user_id?: number;
  sort?: 'newest' | 'oldest';
  page?: number;
  per_page?: number;
}

export const adminDebugLogsApi = {
  list(params: ListDebugLogsParams = {}): Promise<PaginatedDebugLogs> {
    return apiClient
      .get('/admin/debug-logs', { params })
      .then((r) => r.data);
  },

  get(logId: string): Promise<DebugLogDetail> {
    return apiClient
      .get(`/admin/debug-logs/${logId}`)
      .then((r) => r.data);
  },

  delete(logId: string): Promise<{ deleted: boolean }> {
    return apiClient
      .delete(`/admin/debug-logs/${logId}`)
      .then((r) => r.data);
  },

  stats(): Promise<DebugLogStats> {
    return apiClient
      .get('/admin/debug-logs/stats')
      .then((r) => r.data);
  },
};
