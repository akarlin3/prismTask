import apiClient from './client';
import type {
  AuthTokens,
  DeletionInitiatedFrom,
  DeletionStatus,
  FirebaseTokenLogin,
  User,
  UserCreate,
  UserLogin,
} from '@/types/auth';

export const authApi = {
  login(credentials: UserLogin): Promise<AuthTokens> {
    return apiClient.post('/auth/login', credentials).then((r) => r.data);
  },

  register(data: UserCreate): Promise<AuthTokens> {
    return apiClient.post('/auth/register', data).then((r) => r.data);
  },

  firebaseLogin(data: FirebaseTokenLogin): Promise<AuthTokens> {
    return apiClient.post('/auth/firebase', data).then((r) => r.data);
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

  // Account deletion — mirrors the Android two-step typed-DELETE flow.
  // GET intentionally uses get_current_user (not get_active_user) so a
  // pending-deletion account can still inspect its own status.

  getDeletionStatus(): Promise<DeletionStatus> {
    return apiClient.get('/auth/me/deletion').then((r) => r.data);
  },

  requestAccountDeletion(
    initiatedFrom: DeletionInitiatedFrom = 'web',
  ): Promise<DeletionStatus> {
    return apiClient
      .post('/auth/me/deletion', { initiated_from: initiatedFrom })
      .then((r) => r.data);
  },

  cancelAccountDeletion(): Promise<DeletionStatus> {
    return apiClient.delete('/auth/me/deletion').then((r) => r.data);
  },
};
