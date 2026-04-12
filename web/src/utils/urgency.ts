import { differenceInDays, parseISO, startOfDay } from 'date-fns';
import type { Task } from '@/types/task';

interface UrgencyWeights {
  dueDateWeight: number;
  priorityWeight: number;
  ageWeight: number;
  subtaskWeight: number;
}

const DEFAULT_WEIGHTS: UrgencyWeights = {
  dueDateWeight: 0.4,
  priorityWeight: 0.3,
  ageWeight: 0.2,
  subtaskWeight: 0.1,
};

export function computeUrgencyScore(
  task: Task,
  weights: UrgencyWeights = DEFAULT_WEIGHTS,
): number {
  let score = 0;

  // Due date component (0-1): higher as deadline approaches
  if (task.due_date) {
    const daysUntilDue = differenceInDays(
      startOfDay(parseISO(task.due_date)),
      startOfDay(new Date()),
    );
    if (daysUntilDue <= 0) {
      score += weights.dueDateWeight; // Overdue = max
    } else if (daysUntilDue <= 7) {
      score += weights.dueDateWeight * (1 - daysUntilDue / 7);
    }
  }

  // Priority component (0-1): 1=urgent(1.0), 2=high(0.75), 3=med(0.5), 4=low(0.25)
  const priorityScore = (5 - task.priority) / 4;
  score += weights.priorityWeight * priorityScore;

  // Age component (0-1): older tasks get higher scores
  const ageInDays = differenceInDays(
    new Date(),
    parseISO(task.created_at),
  );
  const ageFactor = Math.min(ageInDays / 30, 1); // Max at 30 days
  score += weights.ageWeight * ageFactor;

  // Subtask progress component
  if (task.subtasks && task.subtasks.length > 0) {
    const completed = task.subtasks.filter(
      (s) => s.status === 'done',
    ).length;
    const progress = completed / task.subtasks.length;
    // Higher urgency if more subtasks done (closer to completion)
    score += weights.subtaskWeight * progress;
  }

  return Math.min(Math.max(score, 0), 1);
}
