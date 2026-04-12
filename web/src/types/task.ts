export type TaskStatus = 'todo' | 'in_progress' | 'done' | 'cancelled';
export type TaskPriority = 1 | 2 | 3 | 4; // 1=urgent, 2=high, 3=medium, 4=low

export interface Task {
  id: number;
  project_id: number;
  user_id: number;
  parent_id: number | null;
  title: string;
  description: string | null;
  notes: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  due_date: string | null;
  due_time: string | null;
  planned_date: string | null;
  completed_at: string | null;
  urgency_score: number;
  recurrence_json: string | null;
  eisenhower_quadrant: string | null;
  eisenhower_updated_at: string | null;
  estimated_duration: number | null;
  actual_duration: number | null;
  sort_order: number;
  depth: number;
  created_at: string;
  updated_at: string;
  subtasks?: Task[];
  tags?: import('./tag').Tag[];
}

export interface TaskCreate {
  title: string;
  description?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  due_date?: string;
  sort_order?: number;
  recurrence_json?: string;
  estimated_duration?: number;
}

export interface TaskUpdate {
  title?: string;
  description?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  due_date?: string;
  sort_order?: number;
  eisenhower_quadrant?: string;
  recurrence_json?: string;
  estimated_duration?: number;
}

export interface SubtaskCreate {
  title: string;
  description?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  due_date?: string;
  sort_order?: number;
}

export interface RecurrenceRule {
  type: 'daily' | 'weekly' | 'biweekly' | 'monthly' | 'yearly' | 'weekdays' | 'custom';
  interval?: number;
  days_of_week?: number[];
  days_of_month?: number[];
  end_date?: string;
  after_completion?: boolean;
  end_after_count?: number;
  occurrence_count?: number;
  skip_weekends?: boolean;
}
