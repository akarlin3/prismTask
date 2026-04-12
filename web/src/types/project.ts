export type ProjectStatus = 'active' | 'completed' | 'on_hold' | 'archived';

export interface Project {
  id: number;
  goal_id: number;
  user_id: number;
  title: string;
  description: string | null;
  status: ProjectStatus;
  due_date: string | null;
  sort_order: number;
  created_at: string;
  updated_at: string;
}

export interface ProjectCreate {
  title: string;
  description?: string;
  status?: ProjectStatus;
  due_date?: string;
  sort_order?: number;
}

export interface ProjectUpdate {
  title?: string;
  description?: string;
  status?: ProjectStatus;
  due_date?: string;
  sort_order?: number;
}

export interface ProjectDetail extends Project {
  tasks?: Array<{
    id: number;
    title: string;
    status: string;
    priority: number;
    due_date: string | null;
    sort_order: number;
    depth: number;
    created_at: string;
  }>;
}

export interface ProjectMember {
  id: number;
  user_id: number;
  display_name: string | null;
  email: string;
  avatar_url: string | null;
  role: 'owner' | 'editor' | 'viewer';
  joined_at: string;
}
