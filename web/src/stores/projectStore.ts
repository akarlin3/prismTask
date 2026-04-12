import { create } from 'zustand';
import type { Project, ProjectCreate, ProjectUpdate, ProjectDetail } from '@/types/project';
import type { Goal, GoalCreate, GoalUpdate, GoalDetail } from '@/types/goal';
import { projectsApi } from '@/api/projects';
import { goalsApi } from '@/api/goals';

interface ProjectState {
  projects: Project[];
  goals: Goal[];
  selectedProject: ProjectDetail | null;
  selectedGoal: GoalDetail | null;
  isLoading: boolean;
  error: string | null;

  // Goals
  fetchGoals: () => Promise<void>;
  fetchGoal: (goalId: number) => Promise<GoalDetail>;
  createGoal: (data: GoalCreate) => Promise<Goal>;
  updateGoal: (goalId: number, data: GoalUpdate) => Promise<Goal>;
  deleteGoal: (goalId: number) => Promise<void>;

  // Projects
  fetchByGoal: (goalId: number) => Promise<void>;
  fetchAllProjects: () => Promise<void>;
  fetchProject: (projectId: number) => Promise<ProjectDetail>;
  createProject: (goalId: number, data: ProjectCreate) => Promise<Project>;
  updateProject: (projectId: number, data: ProjectUpdate) => Promise<Project>;
  deleteProject: (projectId: number) => Promise<void>;

  setSelectedProject: (project: ProjectDetail | null) => void;
  clearError: () => void;
}

export const useProjectStore = create<ProjectState>((set) => ({
  projects: [],
  goals: [],
  selectedProject: null,
  selectedGoal: null,
  isLoading: false,
  error: null,

  fetchGoals: async () => {
    set({ isLoading: true, error: null });
    try {
      const goals = await goalsApi.list();
      set({ goals, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  fetchGoal: async (goalId) => {
    const goal = await goalsApi.get(goalId);
    set({ selectedGoal: goal });
    return goal;
  },

  createGoal: async (data) => {
    const goal = await goalsApi.create(data);
    set((state) => ({ goals: [...state.goals, goal] }));
    return goal;
  },

  updateGoal: async (goalId, data) => {
    const updated = await goalsApi.update(goalId, data);
    set((state) => ({
      goals: state.goals.map((g) => (g.id === goalId ? updated : g)),
    }));
    return updated;
  },

  deleteGoal: async (goalId) => {
    await goalsApi.delete(goalId);
    set((state) => ({
      goals: state.goals.filter((g) => g.id !== goalId),
      projects: state.projects.filter((p) => p.goal_id !== goalId),
    }));
  },

  fetchByGoal: async (goalId) => {
    set({ isLoading: true, error: null });
    try {
      const projects = await projectsApi.getByGoal(goalId);
      set((state) => {
        // Merge projects from this goal with existing projects from other goals
        const otherProjects = state.projects.filter((p) => p.goal_id !== goalId);
        return { projects: [...otherProjects, ...projects], isLoading: false };
      });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  fetchAllProjects: async () => {
    set({ isLoading: true, error: null });
    try {
      const goals = await goalsApi.list();
      const allProjects: Project[] = [];
      for (const goal of goals) {
        try {
          const projects = await projectsApi.getByGoal(goal.id);
          allProjects.push(...projects);
        } catch {
          // Skip goals with failed project fetches
        }
      }
      set({ goals, projects: allProjects, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  fetchProject: async (projectId) => {
    const project = await projectsApi.get(projectId);
    set({ selectedProject: project });
    return project;
  },

  createProject: async (goalId, data) => {
    const project = await projectsApi.create(goalId, data);
    set((state) => ({ projects: [...state.projects, project] }));
    return project;
  },

  updateProject: async (projectId, data) => {
    const updated = await projectsApi.update(projectId, data);
    set((state) => ({
      projects: state.projects.map((p) =>
        p.id === projectId ? updated : p,
      ),
    }));
    return updated;
  },

  deleteProject: async (projectId) => {
    await projectsApi.delete(projectId);
    set((state) => ({
      projects: state.projects.filter((p) => p.id !== projectId),
      selectedProject:
        state.selectedProject?.id === projectId ? null : state.selectedProject,
    }));
  },

  setSelectedProject: (project) => set({ selectedProject: project }),
  clearError: () => set({ error: null }),
}));
