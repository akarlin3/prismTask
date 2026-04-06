const dayMs = 86400000;

export function formatDate(timestamp?: number): string {
  if (!timestamp) return '';
  const d = new Date(timestamp);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const todayMs = today.getTime();

  if (timestamp < todayMs) return 'Overdue';
  if (timestamp < todayMs + dayMs) return 'Today';
  if (timestamp < todayMs + dayMs * 2) return 'Tomorrow';
  if (timestamp < todayMs + dayMs * 7) {
    return d.toLocaleDateString('en-US', { weekday: 'short' });
  }
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

export function formatRelativeDate(timestamp?: number): string {
  if (!timestamp) return 'No date';
  const d = new Date(timestamp);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const todayMs = today.getTime();
  const diff = timestamp - todayMs;

  if (diff < 0) {
    const daysAgo = Math.ceil(-diff / dayMs);
    return daysAgo === 1 ? 'Yesterday' : `${daysAgo} days ago`;
  }
  if (diff < dayMs) return 'Today';
  if (diff < dayMs * 2) return 'Tomorrow';
  if (diff < dayMs * 7) return d.toLocaleDateString('en-US', { weekday: 'long' });
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

export function groupTasksByDate(tasks: { dueDate?: number; isCompleted: boolean }[]): Record<string, typeof tasks> {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const todayMs = today.getTime();
  const tomorrowMs = todayMs + dayMs;
  const weekMs = todayMs + dayMs * 7;

  const groups: Record<string, typeof tasks> = {};
  for (const task of tasks) {
    if (task.isCompleted) continue;
    let group: string;
    if (!task.dueDate) group = 'No Date';
    else if (task.dueDate < todayMs) group = 'Overdue';
    else if (task.dueDate < tomorrowMs) group = 'Today';
    else if (task.dueDate < tomorrowMs + dayMs) group = 'Tomorrow';
    else if (task.dueDate < weekMs) group = 'This Week';
    else group = 'Later';

    if (!groups[group]) groups[group] = [];
    groups[group].push(task);
  }
  return groups;
}

export const priorityColors: Record<number, string> = {
  0: 'transparent',
  1: '#6B7280',
  2: '#F59E0B',
  3: '#F97316',
  4: '#EF4444',
};

export const priorityLabels: Record<number, string> = {
  0: 'None',
  1: 'Low',
  2: 'Medium',
  3: 'High',
  4: 'Urgent',
};

export function calculateUrgency(task: {
  dueDate?: number;
  priority: number;
  createdAt: number;
}): number {
  const now = Date.now();
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const todayMs = today.getTime();

  // Due date score (40%)
  let dueScore = 0;
  if (task.dueDate) {
    const daysUntilDue = (task.dueDate - todayMs) / dayMs;
    if (daysUntilDue < -7) dueScore = 1.0;
    else if (daysUntilDue < 0) dueScore = 0.7 + (0.3 * (-daysUntilDue / 7));
    else if (daysUntilDue < 1) dueScore = 0.6;
    else if (daysUntilDue < 2) dueScore = 0.4;
    else if (daysUntilDue < 7) dueScore = 0.1 + (0.3 * (1 - daysUntilDue / 7));
    else dueScore = Math.max(0, 0.1 * (1 - daysUntilDue / 30));
  }

  // Priority score (30%)
  const priorityScores: Record<number, number> = { 0: 0, 1: 0.2, 2: 0.5, 3: 0.8, 4: 1.0 };
  const prioScore = priorityScores[task.priority] ?? 0;

  // Age score (15%)
  const ageDays = (now - task.createdAt) / dayMs;
  let ageScore = 0;
  if (ageDays > 14) ageScore = 0.8 + Math.min(0.2, (ageDays - 14) / 30);
  else if (ageDays > 7) ageScore = 0.6 + (0.2 * (ageDays - 7) / 7);
  else if (ageDays > 3) ageScore = 0.3 + (0.3 * (ageDays - 3) / 4);
  else if (ageDays > 1) ageScore = 0.1 + (0.2 * (ageDays - 1) / 2);

  return dueScore * 0.4 + prioScore * 0.3 + ageScore * 0.15;
}

export function urgencyLevel(score: number): 'critical' | 'high' | 'medium' | 'low' {
  if (score >= 0.7) return 'critical';
  if (score >= 0.5) return 'high';
  if (score >= 0.3) return 'medium';
  return 'low';
}
