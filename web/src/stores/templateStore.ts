import { create } from 'zustand';
import type {
  TaskTemplate,
  TemplateCreate,
  TemplateUpdate,
  TemplateUseRequest,
  TemplateUseResponse,
} from '@/types/template';
import { templatesApi } from '@/api/templates';

interface TemplateState {
  templates: TaskTemplate[];
  isLoading: boolean;
  error: string | null;

  fetch: (category?: string, sortBy?: string) => Promise<void>;
  create: (data: TemplateCreate) => Promise<TaskTemplate>;
  createFromTask: (taskId: number) => Promise<TaskTemplate>;
  update: (id: number, data: TemplateUpdate) => Promise<TaskTemplate>;
  remove: (id: number) => Promise<void>;
  use: (id: number, data?: TemplateUseRequest) => Promise<TemplateUseResponse>;
  clearError: () => void;
}

export const useTemplateStore = create<TemplateState>((set) => ({
  templates: [],
  isLoading: false,
  error: null,

  fetch: async (category?, sortBy?) => {
    set({ isLoading: true, error: null });
    try {
      const templates = await templatesApi.list(category, sortBy);
      set({ templates, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  create: async (data) => {
    const template = await templatesApi.create(data);
    set((state) => ({ templates: [...state.templates, template] }));
    return template;
  },

  createFromTask: async (taskId) => {
    const template = await templatesApi.createFromTask(taskId);
    set((state) => ({ templates: [...state.templates, template] }));
    return template;
  },

  update: async (id, data) => {
    const updated = await templatesApi.update(id, data);
    set((state) => ({
      templates: state.templates.map((t) => (t.id === id ? updated : t)),
    }));
    return updated;
  },

  remove: async (id) => {
    await templatesApi.delete(id);
    set((state) => ({
      templates: state.templates.filter((t) => t.id !== id),
    }));
  },

  use: async (id, data) => {
    const result = await templatesApi.use(id, data);
    // Increment usage count locally
    set((state) => ({
      templates: state.templates.map((t) =>
        t.id === id
          ? { ...t, usage_count: t.usage_count + 1, last_used_at: new Date().toISOString() }
          : t,
      ),
    }));
    return result;
  },

  clearError: () => set({ error: null }),
}));
