import { create } from 'zustand';
import type { Task, TaskCreate, TaskUpdate, SubtaskCreate } from '@/types/task';
import * as firestoreTasks from '@/api/firestore/tasks';
import {
  calculateNextOccurrence,
  parseRecurrenceRule,
} from '@/utils/recurrence';
import type { Unsubscribe } from 'firebase/firestore';

interface TaskState {
  tasks: Task[];
  todayTasks: Task[];
  overdueTasks: Task[];
  upcomingTasks: Task[];
  selectedTask: Task | null;
  isLoading: boolean;
  error: string | null;

  // Selection
  selectedTaskIds: Set<string>;
  toggleTaskSelection: (id: string) => void;
  clearSelection: () => void;
  selectAll: (ids: string[]) => void;

  // Fetch
  fetchByProject: (projectId: string) => Promise<void>;
  fetchToday: () => Promise<void>;
  fetchOverdue: () => Promise<void>;
  fetchUpcoming: (days?: number) => Promise<void>;
  fetchTask: (id: string) => Promise<Task>;

  // CRUD
  createTask: (projectId: string, data: TaskCreate) => Promise<Task>;
  updateTask: (taskId: string, data: TaskUpdate) => Promise<Task>;
  deleteTask: (taskId: string) => Promise<void>;
  completeTask: (taskId: string) => Promise<Task>;
  uncompleteTask: (taskId: string) => Promise<Task>;

  // Subtasks
  createSubtask: (parentId: string, data: SubtaskCreate) => Promise<Task>;

  // Bulk
  bulkComplete: (ids: string[]) => Promise<void>;
  bulkDelete: (ids: string[]) => Promise<void>;
  bulkMove: (ids: string[], projectId: string) => Promise<void>;
  bulkUpdatePriority: (ids: string[], priority: number) => Promise<void>;
  bulkUpdateDueDate: (ids: string[], dueDate: string) => Promise<void>;

  // Real-time
  subscribeToTasks: (uid: string) => Unsubscribe;

  // Local
  setSelectedTask: (task: Task | null) => void;
  clearError: () => void;
  removeTaskFromLists: (taskId: string) => void;
  updateTaskInLists: (task: Task) => void;
}

import { getFirebaseUid } from '@/stores/firebaseUid';

function getUid(): string {
  return getFirebaseUid();
}

function updateInArray(arr: Task[], id: string, updated: Task): Task[] {
  return arr.map((t) => (t.id === id ? updated : t));
}

function removeFromArray(arr: Task[], id: string): Task[] {
  return arr.filter((t) => t.id !== id);
}

