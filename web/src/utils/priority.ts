import type { TaskPriority } from '@/types/task';

export const PRIORITY_CONFIG: Record<
  TaskPriority,
  { label: string; color: string; bgColor: string }
> = {
  1: {
    label: 'Urgent',
    color: 'var(--color-priority-urgent)',
    bgColor: 'rgba(239, 68, 68, 0.1)',
  },
  2: {
    label: 'High',
    color: 'var(--color-priority-high)',
    bgColor: 'rgba(245, 158, 11, 0.1)',
  },
  3: {
    label: 'Medium',
    color: 'var(--color-priority-medium)',
    bgColor: 'rgba(59, 130, 246, 0.1)',
  },
  4: {
    label: 'Low',
    color: 'var(--color-priority-low)',
    bgColor: 'rgba(107, 114, 128, 0.1)',
  },
};

export function getPriorityLabel(priority: TaskPriority): string {
  return PRIORITY_CONFIG[priority]?.label ?? 'None';
}

export function getPriorityColor(priority: TaskPriority): string {
  return PRIORITY_CONFIG[priority]?.color ?? 'var(--color-priority-none)';
}
