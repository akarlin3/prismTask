import { create } from 'zustand';
import type { Project, ProjectCreate, ProjectUpdate } from '@/types/project';
import { projectsApi } from '@/api/projects';

interface ProjectState {
  projects: Project[];
  selectedProject: Project | null;
  isLoading: boolean;
  error: string | null;

  fetchByGoal: (goalId: number) => Promise<void>;
  createProject: (goalId: number, data: ProjectCreate) => Promise<Project>;
  updateProject: (projectId: number, data: ProjectUpdate) => Promise<Project>;
  deleteProject: (projectId: number) => Promise<void>;
  setSelectedProject: (project: Project | null) => void;
  clearError: () => void;
}

export const useProjectStore = create<ProjectState>((set) => ({
  projects: [],
  selectedProject: null,
  isLoading: false,
  error: null,

  fetchByGoal: async (goalId) => {
    set({ isLoading: true, error: null });
    try {
      const projects = await projectsApi.getByGoal(goalId);
      set({ projects, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
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
    }));
  },

  setSelectedProject: (project) => set({ selectedProject: project }),
  clearError: () => set({ error: null }),
}));
