import apiClient from './client';
import type { DashboardSummary, AnalyticsSummary } from '@/types/api';

export const dashboardApi = {
  getSummary(): Promise<DashboardSummary> {
    return apiClient.get('/dashboard/summary').then((r) => r.data);
  },

  getAnalyticsSummary(): Promise<AnalyticsSummary> {
    return apiClient.get('/analytics/summary').then((r) => r.data);
  },
};
