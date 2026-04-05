// Data models matching Android entities

export interface Task {
  id: number;
  title: string;
  description?: string;
  dueDate?: number;
  dueTime?: number;
  priority: number; // 0=None, 1=Low, 2=Med, 3=High, 4=Urgent
  isCompleted: boolean;
  projectId?: number;
  parentTaskId?: number;
  recurrenceRule?: string;
  reminderOffset?: number;
  createdAt: number;
  updatedAt: number;
  completedAt?: number;
  archivedAt?: number;
  notes?: string;
  plannedDate?: number;
  estimatedDuration?: number;
  scheduledStartTime?: number;
}

export interface Project {
  id: number;
  name: string;
  color: string;
  icon: string;
  createdAt: number;
  updatedAt: number;
  taskCount?: number;
}

export interface Habit {
  id: number;
  name: string;
  description?: string;
  targetFrequency: number;
  frequencyPeriod: string;
  activeDays?: string;
  color: string;
  icon: string;
  reminderTime?: number;
  sortOrder: number;
  isArchived: boolean;
  createDailyTask: boolean;
  category?: string;
  createdAt: number;
  updatedAt: number;
}

export interface HabitWithStatus {
  habit: Habit;
  isCompletedToday: boolean;
  currentStreak: number;
  completionsThisWeek: number;
}

export interface Tag {
  id: number;
  name: string;
  color: string;
  createdAt: number;
}

export interface HabitCompletion {
  id: number;
  habitId: number;
  completedDate: number;
  completedAt: number;
  notes?: string;
}

export type SortOption = 'DUE_DATE' | 'PRIORITY' | 'URGENCY' | 'CREATED' | 'ALPHABETICAL';
export type ViewMode = 'UPCOMING' | 'LIST' | 'WEEK' | 'MONTH';

export interface TaskFilter {
  showCompleted: boolean;
  showArchived: boolean;
  selectedProjectIds: number[];
  selectedPriorities: number[];
  selectedTagIds: number[];
  tagFilterMode: 'ANY' | 'ALL';
  searchQuery: string;
}

// Bridge interface for Android communication
export interface AndroidBridge {
  // Task operations
  getTasks(): string;
  getTasksByProject(projectId: number): string;
  getSubtasks(parentTaskId: number): string;
  getOverdueTasks(): string;
  getTodayTasks(): string;
  getPlannedTasks(): string;
  getCompletedToday(): string;
  getTasksNotInToday(): string;
  addTask(json: string): void;
  updateTask(json: string): void;
  completeTask(taskId: number): void;
  uncompleteTask(taskId: number): void;
  deleteTask(taskId: number): void;
  planForToday(taskId: number): void;
  removeFromToday(taskId: number): void;
  getTaskTags(taskId: number): string;

  // Project operations
  getProjects(): string;
  addProject(json: string): void;
  updateProject(json: string): void;
  deleteProject(projectId: number): void;

  // Habit operations
  getHabitsWithStatus(): string;
  addHabit(json: string): void;
  updateHabit(json: string): void;
  deleteHabit(habitId: number): void;
  toggleHabitCompletion(habitId: number, isCompleted: boolean): void;

  // Tag operations
  getTags(): string;
  addTag(json: string): void;
  deleteTag(tagId: number): void;

  // Settings
  getTheme(): string;
  setTheme(mode: string): void;
  getAccentColor(): string;
  setAccentColor(color: string): void;
  exportJson(): void;
  exportCsv(): void;
  importJson(): void;
  syncNow(): void;
  getAuthState(): string;
  signIn(): void;
  signOut(): void;
  getVersion(): string;

  // Navigation
  navigate(route: string): void;
}

declare global {
  interface Window {
    AndroidBridge?: AndroidBridge;
    updateData?: (type: string, json: string) => void;
  }
}
