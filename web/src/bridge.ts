import type { Task, Project, HabitWithStatus, Tag } from './types';

// Bridge layer that communicates with Android via JavascriptInterface
// Falls back to mock data when running in browser for development

const isAndroid = !!window.AndroidBridge;

function callBridge<T>(method: string, ...args: unknown[]): T | null {
  if (!isAndroid) return null;
  try {
    const bridge = window.AndroidBridge!;
    const fn = (bridge as unknown as Record<string, (...a: unknown[]) => unknown>)[method];
    if (typeof fn === 'function') {
      const result = fn.apply(bridge, args);
      if (typeof result === 'string') return JSON.parse(result) as T;
      return result as T;
    }
  } catch (e) {
    console.error(`Bridge call failed: ${method}`, e);
  }
  return null;
}

function callBridgeVoid(method: string, ...args: unknown[]): void {
  if (!isAndroid) return;
  try {
    const bridge = window.AndroidBridge!;
    const fn = (bridge as unknown as Record<string, (...a: unknown[]) => void>)[method];
    if (typeof fn === 'function') fn.apply(bridge, args);
  } catch (e) {
    console.error(`Bridge call failed: ${method}`, e);
  }
}

// --- Mock data for browser development ---
const now = Date.now();
const startOfToday = new Date();
startOfToday.setHours(0, 0, 0, 0);
const todayMs = startOfToday.getTime();
const dayMs = 86400000;

const mockProjects: Project[] = [
  { id: 1, name: 'Work', color: '#4A90D9', icon: '💼', createdAt: now, updatedAt: now, taskCount: 3 },
  { id: 2, name: 'Personal', color: '#50C878', icon: '🏠', createdAt: now, updatedAt: now, taskCount: 2 },
  { id: 3, name: 'Side Project', color: '#FF6B6B', icon: '🚀', createdAt: now, updatedAt: now, taskCount: 1 },
];

const mockTags: Tag[] = [
  { id: 1, name: 'urgent', color: '#FF4444', createdAt: now },
  { id: 2, name: 'design', color: '#9B59B6', createdAt: now },
  { id: 3, name: 'backend', color: '#3498DB', createdAt: now },
];

const mockTasks: Task[] = [
  { id: 1, title: 'Fix login bug', description: 'Users getting 401 on refresh', dueDate: todayMs - dayMs, priority: 4, isCompleted: false, projectId: 1, createdAt: now - dayMs * 3, updatedAt: now },
  { id: 2, title: 'Design new dashboard', dueDate: todayMs, priority: 3, isCompleted: false, projectId: 1, createdAt: now - dayMs * 2, updatedAt: now },
  { id: 3, title: 'Write unit tests', dueDate: todayMs, priority: 2, isCompleted: false, projectId: 1, createdAt: now - dayMs, updatedAt: now },
  { id: 4, title: 'Grocery shopping', dueDate: todayMs + dayMs, priority: 1, isCompleted: false, projectId: 2, createdAt: now, updatedAt: now },
  { id: 5, title: 'Call dentist', dueDate: todayMs + dayMs * 3, priority: 2, isCompleted: false, projectId: 2, createdAt: now, updatedAt: now },
  { id: 6, title: 'Deploy v2.0', dueDate: todayMs + dayMs * 7, priority: 3, isCompleted: false, projectId: 3, createdAt: now, updatedAt: now },
  { id: 7, title: 'Morning standup', dueDate: todayMs, priority: 1, isCompleted: true, completedAt: now, createdAt: now - dayMs, updatedAt: now },
  { id: 8, title: 'Review PR #42', dueDate: todayMs, priority: 2, isCompleted: true, completedAt: now, createdAt: now - dayMs, updatedAt: now },
  { id: 9, title: 'Plan sprint', dueDate: todayMs + dayMs * 14, priority: 1, isCompleted: false, createdAt: now, updatedAt: now },
  { id: 10, title: 'Read chapter 5', priority: 0, isCompleted: false, createdAt: now, updatedAt: now },
];

const mockHabits: HabitWithStatus[] = [
  { habit: { id: 1, name: 'Morning Exercise', targetFrequency: 1, frequencyPeriod: 'daily', color: '#FF6B6B', icon: '🏃', sortOrder: 0, isArchived: false, createDailyTask: false, createdAt: now, updatedAt: now }, isCompletedToday: true, currentStreak: 5, completionsThisWeek: 4 },
  { habit: { id: 2, name: 'Read 30min', targetFrequency: 1, frequencyPeriod: 'daily', color: '#4A90D9', icon: '📚', sortOrder: 1, isArchived: false, createDailyTask: false, createdAt: now, updatedAt: now }, isCompletedToday: false, currentStreak: 12, completionsThisWeek: 3 },
  { habit: { id: 3, name: 'Meditate', targetFrequency: 1, frequencyPeriod: 'daily', color: '#50C878', icon: '🧘', sortOrder: 2, isArchived: false, createDailyTask: false, createdAt: now, updatedAt: now }, isCompletedToday: false, currentStreak: 0, completionsThisWeek: 1 },
  { habit: { id: 4, name: 'Journal', targetFrequency: 1, frequencyPeriod: 'daily', color: '#F39C12', icon: '✍️', sortOrder: 3, isArchived: false, createDailyTask: false, createdAt: now, updatedAt: now }, isCompletedToday: true, currentStreak: 3, completionsThisWeek: 5 },
];

