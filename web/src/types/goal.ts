export type GoalStatus = 'active' | 'achieved' | 'archived';

export interface Goal {
  id: number;
  user_id: number;
  title: string;
  description: string | null;
  status: GoalStatus;
  target_date: string | null;
  color: string | null;
  sort_order: number;
  created_at: string;
  updated_at: string;
}

export interface GoalCreate {
  title: string;
  description?: string;
  status?: GoalStatus;
  target_date?: string;
  color?: string;
  sort_order?: number;
}

export interface GoalUpdate {
  title?: string;
  description?: string;
  status?: GoalStatus;
  target_date?: string;
  color?: string;
  sort_order?: number;
}

export interface GoalDetail extends Goal {
  projects?: Array<{
    id: number;
    title: string;
    description: string | null;
    status: string;
    due_date: string | null;
    sort_order: number;
    created_at: string;
    updated_at: string;
  }>;
}
