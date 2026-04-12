export type HabitFrequency = 'daily' | 'weekly';

export interface Habit {
  id: string;
  user_id: string;
  name: string;
  description: string | null;
  icon: string | null;
  color: string | null;
  category: string | null;
  frequency: HabitFrequency;
  target_count: number;
  active_days_json: string | null;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface HabitCreate {
  name: string;
  description?: string;
  icon?: string;
  color?: string;
  category?: string;
  frequency?: HabitFrequency;
  target_count?: number;
  active_days_json?: string;
}

export interface HabitUpdate {
  name?: string;
  description?: string;
  icon?: string;
  color?: string;
  category?: string;
  frequency?: HabitFrequency;
  target_count?: number;
  active_days_json?: string;
  is_active?: boolean;
}

export interface HabitCompletion {
  id: string;
  habit_id: string;
  date: string;
  count: number;
  created_at: string;
}

export interface HabitCompletionCreate {
  date: string;
  count?: number;
}

export interface HabitWithCompletions extends Habit {
  completions?: HabitCompletion[];
}

export interface HabitStats {
  habit_id: string;
  current_streak: number;
  longest_streak: number;
  total_completions: number;
  completion_rate: number;
  completions_this_week: number;
}
