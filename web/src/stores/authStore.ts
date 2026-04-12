import { create } from 'zustand';
import type { User } from '@/types/auth';
import { authApi } from '@/api/auth';

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name: string) => Promise<void>;
  logout: () => void;
  refreshAccessToken: () => Promise<void>;
  hydrateFromStorage: () => Promise<void>;
  fetchUser: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  accessToken: null,
  refreshToken: null,
  isAuthenticated: false,
  isLoading: true,

  login: async (email, password) => {
    const tokens = await authApi.login({ email, password });
    localStorage.setItem('prismtask_access_token', tokens.access_token);
    localStorage.setItem('prismtask_refresh_token', tokens.refresh_token);
    set({
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token,
      isAuthenticated: true,
    });
    await get().fetchUser();
  },

  register: async (email, password, name) => {
    const tokens = await authApi.register({ email, name, password });
    localStorage.setItem('prismtask_access_token', tokens.access_token);
    localStorage.setItem('prismtask_refresh_token', tokens.refresh_token);
    set({
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token,
      isAuthenticated: true,
    });
    await get().fetchUser();
  },

  logout: () => {
    localStorage.removeItem('prismtask_access_token');
    localStorage.removeItem('prismtask_refresh_token');
    set({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
    });
  },

  refreshAccessToken: async () => {
    const refreshToken = get().refreshToken;
    if (!refreshToken) throw new Error('No refresh token');

    const tokens = await authApi.refresh(refreshToken);
    localStorage.setItem('prismtask_access_token', tokens.access_token);
    localStorage.setItem('prismtask_refresh_token', tokens.refresh_token);
    set({
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token,
    });
  },

  hydrateFromStorage: async () => {
    const accessToken = localStorage.getItem('prismtask_access_token');
    const refreshToken = localStorage.getItem('prismtask_refresh_token');

    if (accessToken) {
      set({
        accessToken,
        refreshToken,
        isAuthenticated: true,
      });
      try {
        await get().fetchUser();
      } catch {
        // Token expired — try refresh
        if (refreshToken) {
          try {
            await get().refreshAccessToken();
            await get().fetchUser();
          } catch {
            get().logout();
          }
        } else {
          get().logout();
        }
      }
    }
    set({ isLoading: false });
  },

  fetchUser: async () => {
    const user = await authApi.me();
    set({ user });
  },
}));
