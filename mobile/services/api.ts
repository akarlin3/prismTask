import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
import { API_BASE_URL } from "../constants/api";
import { getAccessToken, refreshAccessToken, clearTokens } from "./auth";

// Types
export interface User {
  id: number;
  email: string;
  name: string;
}

export interface Goal {
  id: number;
  user_id: number;
  title: string;
  description: string | null;
  status: string;
  target_date: string | null;
  color: string | null;
  sort_order: number;
  created_at: string;
  updated_at: string;
  projects?: Project[];
}

export interface Project {
  id: number;
  goal_id: number;
  user_id: number;
  title: string;
  description: string | null;
  status: string;
  due_date: string | null;
  sort_order: number;
  created_at: string;
  updated_at: string;
  tasks?: Task[];
}

export interface Task {
  id: number;
  project_id: number;
  user_id: number;
  parent_id: number | null;
  title: string;
  description: string | null;
  status: string;
  priority: number;
  due_date: string | null;
  completed_at: string | null;
  sort_order: number;
  depth: number;
  created_at: string;
  updated_at: string;
  subtasks: Task[];
}

export interface DashboardSummary {
  total_tasks: number;
  completed_tasks: number;
  overdue_tasks: number;
  today_tasks: number;
  upcoming_tasks: number;
  completion_rate: number;
}

export interface ParsedTask {
  title: string;
  project_suggestion: string | null;
  due_date: string | null;
  priority: number | null;
  parent_task_suggestion: string | null;
  confidence: number;
  needs_confirmation: boolean;
}

// Axios instance
const api = axios.create({ baseURL: API_BASE_URL });

// Request interceptor — attach JWT
api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const token = await getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor — refresh on 401
let isRefreshing = false;
let pendingRequests: Array<(token: string) => void> = [];

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      if (!isRefreshing) {
        isRefreshing = true;
        const newToken = await refreshAccessToken();
        isRefreshing = false;

        if (newToken) {
          pendingRequests.forEach((cb) => cb(newToken));
          pendingRequests = [];
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
          return api(originalRequest);
        } else {
          pendingRequests = [];
          await clearTokens();
          return Promise.reject(error);
        }
      }

      return new Promise((resolve) => {
        pendingRequests.push((token: string) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          resolve(api(originalRequest));
        });
      });
    }

    return Promise.reject(error);
  }
);

// Auth
export const loginApi = (email: string, password: string) =>
  api.post("/auth/login", { email, password });

export const registerApi = (name: string, email: string, password: string) =>
  api.post("/auth/register", { name, email, password });

export const getMe = () => api.get<User>("/auth/me");

// Goals
export const getGoals = () => api.get<Goal[]>("/goals");
export const getGoal = (id: number) => api.get<Goal>(`/goals/${id}`);
export const createGoal = (data: Partial<Goal>) => api.post<Goal>("/goals", data);
export const updateGoal = (id: number, data: Partial<Goal>) =>
  api.patch<Goal>(`/goals/${id}`, data);
export const deleteGoal = (id: number) => api.delete(`/goals/${id}`);
export const reorderGoals = (items: { id: number; sort_order: number }[]) =>
  api.patch("/goals/reorder", items);

// Projects
export const getProjects = (goalId: number) =>
  api.get<Project[]>(`/goals/${goalId}/projects`);
export const getProject = (id: number) => api.get<Project>(`/projects/${id}`);
export const createProject = (goalId: number, data: Partial<Project>) =>
  api.post<Project>(`/goals/${goalId}/projects`, data);
export const updateProject = (id: number, data: Partial<Project>) =>
  api.patch<Project>(`/projects/${id}`, data);
export const deleteProject = (id: number) => api.delete(`/projects/${id}`);
export const reorderProjects = (items: { id: number; sort_order: number }[]) =>
  api.patch("/projects/reorder", items);

// Tasks
export const getTasks = (projectId: number) =>
  api.get<Task[]>(`/projects/${projectId}/tasks`);
export const getTask = (id: number) => api.get<Task>(`/tasks/${id}`);
export const createTask = (projectId: number, data: Partial<Task>) =>
  api.post<Task>(`/projects/${projectId}/tasks`, data);
export const updateTask = (id: number, data: Partial<Task>) =>
  api.patch<Task>(`/tasks/${id}`, data);
export const deleteTask = (id: number) => api.delete(`/tasks/${id}`);
export const createSubtask = (taskId: number, data: Partial<Task>) =>
  api.post<Task>(`/tasks/${taskId}/subtasks`, data);
export const reorderTasks = (items: { id: number; sort_order: number }[]) =>
  api.patch("/tasks/reorder", items);

// Dashboard
export const getTasksToday = () => api.get<Task[]>("/tasks/today");
export const getTasksOverdue = () => api.get<Task[]>("/tasks/overdue");
export const getTasksUpcoming = (days: number = 7) =>
  api.get<Task[]>(`/tasks/upcoming?days=${days}`);
export const getDashboardSummary = () =>
  api.get<DashboardSummary>("/dashboard/summary");

// Search
export const searchTasks = (q: string) =>
  api.get<{ results: Task[]; count: number }>(`/search?q=${encodeURIComponent(q)}`);

// NLP
export const parseTaskInput = (text: string) =>
  api.post<ParsedTask>("/tasks/parse", { text });

export default api;
