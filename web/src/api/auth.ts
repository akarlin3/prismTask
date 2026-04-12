import apiClient from './client';
import type { AuthTokens, User, UserCreate, UserLogin } from '@/types/auth';

export const authApi = {
  login(credentials: UserLogin): Promise<AuthTokens> {
    return apiClient.post('/auth/login', credentials).then((r) => r.data);
  },

  register(data: UserCreate): Promise<AuthTokens> {
    return apiClient.post('/auth/register', data).then((r) => r.data);
  },

  refresh(refreshToken: string): Promise<AuthTokens> {
    return apiClient
      .post('/auth/refresh', { refresh_token: refreshToken })
      .then((r) => r.data);
  },

  me(): Promise<User> {
    return apiClient.get('/auth/me').then((r) => r.data);
  },

  updateTier(tier: string): Promise<User> {
    return apiClient.patch('/auth/me/tier', { tier }).then((r) => r.data);
  },
};
