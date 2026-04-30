import { useState, useEffect, useCallback, useMemo } from 'react';
import { format, parseISO, isToday, isBefore, startOfDay } from 'date-fns';
import type { Task } from '@/types/task';
import { tasksApi } from '@/api/tasks';

/**
 * Groups tasks by ISO date string for calendar views.
 * Overdue tasks are included in today's bucket.
 */
export function useCalendarTasks(startDate: Date, endDate: Date) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const startKey = format(startDate, 'yyyy-MM-dd');
  const endKey = format(endDate, 'yyyy-MM-dd');

  const fetchTasks = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // Fetch today, overdue, and upcoming tasks to cover the date range
      const [todayTasks, overdueTasks, upcomingTasks] = await Promise.all([
        tasksApi.getToday(),
        tasksApi.getOverdue(),
        tasksApi.getUpcoming(90), // Fetch up to 90 days ahead
      ]);

      // Merge and deduplicate by id
      const allMap = new Map<string, Task>();
      [...todayTasks, ...overdueTasks, ...upcomingTasks].forEach((t) => {
        allMap.set(t.id, t);
      });
      setTasks(Array.from(allMap.values()));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- data-fetch effect: load calendar tasks on mount
    fetchTasks();
  }, [fetchTasks]);

  const tasksByDate = useMemo(() => {
    const map = new Map<string, Task[]>();
    const todayStr = format(new Date(), 'yyyy-MM-dd');

    for (const task of tasks) {
      if (task.status === 'done' || task.status === 'cancelled') continue;

      let dateKey: string;
      if (!task.due_date) continue;

      const dueDate = parseISO(task.due_date);
      const isOverdue =
        isBefore(startOfDay(dueDate), startOfDay(new Date())) &&
        !isToday(dueDate);

      if (isOverdue) {
        // Overdue tasks go in today's bucket
        dateKey = todayStr;
      } else {
        dateKey = format(dueDate, 'yyyy-MM-dd');
      }

      // Only include tasks within the visible range
      if (dateKey < startKey || dateKey > endKey) continue;

      const existing = map.get(dateKey) || [];
      existing.push(task);
      map.set(dateKey, existing);
    }

    // Sort tasks within each day by priority (urgent first)
    for (const [key, dayTasks] of map.entries()) {
      map.set(
        key,
        dayTasks.sort((a, b) => a.priority - b.priority),
      );
    }

    return map;
  }, [tasks, startKey, endKey]);

  const getTasksForDate = useCallback(
    (date: Date): Task[] => {
      const key = format(date, 'yyyy-MM-dd');
      return tasksByDate.get(key) || [];
    },
    [tasksByDate],
  );

  const getTaskCountForDate = useCallback(
    (date: Date): number => {
      return getTasksForDate(date).length;
    },
    [getTasksForDate],
  );

  const getHighestPriorityForDate = useCallback(
    (date: Date): number | null => {
      const dayTasks = getTasksForDate(date);
      if (dayTasks.length === 0) return null;
      return Math.min(...dayTasks.map((t) => t.priority));
    },
    [getTasksForDate],
  );

  return {
    tasks,
    tasksByDate,
    isLoading,
    error,
    refetch: fetchTasks,
    getTasksForDate,
    getTaskCountForDate,
    getHighestPriorityForDate,
  };
}
