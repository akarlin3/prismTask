/**
 * Types matching `backend/app/schemas/analytics.py`.
 *
 * Wired endpoints on web (slice 4):
 *   - GET /analytics/summary          → AnalyticsSummaryResponse
 *   - GET /analytics/productivity-score → ProductivityScoreResponse
 *   - GET /analytics/time-tracking    → TimeTrackingResponse
 *   - GET /analytics/habit-correlations → HabitCorrelationResponse
 *
 * NOT wired: GET /analytics/project-progress — it requires an integer
 * Postgres project_id, but web projects live in Firestore with string
 * doc IDs. Wiring this properly needs either a backend change or a
 * cross-reference resolver and is out of scope for slice 4.
 */

// ── Productivity score ───────────────────────────────────────────

export interface ScoreBreakdown {
  task_completion: number;
  on_time: number;
  habit_completion: number;
  estimation_accuracy: number;
}

export interface DailyScore {
  /** ISO date `YYYY-MM-DD`. */
  date: string;
  score: number;
  breakdown: ScoreBreakdown;
}

export interface BestWorstDay {
  date: string;
  score: number;
}

export type ProductivityTrend = 'improving' | 'declining' | 'stable';

export interface ProductivityScoreResponse {
  scores: DailyScore[];
  average_score: number;
  trend: ProductivityTrend;
  best_day: BestWorstDay | null;
  worst_day: BestWorstDay | null;
}

// ── Time tracking ─────────────────────────────────────────────────

export type TimeTrackingGroupBy = 'project' | 'tag' | 'priority' | 'day';

export interface TimeTrackingEntry {
  group: string;
  total_minutes: number;
  task_count: number;
  avg_minutes_per_task: number;
  estimated_total: number;
  accuracy_pct: number;
}

export interface TimeTrackingResponse {
  entries: TimeTrackingEntry[];
  total_tracked_minutes: number;
  total_estimated_minutes: number;
  overall_accuracy_pct: number;
  most_time_consuming_project: string | null;
  most_accurate_estimates: string | null;
}

// ── Habit correlations ────────────────────────────────────────────

export type CorrelationDirection = 'positive' | 'negative' | 'neutral';

export interface HabitCorrelation {
  habit: string;
  done_productivity: number;
  not_done_productivity: number;
  correlation: CorrelationDirection;
  interpretation: string;
}

export interface HabitCorrelationResponse {
  correlations: HabitCorrelation[];
  top_insight: string;
  recommendation: string;
}

// ── Summary ──────────────────────────────────────────────────────

export interface TodaySummary {
  completed: number;
  remaining: number;
  score: number;
}

export interface WeekSummary {
  completed: number;
  remaining: number;
  score: number;
  trend: string;
}

export interface MonthSummary {
  completed: number;
  remaining: number;
  score: number;
}

export interface StreakSummary {
  current_productive_days: number;
  longest_productive_days: number;
}

export interface HabitSummaryBucket {
  completion_rate_7d: number;
  completion_rate_30d: number;
}

export interface AnalyticsSummaryResponse {
  today: TodaySummary;
  this_week: WeekSummary;
  this_month: MonthSummary;
  streaks: StreakSummary;
  habits: HabitSummaryBucket;
}

// ── Query param helpers ──────────────────────────────────────────

export interface ProductivityScoreParams {
  period?: 'daily' | 'weekly' | 'monthly';
  start_date?: string;
  end_date?: string;
}

export interface TimeTrackingParams {
  start_date?: string;
  end_date?: string;
  group_by?: TimeTrackingGroupBy;
}
