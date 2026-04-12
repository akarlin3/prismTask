export type ProjectStatus = 'active' | 'completed' | 'on_hold' | 'archived';

export interface Project {
  id: string;
  goal_id: string;
  user_id: string;
  title: string;
  description: string | null;
  status: ProjectStatus;
  due_date: string | null;
  color: string;
  icon: string;
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
    id: string;
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
  id: string;
  user_id: string;
  display_name: string | null;
  email: string;
  avatar_url: string | null;
  role: 'owner' | 'editor' | 'viewer';
  joined_at: string;
}