const mockTaskTags: Record<number, Tag[]> = {
  1: [mockTags[0], mockTags[2]],
  2: [mockTags[1]],
  3: [mockTags[2]],
};

// --- Public API ---

export const bridge = {
  // Tasks
  getTasks(): Task[] {
    return callBridge<Task[]>('getTasks') ?? mockTasks;
  },
  getOverdueTasks(): Task[] {
    return callBridge<Task[]>('getOverdueTasks') ?? mockTasks.filter(t => !t.isCompleted && t.dueDate && t.dueDate < todayMs);
  },
  getTodayTasks(): Task[] {
    return callBridge<Task[]>('getTodayTasks') ?? mockTasks.filter(t => !t.isCompleted && t.dueDate && t.dueDate >= todayMs && t.dueDate < todayMs + dayMs);
  },
  getPlannedTasks(): Task[] {
    return callBridge<Task[]>('getPlannedTasks') ?? [];
  },
  getCompletedToday(): Task[] {
    return callBridge<Task[]>('getCompletedToday') ?? mockTasks.filter(t => t.isCompleted && t.completedAt && t.completedAt >= todayMs);
  },
  getTasksNotInToday(): Task[] {
    return callBridge<Task[]>('getTasksNotInToday') ?? mockTasks.filter(t => !t.isCompleted && (!t.dueDate || t.dueDate >= todayMs + dayMs));
  },
  getSubtasks(parentTaskId: number): Task[] {
    return callBridge<Task[]>('getSubtasks', parentTaskId) ?? [];
  },
  getTaskTags(taskId: number): Tag[] {
    return callBridge<Tag[]>('getTaskTags', taskId) ?? (mockTaskTags[taskId] || []);
  },
  completeTask(taskId: number): void {
    callBridgeVoid('completeTask', taskId);
  },
  uncompleteTask(taskId: number): void {
    callBridgeVoid('uncompleteTask', taskId);
  },
  deleteTask(taskId: number): void {
    callBridgeVoid('deleteTask', taskId);
  },
  addTask(task: Partial<Task>): void {
    callBridgeVoid('addTask', JSON.stringify(task));
  },
  planForToday(taskId: number): void {
    callBridgeVoid('planForToday', taskId);
  },
  removeFromToday(taskId: number): void {
    callBridgeVoid('removeFromToday', taskId);
  },

  // Projects
  getProjects(): Project[] {
    return callBridge<Project[]>('getProjects') ?? mockProjects;
  },
  addProject(project: Partial<Project>): void {
    callBridgeVoid('addProject', JSON.stringify(project));
  },
  updateProject(project: Partial<Project>): void {
    callBridgeVoid('updateProject', JSON.stringify(project));
  },
  deleteProject(projectId: number): void {
    callBridgeVoid('deleteProject', projectId);
  },

  // Habits
  getHabitsWithStatus(): HabitWithStatus[] {
    return callBridge<HabitWithStatus[]>('getHabitsWithStatus') ?? mockHabits;
  },
  toggleHabitCompletion(habitId: number, isCompleted: boolean): void {
    callBridgeVoid('toggleHabitCompletion', habitId, isCompleted);
  },
  deleteHabit(habitId: number): void {
    callBridgeVoid('deleteHabit', habitId);
  },

  // Tags
  getTags(): Tag[] {
    return callBridge<Tag[]>('getTags') ?? mockTags;
  },

  // Settings
  getTheme(): string {
    return callBridge<string>('getTheme') ?? 'dark';
  },
  setTheme(mode: string): void {
    callBridgeVoid('setTheme', mode);
  },
  getAccentColor(): string {
    return callBridge<string>('getAccentColor') ?? '#4A90D9';
  },
  setAccentColor(color: string): void {
    callBridgeVoid('setAccentColor', color);
  },
  exportJson(): void { callBridgeVoid('exportJson'); },
  exportCsv(): void { callBridgeVoid('exportCsv'); },
  importJson(): void { callBridgeVoid('importJson'); },
  syncNow(): void { callBridgeVoid('syncNow'); },
  signIn(): void { callBridgeVoid('signIn'); },
  signOut(): void { callBridgeVoid('signOut'); },
  getAuthState(): { signedIn: boolean; email?: string } {
    return callBridge<{ signedIn: boolean; email?: string }>('getAuthState') ?? { signedIn: false };
  },
  getVersion(): string {
    return callBridge<string>('getVersion') ?? 'v0.5.0';
  },

  // Navigation
  navigate(route: string): void {
    callBridgeVoid('navigate', route);
  },
};
