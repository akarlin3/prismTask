import { create } from 'zustand';
import type { Task, TaskCreate, TaskUpdate } from '@/types/task';
import { tasksApi } from '@/api/tasks';

interface TaskState {
  tasks: Task[];
  todayTasks: Task[];
  overdueTasks: Task[];
  selectedTask: Task | null;
  isLoading: boolean;
  error: string | null;

  fetchByProject: (projectId: number) => Promise<void>;
  fetchToday: () => Promise<void>;
  fetchOverdue: () => Promise<void>;
  fetchUpcoming: (days?: number) => Promise<void>;
  createTask: (projectId: number, data: TaskCreate) => Promise<Task>;
  updateTask: (taskId: number, data: TaskUpdate) => Promise<Task>;
  deleteTask: (taskId: number) => Promise<void>;
  setSelectedTask: (task: Task | null) => void;
  clearError: () => void;
}

export const useTaskStore = create<TaskState>((set) => ({
  tasks: [],
  todayTasks: [],
  overdueTasks: [],
  selectedTask: null,
  isLoading: false,
  error: null,

  fetchByProject: async (projectId) => {
    set({ isLoading: true, error: null });
    try {
      const tasks = await tasksApi.getByProject(projectId);
      set({ tasks, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  fetchToday: async () => {
    set({ isLoading: true, error: null });
    try {
      const todayTasks = await tasksApi.getToday();
      set({ todayTasks, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  fetchOverdue: async () => {
    try {
      const overdueTasks = await tasksApi.getOverdue();
      set({ overdueTasks });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  fetchUpcoming: async (days) => {
    set({ isLoading: true, error: null });
    try {
      const tasks = await tasksApi.getUpcoming(days);
      set({ tasks, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  createTask: async (projectId, data) => {
    const task = await tasksApi.create(projectId, data);
    set((state) => ({ tasks: [...state.tasks, task] }));
    return task;
  },

  updateTask: async (taskId, data) => {
    const updated = await tasksApi.update(taskId, data);
    set((state) => ({
      tasks: state.tasks.map((t) => (t.id === taskId ? updated : t)),
      todayTasks: state.todayTasks.map((t) =>
        t.id === taskId ? updated : t,
      ),
    }));
    return updated;
  },

  deleteTask: async (taskId) => {
    await tasksApi.delete(taskId);
    set((state) => ({
      tasks: state.tasks.filter((t) => t.id !== taskId),
      todayTasks: state.todayTasks.filter((t) => t.id !== taskId),
    }));
  },

  setSelectedTask: (task) => set({ selectedTask: task }),
  clearError: () => set({ error: null }),
}));