export const useTaskStore = create<TaskState>((set, get) => ({
  tasks: [],
  todayTasks: [],
  overdueTasks: [],
  upcomingTasks: [],
  selectedTask: null,
  isLoading: false,
  error: null,
  selectedTaskIds: new Set(),

  toggleTaskSelection: (id) =>
    set((state) => {
      const next = new Set(state.selectedTaskIds);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return { selectedTaskIds: next };
    }),

  clearSelection: () => set({ selectedTaskIds: new Set() }),

  selectAll: (ids) => set({ selectedTaskIds: new Set(ids) }),

  fetchByProject: async (projectId) => {
    set({ isLoading: true, error: null });
    try {
      const uid = getUid();
      const tasks = await firestoreTasks.getTasksByProject(uid, projectId);
      set({ tasks, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  fetchToday: async () => {
    try {
      const uid = getUid();
      const todayTasks = await firestoreTasks.getTodayTasks(uid);
      set({ todayTasks });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  fetchOverdue: async () => {
    try {
      const uid = getUid();
      const overdueTasks = await firestoreTasks.getOverdueTasks(uid);
      set({ overdueTasks });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  fetchUpcoming: async (days = 7) => {
    try {
      const uid = getUid();
      const upcomingTasks = await firestoreTasks.getUpcomingTasks(uid, days);
      set({ upcomingTasks });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  fetchTask: async (id) => {
    const uid = getUid();
    const task = await firestoreTasks.getTask(uid, id);
    if (!task) throw new Error('Task not found');
    // Fetch subtasks
    const subtasks = await firestoreTasks.getSubtasks(uid, id);
    const taskWithSubs = { ...task, subtasks };
    set({ selectedTask: taskWithSubs });
    return taskWithSubs;
  },

  createTask: async (projectId, data) => {
    const uid = getUid();
    const task = await firestoreTasks.createTask(uid, {
      ...data,
      project_id: projectId,
    } as Partial<Task> & { title: string });
    set((state) => ({ tasks: [...state.tasks, task] }));
    return task;
  },

  updateTask: async (taskId, data) => {
    const uid = getUid();
    const updated = await firestoreTasks.updateTask(uid, taskId, data as Record<string, unknown>);
    get().updateTaskInLists(updated);
    return updated;
  },

  deleteTask: async (taskId) => {
    const uid = getUid();
    await firestoreTasks.deleteTask(uid, taskId);
    get().removeTaskFromLists(taskId);
  },

  completeTask: async (taskId) => {
    const uid = getUid();

    // Find the task before completing to check recurrence
    const existingTask =
      get().tasks.find((t) => t.id === taskId) ??
      get().todayTasks.find((t) => t.id === taskId) ??
      get().overdueTasks.find((t) => t.id === taskId) ??
      get().upcomingTasks.find((t) => t.id === taskId);

    const updated = await firestoreTasks.updateTask(uid, taskId, { status: 'done' });
    get().updateTaskInLists(updated);

    // Handle recurrence: create next occurrence if applicable
    if (existingTask?.recurrence_json && existingTask.due_date) {
      const rule = parseRecurrenceRule(existingTask.recurrence_json);
      if (rule) {
        const nextDate = calculateNextOccurrence(existingTask.due_date, rule);
        if (nextDate) {
          try {
            const nextTask = await firestoreTasks.createTask(uid, {
              title: existingTask.title,
              description: existingTask.description ?? undefined,
              priority: existingTask.priority,
              due_date: nextDate,
              sort_order: existingTask.sort_order,
              recurrence_json: existingTask.recurrence_json ?? undefined,
              project_id: existingTask.project_id,
            } as Partial<Task> & { title: string });
            set((state) => ({
              tasks: [...state.tasks, nextTask],
            }));
            (updated as Task & { _nextDate?: string })._nextDate = nextDate;
          } catch {
            // Silently fail — the task is still completed
          }
        }
      }
    }

    return updated;
  },

  uncompleteTask: async (taskId) => {
    const uid = getUid();
    const updated = await firestoreTasks.updateTask(uid, taskId, { status: 'todo' });
    get().updateTaskInLists(updated);
    return updated;
  },

  createSubtask: async (parentId, data) => {
    const uid = getUid();
    const subtask = await firestoreTasks.createTask(uid, {
      ...data,
      parent_id: parentId,
    } as Partial<Task> & { title: string });
    // Re-fetch the parent to get updated subtasks
    const parent = await get().fetchTask(parentId);
    get().updateTaskInLists(parent);
    return subtask;
  },

  bulkComplete: async (ids) => {
    const uid = getUid();
    await Promise.all(ids.map((id) => firestoreTasks.updateTask(uid, id, { status: 'done' })));
    set((state) => ({
      tasks: state.tasks.map((t) =>
        ids.includes(t.id) ? { ...t, status: 'done' as const } : t,
      ),
      todayTasks: state.todayTasks.map((t) =>
        ids.includes(t.id) ? { ...t, status: 'done' as const } : t,
      ),
      overdueTasks: state.overdueTasks.map((t) =>
        ids.includes(t.id) ? { ...t, status: 'done' as const } : t,
      ),
      upcomingTasks: state.upcomingTasks.map((t) =>
        ids.includes(t.id) ? { ...t, status: 'done' as const } : t,
      ),
      selectedTaskIds: new Set(),
    }));
  },

  bulkDelete: async (ids) => {
    const uid = getUid();
    await Promise.all(ids.map((id) => firestoreTasks.deleteTask(uid, id)));
    const filterOut = (arr: Task[]) => arr.filter((t) => !ids.includes(t.id));
    set((state) => ({
      tasks: filterOut(state.tasks),
      todayTasks: filterOut(state.todayTasks),
      overdueTasks: filterOut(state.overdueTasks),
      upcomingTasks: filterOut(state.upcomingTasks),
      selectedTaskIds: new Set(),
    }));
  },

  bulkMove: async (ids, projectId) => {
    const uid = getUid();
    await Promise.all(
      ids.map((id) => firestoreTasks.updateTask(uid, id, { project_id: projectId })),
    );
    set({ selectedTaskIds: new Set() });
  },

  bulkUpdatePriority: async (ids, priority) => {
    const uid = getUid();
    await Promise.all(
      ids.map((id) => firestoreTasks.updateTask(uid, id, { priority })),
    );
    const updatePriority = (arr: Task[]) =>
      arr.map((t) =>
        ids.includes(t.id)
          ? { ...t, priority: priority as 1 | 2 | 3 | 4 }
          : t,
      );
    set((state) => ({
      tasks: updatePriority(state.tasks),
      todayTasks: updatePriority(state.todayTasks),
      overdueTasks: updatePriority(state.overdueTasks),
      upcomingTasks: updatePriority(state.upcomingTasks),
      selectedTaskIds: new Set(),
    }));
  },

  bulkUpdateDueDate: async (ids, dueDate) => {
    const uid = getUid();
    await Promise.all(
      ids.map((id) => firestoreTasks.updateTask(uid, id, { due_date: dueDate })),
    );
    const updateDue = (arr: Task[]) =>
      arr.map((t) =>
        ids.includes(t.id) ? { ...t, due_date: dueDate } : t,
      );
    set((state) => ({
      tasks: updateDue(state.tasks),
      todayTasks: updateDue(state.todayTasks),
      overdueTasks: updateDue(state.overdueTasks),
      upcomingTasks: updateDue(state.upcomingTasks),
      selectedTaskIds: new Set(),
    }));
  },

  subscribeToTasks: (uid: string) => {
    return firestoreTasks.subscribeToTasks(uid, (allTasks) => {
      // Partition tasks into today/overdue/upcoming based on due date
      const now = new Date();
      const todayStr = now.toISOString().slice(0, 10);
      const todayTasks: Task[] = [];
      const overdueTasks: Task[] = [];
      const upcomingTasks: Task[] = [];

      for (const task of allTasks) {
        if (task.status === 'done') continue;
        if (!task.due_date) continue;
        if (task.due_date === todayStr) {
          todayTasks.push(task);
        } else if (task.due_date < todayStr) {
          overdueTasks.push(task);
        } else {
          upcomingTasks.push(task);
        }
      }

      set({ tasks: allTasks, todayTasks, overdueTasks, upcomingTasks });
    });
  },

  setSelectedTask: (task) => set({ selectedTask: task }),
  clearError: () => set({ error: null }),

  removeTaskFromLists: (taskId) =>
    set((state) => ({
      tasks: removeFromArray(state.tasks, taskId),
      todayTasks: removeFromArray(state.todayTasks, taskId),
      overdueTasks: removeFromArray(state.overdueTasks, taskId),
      upcomingTasks: removeFromArray(state.upcomingTasks, taskId),
      selectedTask:
        state.selectedTask?.id === taskId ? null : state.selectedTask,
    })),

  updateTaskInLists: (task) =>
    set((state) => ({
      tasks: updateInArray(state.tasks, task.id, task),
      todayTasks: updateInArray(state.todayTasks, task.id, task),
      overdueTasks: updateInArray(state.overdueTasks, task.id, task),
      upcomingTasks: updateInArray(state.upcomingTasks, task.id, task),
      selectedTask:
        state.selectedTask?.id === task.id ? task : state.selectedTask,
    })),
}));
