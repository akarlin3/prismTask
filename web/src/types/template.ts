export interface TaskTemplate {
  id: string;
  user_id: string;
  name: string;
  description: string | null;
  icon: string | null;
  category: string | null;
  template_title: string | null;
  template_description: string | null;
  template_priority: number | null;
  template_project_id: string | null;
  template_tags_json: string | null;
  template_recurrence_json: string | null;
  template_duration: number | null;
  template_subtasks_json: string | null;
  is_built_in: boolean;
  usage_count: number;
  last_used_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface TemplateCreate {
  name: string;
  description?: string;
  icon?: string;
  category?: string;
  template_title?: string;
  template_description?: string;
  template_priority?: number;
  template_project_id?: string;
  template_tags_json?: string;
  template_recurrence_json?: string;
  template_duration?: number;
  template_subtasks_json?: string;
}

export interface TemplateUpdate {
  name?: string;
  description?: string;
  icon?: string;
  category?: string;
  template_title?: string;
  template_description?: string;
  template_priority?: number;
  template_project_id?: string;
  template_tags_json?: string;
  template_recurrence_json?: string;
  template_duration?: number;
  template_subtasks_json?: string;
}

export interface TemplateUseRequest {
  due_date?: string;
  project_id?: string;
}

export interface TemplateUseResponse {
  task_id: string;
  message: string;
}
