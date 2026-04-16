export interface SyllabusTask {
  title: string;
  due_date: string | null;
  due_time: string | null;
  type: 'assignment' | 'exam' | 'quiz' | 'project' | 'reading' | 'other';
  notes: string | null;
}

export interface SyllabusEvent {
  title: string;
  date: string | null;
  start_time: string | null;
  end_time: string | null;
  location: string | null;
}

export interface SyllabusRecurringItem {
  title: string;
  day_of_week: string;
  start_time: string | null;
  end_time: string | null;
  location: string | null;
  recurrence_end_date: string | null;
}

export interface SyllabusParseResult {
  course_name: string;
  tasks: SyllabusTask[];
  events: SyllabusEvent[];
  recurring_schedule: SyllabusRecurringItem[];
}

export interface SyllabusConfirmRequest {
  course_name: string;
  tasks: SyllabusTask[];
  events: SyllabusEvent[];
  recurring_schedule: SyllabusRecurringItem[];
}

export interface SyllabusConfirmResponse {
  tasks_created: number;
  events_created: number;
  recurring_created: number;
}
