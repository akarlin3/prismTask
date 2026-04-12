import { create } from 'zustand';
import type { Tag, TagCreate, TagUpdate } from '@/types/tag';
import { tagsApi } from '@/api/tags';

interface TagState {
  tags: Tag[];
  isLoading: boolean;
  error: string | null;

  fetchTags: () => Promise<void>;
  createTag: (data: TagCreate) => Promise<Tag>;
  updateTag: (tagId: number, data: TagUpdate) => Promise<Tag>;
  deleteTag: (tagId: number) => Promise<void>;
  clearError: () => void;
}

export const useTagStore = create<TagState>((set) => ({
  tags: [],
  isLoading: false,
  error: null,

  fetchTags: async () => {
    set({ isLoading: true, error: null });
    try {
      const tags = await tagsApi.list();
      set({ tags, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  createTag: async (data) => {
    const tag = await tagsApi.create(data);
    set((state) => ({ tags: [...state.tags, tag] }));
    return tag;
  },

  updateTag: async (tagId, data) => {
    const updated = await tagsApi.update(tagId, data);
    set((state) => ({
      tags: state.tags.map((t) => (t.id === tagId ? updated : t)),
    }));
    return updated;
  },

  deleteTag: async (tagId) => {
    await tagsApi.delete(tagId);
    set((state) => ({
      tags: state.tags.filter((t) => t.id !== tagId),
    }));
  },

  clearError: () => set({ error: null }),
}));
