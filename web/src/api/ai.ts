import apiClient from './client';
import type { EisenhowerResponse } from '@/types/api';

export interface PomodoroRequest {
  available_minutes: number;
  session_length?: number;
  break_length?: number;
  long_break_length?: number;
  focus_preference?: 'balanced' | 'deep_work' | 'quick_wins' | 'deadline_driven';
}

export interface PomodoroSessionTask {
  task_id: number;
  title: string;
  allocated_minutes: number;
}

export interface PomodoroSession {
  session_number: number;
  tasks: PomodoroSessionTask[];
  rationale: string;
}

export interface SkippedTask {
  task_id: number;
  reason: string;
}

export interface PomodoroResponse {
  sessions: PomodoroSession[];
  total_sessions: number;
  total_work_minutes: number;
  total_break_minutes: number;
  skipped_tasks: SkippedTask[];
}

export const aiApi = {
  eisenhowerCategorize(taskIds?: number[]): Promise<EisenhowerResponse> {
    return apiClient
      .post('/ai/eisenhower', { task_ids: taskIds || null })
      .then((r) => r.data);
  },

  pomodoroPlan(data: PomodoroRequest): Promise<PomodoroResponse> {
    return apiClient
      .post('/ai/pomodoro-plan', data)
      .then((r) => r.data);
  },
};
