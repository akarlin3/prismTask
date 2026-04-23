import apiClient from './client';
import type {
  ProductivityScoreResponse,
  ProductivityScoreParams,
  TimeTrackingResponse,
  TimeTrackingParams,
  HabitCorrelationResponse,
} from '@/types/analytics';

/**
 * Backend analytics endpoints. The existing `dashboardApi.getAnalyticsSummary`
 * covers `/analytics/summary` — this module adds the three additional
 * endpoints the dashboard needs.
 *
 * Not wired here: `/analytics/project-progress`. That endpoint takes an
 * integer Postgres project_id, but the web client stores projects in
 * Firestore with string doc IDs. Wiring it needs either a backend change
 * (accept Firestore IDs) or a resolver mapping — out of scope for the
 * initial analytics slice.
 */
export const analyticsApi = {
  productivityScore(
    params: ProductivityScoreParams = {},
  ): Promise<ProductivityScoreResponse> {
    return apiClient
      .get('/analytics/productivity-score', { params })
      .then((r) => r.data);
  },

  timeTracking(
    params: TimeTrackingParams = {},
  ): Promise<TimeTrackingResponse> {
    return apiClient
      .get('/analytics/time-tracking', { params })
      .then((r) => r.data);
  },

  habitCorrelations(): Promise<HabitCorrelationResponse> {
    return apiClient.get('/analytics/habit-correlations').then((r) => r.data);
  },
};
