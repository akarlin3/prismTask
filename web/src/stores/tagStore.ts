import { create } from 'zustand';
import type { Tag, TagCreate, TagUpdate } from '@/types/tag';
import * as firestoreTags from '@/api/firestore/tags';
import type { Unsubscribe } from 'firebase/firestore';

interface TagState {
  tags: Tag[];
  isLoading: boolean;
  error: string | null;

  fetchTags: () => Promise<void>;
  createTag: (data: TagCreate) => Promise<Tag>;
  updateTag: (tagId: string, data: TagUpdate) => Promise<Tag>;
  deleteTag: (tagId: string) => Promise<void>;
  subscribeToTags: (uid: string) => Unsubscribe;
  clearError: () => void;
}

import { getFirebaseUid } from '@/stores/firebaseUid';

function getUid(): string {
  return getFirebaseUid();
}

export const useTagStore = create<TagState>((set) => ({
  tags: [],
  isLoading: false,
  error: null,

  fetchTags: async () => {
    set({ isLoading: true, error: null });
    try {
      const uid = getUid();
      const tags = await firestoreTags.getTags(uid);
      set({ tags, isLoading: false });
    } catch (e) {
      set({ error: (e as Error).message, isLoading: false });
    }
  },

  createTag: async (data) => {
    const uid = getUid();
    const tag = await firestoreTags.createTag(uid, data);
    set((state) => ({ tags: [...state.tags, tag] }));
    return tag;
  },

  updateTag: async (tagId, data) => {
    const uid = getUid();
    const updated = await firestoreTags.updateTag(uid, tagId, data);
    set((state) => ({
      tags: state.tags.map((t) => (t.id === tagId ? updated : t)),
    }));
    return updated;
  },

  deleteTag: async (tagId) => {
    const uid = getUid();
    await firestoreTags.deleteTag(uid, tagId);
    set((state) => ({
      tags: state.tags.filter((t) => t.id !== tagId),
    }));
  },

  subscribeToTags: (uid: string) => {
    return firestoreTags.subscribeToTags(uid, (tags) => {
      set({ tags });
    });
  },

  clearError: () => set({ error: null }),
}));
