/**
 * Types matching `backend/app/schemas/ai.py`:
 *   PomodoroCoachingRequest / PomodoroCoachingResponse.
 *
 * A single endpoint powers three Pomodoro+ surfaces via `trigger`:
 *   - `pre_session`    — about to start a work session
 *   - `break_activity` — during a break
 *   - `session_recap`  — just finished a session or full block
 *
 * Only the fields relevant to a given trigger are consulted server-side;
 * the rest are ignored. Client can safely omit fields it doesn't have.
 */

export type PomodoroCoachingTrigger =
  | 'pre_session'
  | 'break_activity'
  | 'session_recap';

export type BreakType = 'short' | 'long';

export interface PomodoroCoachingTask {
  task_id?: string | null;
  title: string;
  allocated_minutes?: number | null;
}

export interface PomodoroCoachingRequest {
  trigger: PomodoroCoachingTrigger;

  // pre_session
  upcoming_tasks?: PomodoroCoachingTask[] | null;
  session_length_minutes?: number | null;

  // break_activity
  elapsed_minutes?: number | null;
  break_type?: BreakType | null;
  recent_suggestions?: string[] | null;

  // session_recap
  completed_tasks?: PomodoroCoachingTask[] | null;
  started_tasks?: PomodoroCoachingTask[] | null;
  session_duration_minutes?: number | null;
}

export interface PomodoroCoachingResponse {
  message: string;
}
