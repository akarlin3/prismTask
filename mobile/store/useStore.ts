import { create } from "zustand";
import * as authService from "../services/auth";
import * as api from "../services/api";
import type { Goal, Project, Task, User, DashboardSummary } from "../services/api";

interface AppState {
  // Auth
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  // Theme
  isDarkMode: boolean;
  toggleDarkMode: () => void;
  setDarkMode: (value: boolean) => void;

  // Goals
  goals: Goal[];
  selectedGoal: Goal | null;

  // Projects
  projects: Project[];
  selectedProject: Project | null;

  // Tasks
  tasks: Task[];

  // Dashboard
  summary: DashboardSummary | null;
  todayTasks: Task[];
  overdueTasks: Task[];
  upcomingTasks: Task[];

  // Actions
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  checkAuth: () => Promise<void>;

  fetchGoals: () => Promise<void>;
  fetchGoal: (id: number) => Promise<void>;
  createGoal: (data: Partial<Goal>) => Promise<Goal>;
  updateGoal: (id: number, data: Partial<Goal>) => Promise<void>;
  deleteGoal: (id: number) => Promise<void>;

  fetchProjects: (goalId: number) => Promise<void>;
  fetchProject: (id: number) => Promise<void>;
  createProject: (goalId: number, data: Partial<Project>) => Promise<Project>;
  updateProject: (id: number, data: Partial<Project>) => Promise<void>;
  deleteProject: (id: number) => Promise<void>;

  fetchTasks: (projectId: number) => Promise<void>;
  createTask: (projectId: number, data: Partial<Task>) => Promise<Task>;
  updateTask: (id: number, data: Partial<Task>) => Promise<void>;
  deleteTask: (id: number) => Promise<void>;
  createSubtask: (taskId: number, data: Partial<Task>) => Promise<Task>;

  fetchDashboard: () => Promise<void>;
  fetchTodayTasks: () => Promise<void>;
  fetchOverdueTasks: () => Promise<void>;
  fetchUpcomingTasks: (days?: number) => Promise<void>;
}

export const useStore = create<AppState>((set, get) => ({
  // Initial state
  user: null,
  isAuthenticated: false,
  isLoading: true,
  isDarkMode: false,
  goals: [],
  selectedGoal: null,
  projects: [],
  selectedProject: null,
  tasks: [],
  summary: null,
  todayTasks: [],
  overdueTasks: [],
  upcomingTasks: [],

  toggleDarkMode: () => set((s) => ({ isDarkMode: !s.isDarkMode })),
  setDarkMode: (value) => set({ isDarkMode: value }),

  // Auth
  login: async (email, password) => {
    const resp = await api.loginApi(email, password);
    await authService.storeTokens(resp.data.access_token, resp.data.refresh_token);
    const me = await api.getMe();
    set({ user: me.data, isAuthenticated: true });
  },

  register: async (name, email, password) => {
    const resp = await api.registerApi(name, email, password);
    await authService.storeTokens(resp.data.access_token, resp.data.refresh_token);
    const me = await api.getMe();
    set({ user: me.data, isAuthenticated: true });
  },

  logout: async () => {
    await authService.clearTokens();
    set({
      user: null,
      isAuthenticated: false,
      goals: [],
      projects: [],
      tasks: [],
      selectedGoal: null,
      selectedProject: null,
      summary: null,
      todayTasks: [],
      overdueTasks: [],
      upcomingTasks: [],
    });
  },

  checkAuth: async () => {
    try {
      const token = await authService.getAccessToken();
      if (token) {
        const me = await api.getMe();
        set({ user: me.data, isAuthenticated: true, isLoading: false });
      } else {
        set({ isAuthenticated: false, isLoading: false });
      }
    } catch {
      set({ isAuthenticated: false, isLoading: false });
    }
  },

  // Goals
  fetchGoals: async () => {
    const resp = await api.getGoals();
    set({ goals: resp.data });
  },

  fetchGoal: async (id) => {
    const resp = await api.getGoal(id);
    set({ selectedGoal: resp.data, projects: resp.data.projects || [] });
  },

  createGoal: async (data) => {
    const resp = await api.createGoal(data);
    set((s) => ({ goals: [...s.goals, resp.data] }));
    return resp.data;
  },

  updateGoal: async (id, data) => {
    const resp = await api.updateGoal(id, data);
    set((s) => ({
      goals: s.goals.map((g) => (g.id === id ? resp.data : g)),
      selectedGoal: s.selectedGoal?.id === id ? resp.data : s.selectedGoal,
    }));
  },

  deleteGoal: async (id) => {
    await api.deleteGoal(id);
    set((s) => ({
      goals: s.goals.filter((g) => g.id !== id),
      selectedGoal: s.selectedGoal?.id === id ? null : s.selectedGoal,
    }));
  },

  // Projects
  fetchProjects: async (goalId) => {
    const resp = await api.getProjects(goalId);
    set({ projects: resp.data });
  },

  fetchProject: async (id) => {
    const resp = await api.getProject(id);
    set({ selectedProject: resp.data, tasks: resp.data.tasks || [] });
  },

  createProject: async (goalId, data) => {
    const resp = await api.createProject(goalId, data);
    set((s) => ({ projects: [...s.projects, resp.data] }));
    return resp.data;
  },

  updateProject: async (id, data) => {
    const resp = await api.updateProject(id, data);
    set((s) => ({
      projects: s.projects.map((p) => (p.id === id ? resp.data : p)),
      selectedProject: s.selectedProject?.id === id ? resp.data : s.selectedProject,
    }));
  },

  deleteProject: async (id) => {
    await api.deleteProject(id);
    set((s) => ({
      projects: s.projects.filter((p) => p.id !== id),
    }));
  },

  // Tasks
  fetchTasks: async (projectId) => {
    const resp = await api.getTasks(projectId);
    set({ tasks: resp.data });
  },

  createTask: async (projectId, data) => {
    const resp = await api.createTask(projectId, data);
    set((s) => ({ tasks: [...s.tasks, resp.data] }));
    return resp.data;
  },

  updateTask: async (id, data) => {
    const resp = await api.updateTask(id, data);
    set((s) => ({
      tasks: s.tasks.map((t) => (t.id === id ? resp.data : t)),
    }));
  },

  deleteTask: async (id) => {
    await api.deleteTask(id);
    set((s) => ({ tasks: s.tasks.filter((t) => t.id !== id) }));
  },

  createSubtask: async (taskId, data) => {
    const resp = await api.createSubtask(taskId, data);
    set((s) => ({
      tasks: s.tasks.map((t) =>
        t.id === taskId
          ? { ...t, subtasks: [...(t.subtasks || []), resp.data] }
          : t
      ),
    }));
    return resp.data;
  },

  // Dashboard
  fetchDashboard: async () => {
    const resp = await api.getDashboardSummary();
    set({ summary: resp.data });
  },

  fetchTodayTasks: async () => {
    const resp = await api.getTasksToday();
    set({ todayTasks: resp.data });
  },

  fetchOverdueTasks: async () => {
    const resp = await api.getTasksOverdue();
    set({ overdueTasks: resp.data });
  },

  fetchUpcomingTasks: async (days = 7) => {
    const resp = await api.getTasksUpcoming(days);
    set({ upcomingTasks: resp.data });
  },
}));
