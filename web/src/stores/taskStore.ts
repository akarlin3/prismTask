import { create } from 'zustand';
import type { Task, TaskCreate, TaskUpdate, SubtaskCreate } from '@/types/task';
import { tasksApi } from '@/api/tasks';

interface TaskState {
  tasks: Task[];
  todayTasks: Task[];
  overdueTasks: Task[];
  upcomingTasks: Task[];
  selectedTask: Task | null;
  isLoading: boolean;
  error: string | null;

  // Selection
  selectedTaskIds: Set<number>;
  toggleTaskSelection: (id: number) => void;
  clearSelection: () => void;
  selectAll: (ids: number[]) => void;

  // Fetch
  fetchByProject: (projectId: number) => Promise<void>;
  fetchToday: () => Promise<void>;
  fetchOverdue: () => Promise<void>;
  fetchUpcoming: (days?: number) => Promise<void>;
  fetchTask: (id: number) => Promise<Task>;

  // CRUD
  createTask: (projectId: number, data: TaskCreate) => Promise<Task>;
  updateTask: (taskId: number, data: TaskUpdate) => Promise<Task>;
  deleteTask: (taskId: number) => Promise<void>;
  completeTask: (taskId: number) => Promise<Task>;
  uncompleteTask: (taskId: number) => Promise<Task>;

  // Subtasks
  createSubtask: (parentId: number, data: SubtaskCreate) => Promise<Task>;

  // Bulk
  bulkComplete: (ids: number[]) => Promise<void>;
  bulkDelete: (ids: number[]) => Promise<void>;
  bulkMove: (ids: number[], projectId: number) => Promise<void>;
  bulkUpdatePriority: (ids: number[], priority: number) => Promise<void>;
  bulkUpdateDueDate: (ids: number[], dueDate: string) => Promise<void>;

  // Local
  setSelectedTask: (task: Task | null) => void;
  clearError: () => void;
  removeTaskFromLists: (taskId: number) => void;
  updateTaskInLists: (task: Task) => void;
}

function updateInArray(arr: Task[], id: number, updated: Task): Task[] {
  return arr.map((t) => (t.id === id ? updated : t));
}

function removeFromArray(arr: Task[], id: number): Task[] {
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
      const tasks = await tasksApi.getByProject(projectId);
      set({ tasks, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  fetchToday: async () => {
    try {
      const todayTasks = await tasksApi.getToday();
      set({ todayTasks });
    } catch (e) {
      set({ error: (e as Error).message });
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

  fetchUpcoming: async (days = 7) => {
    try {
      const upcomingTasks = await tasksApi.getUpcoming(days);
      set({ upcomingTasks });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  fetchTask: async (id) => {
    const task = await tasksApi.get(id);
    set({ selectedTask: task });
    return task;
  },

  createTask: async (projectId, data) => {
    const task = await tasksApi.create(projectId, data);
    set((state) => ({ tasks: [...state.tasks, task] }));
    return task;
  },

  updateTask: async (taskId, data) => {
    const updated = await tasksApi.update(taskId, data);
    get().updateTaskInLists(updated);
    return updated;
  },

  deleteTask: async (taskId) => {
    await tasksApi.delete(taskId);
    get().removeTaskFromLists(taskId);
  },

  completeTask: async (taskId) => {
    const updated = await tasksApi.update(taskId, { status: 'done' });
    get().updateTaskInLists(updated);
    return updated;
  },

  uncompleteTask: async (taskId) => {
    const updated = await tasksApi.update(taskId, { status: 'todo' });
    get().updateTaskInLists(updated);
    return updated;
  },

  createSubtask: async (parentId, data) => {
    const subtask = await tasksApi.createSubtask(parentId, data);
    // Re-fetch the parent to get updated subtasks
    const parent = await tasksApi.get(parentId);
    get().updateTaskInLists(parent);
    if (get().selectedTask?.id === parentId) {
      set({ selectedTask: parent });
    }
    return subtask;
  },

  bulkComplete: async (ids) => {
    await Promise.all(ids.map((id) => tasksApi.update(id, { status: 'done' })));
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
    await Promise.all(ids.map((id) => tasksApi.delete(id)));
    const filterOut = (arr: Task[]) => arr.filter((t) => !ids.includes(t.id));
    set((state) => ({
      tasks: filterOut(state.tasks),
      todayTasks: filterOut(state.todayTasks),
      overdueTasks: filterOut(state.overdueTasks),
      upcomingTasks: filterOut(state.upcomingTasks),
      selectedTaskIds: new Set(),
    }));
  },

  bulkMove: async (ids, _projectId) => {
    await Promise.all(
      ids.map((id) => tasksApi.update(id, {} as TaskUpdate)),
    );
    set({ selectedTaskIds: new Set() });
  },

  bulkUpdatePriority: async (ids, priority) => {
    await Promise.all(
      ids.map((id) => tasksApi.update(id, { priority: priority as 1 | 2 | 3 | 4 })),
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
    await Promise.all(
      ids.map((id) => tasksApi.update(id, { due_date: dueDate })),
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
