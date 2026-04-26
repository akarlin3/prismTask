import axios from 'axios';
import { toast } from 'sonner';

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  'https://averytask-production.up.railway.app/api/v1';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor: attach JWT token
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('prismtask_access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor: handle 401 with token refresh + global error toasts
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
}> = [];

const processQueue = (error: unknown) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error);
    } else {
      promise.resolve(undefined);
    }
  });
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Network error (no response)
    if (!error.response) {
      toast.error('Network error. Check your connection.');
      return Promise.reject(error);
    }

    const status = error.response?.status;

    // Handle 401 with token refresh
    if (status === 401 && !originalRequest._retry) {
      // Don't retry auth endpoints
      if (
        originalRequest.url?.includes('/auth/login') ||
        originalRequest.url?.includes('/auth/register') ||
        originalRequest.url?.includes('/auth/refresh') ||
        originalRequest.url?.includes('/auth/firebase')
      ) {
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then(() => apiClient(originalRequest));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('prismtask_refresh_token');
      if (!refreshToken) {
        isRefreshing = false;
        localStorage.removeItem('prismtask_access_token');
        localStorage.removeItem('prismtask_refresh_token');
        // Let the auth store handle the redirect via React state.
        // Dynamic import avoids circular dependency (client → authStore → auth → client).
        const { useAuthStore } = await import('@/stores/authStore');
        useAuthStore.setState({ isAuthenticated: false, isLoading: false });
        return Promise.reject(error);
      }

      try {
        const response = await axios.post(`${API_BASE_URL}/auth/refresh`, {
          refresh_token: refreshToken,
        });

        const { access_token, refresh_token } = response.data;
        localStorage.setItem('prismtask_access_token', access_token);
        localStorage.setItem('prismtask_refresh_token', refresh_token);

        processQueue(null);
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError);
        localStorage.removeItem('prismtask_access_token');
        localStorage.removeItem('prismtask_refresh_token');
        // Let the auth store handle the redirect via React state.
        // Dynamic import avoids circular dependency (client → authStore → auth → client).
        const { useAuthStore } = await import('@/stores/authStore');
        useAuthStore.setState({ isAuthenticated: false, isLoading: false });
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // 410 Gone — backend's get_active_user middleware returns this when
    // the account is pending deletion. Any in-flight or subsequent
    // mutation request will hit this. Force a logout so the web client
    // doesn't sit in a zombie state with a soft-deleted account: clear
    // the JWT pair and flip the auth store to unauthenticated. The
    // user's confirmation flow already calls logout() once on success,
    // but a racing request can land afterwards — this is the safety net.
    if (status === 410) {
      localStorage.removeItem('prismtask_access_token');
      localStorage.removeItem('prismtask_refresh_token');
      const { useAuthStore } = await import('@/stores/authStore');
      useAuthStore.getState().logout();
      toast.error('Your account has been scheduled for deletion. You have been signed out.');
      return Promise.reject(error);
    }

    // Handle other HTTP errors with user-facing toasts
    if (status === 403) {
      toast.error("You don't have permission for this action.");
    } else if (status === 429) {
      const retryAfter = error.response.headers?.['retry-after'];
      const seconds = retryAfter ? parseInt(retryAfter, 10) : 30;
      toast.error(`Too many requests. Please wait ${seconds}s and try again.`);
    } else if (status >= 500) {
      toast.error('Server error. Please try again.');
    }
    // 422 errors are handled inline by forms — no global toast

    return Promise.reject(error);
  },
);

export default apiClient;
