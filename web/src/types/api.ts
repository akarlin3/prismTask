import type { Task } from './task';

export interface ApiError {
  detail: string;
  status_code?: number;
}

export interface DashboardSummary {
  total_tasks: number;
  completed_tasks: number;
  overdue_tasks: number;
  today_tasks: number;
  upcoming_tasks: number;
  completion_rate: number;
}

export interface TaskListResponse {
  tasks: Task[];
  count: number;
}

export interface NLPParseRequest {
  text: string;
}

export interface NLPParseResult {
  title: string;
  project_suggestion: string | null;
  due_date: string | null;
  priority: number | null;
  parent_task_suggestion: string | null;
  confidence: number;
  suggestions: string[];
  needs_confirmation: boolean;
}

export interface EisenhowerCategorization {
  task_id: number;
  quadrant: 'Q1' | 'Q2' | 'Q3' | 'Q4';
  reason: string;
}

export interface EisenhowerResponse {
  categorizations: EisenhowerCategorization[];
  summary: {
    Q1?: number;
    Q2?: number;
    Q3?: number;
    Q4?: number;
  };
}

export interface AnalyticsSummary {
  today: { completed: number; remaining: number; score: number };
  this_week: { completed: number; remaining: number; score: number; trend: string };
  this_month: { completed: number; remaining: number; score: number };
  streaks: { current_productive_days: number; longest_productive_days: number };
  habits: { completion_rate_7d: number; completion_rate_30d: number };
}
