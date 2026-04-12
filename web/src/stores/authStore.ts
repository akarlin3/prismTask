import { create } from 'zustand';
import {
  signInWithPopup,
  signOut,
  onAuthStateChanged,
  type User as FbUser,
} from 'firebase/auth';
import { firebaseAuth, googleProvider } from '@/lib/firebase';
import type { User, FirebaseUser } from '@/types/auth';
import { authApi } from '@/api/auth';
import { setFirebaseUid } from '@/stores/firebaseUid';

interface AuthState {
  /** FastAPI backend user (for NLP/AI features) */
  user: User | null;
  /** Firebase Auth user (for Firestore access) */
  firebaseUser: FirebaseUser | null;
  /** Firebase UID — the key for all Firestore paths */
  firebaseUid: string | null;
  /** JWT for FastAPI backend calls */
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  // Firebase Auth
  signInWithGoogle: () => Promise<void>;
  initFirebaseAuthListener: () => () => void;

  // Legacy email/password (calls FastAPI only)
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name: string) => Promise<void>;

  logout: () => void;
  refreshAccessToken: () => Promise<void>;
  hydrateFromStorage: () => Promise<void>;
  fetchUser: () => Promise<void>;
}

function toFirebaseUser(fbUser: FbUser): FirebaseUser {
  return {
    uid: fbUser.uid,
    email: fbUser.email,
    displayName: fbUser.displayName,
    photoURL: fbUser.photoURL,
  };
}

/**
 * After Firebase Auth succeeds, ensure the user also has a FastAPI account
 * and obtain a JWT for backend API calls (NLP, AI features).
 */
async function ensureBackendAccount(fbUser: FbUser): Promise<void> {
  const email = fbUser.email;
  if (!email) return;

  // Try login first
  try {
    const tokens = await authApi.login({
      email,
      password: `firebase_${fbUser.uid}`,
    });
    localStorage.setItem('prismtask_access_token', tokens.access_token);
    localStorage.setItem('prismtask_refresh_token', tokens.refresh_token);
    return;
  } catch {
    // Login failed — try to register
  }

  try {
    const tokens = await authApi.register({
      email,
      name: fbUser.displayName || email.split('@')[0],
      password: `firebase_${fbUser.uid}`,
    });
    localStorage.setItem('prismtask_access_token', tokens.access_token);
    localStorage.setItem('prismtask_refresh_token', tokens.refresh_token);
  } catch {
    // Backend unavailable — Firestore still works without JWT
    console.warn('Could not establish backend account. NLP/AI features may be unavailable.');
  }
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  firebaseUser: null,
  firebaseUid: null,
  accessToken: null,
  refreshToken: null,
  isAuthenticated: false,
  isLoading: true,

  signInWithGoogle: async () => {
    const result = await signInWithPopup(firebaseAuth, googleProvider);
    const fbUser = result.user;

    setFirebaseUid(fbUser.uid);
    set({
      firebaseUser: toFirebaseUser(fbUser),
      firebaseUid: fbUser.uid,
      isAuthenticated: true,
    });

    // Link with FastAPI backend for NLP/AI features
    await ensureBackendAccount(fbUser);

    const accessToken = localStorage.getItem('prismtask_access_token');
    const refreshToken = localStorage.getItem('prismtask_refresh_token');
    set({ accessToken, refreshToken });

    // Try to fetch the backend user profile
    try {
      await get().fetchUser();
    } catch {
      // Backend user fetch failed — non-critical
    }
  },

  initFirebaseAuthListener: () => {
    const unsubscribe = onAuthStateChanged(firebaseAuth, async (fbUser) => {
      if (fbUser) {
        setFirebaseUid(fbUser.uid);
        set({
          firebaseUser: toFirebaseUser(fbUser),
          firebaseUid: fbUser.uid,
          isAuthenticated: true,
          isLoading: false,
        });

        // Restore JWT tokens from localStorage
        const accessToken = localStorage.getItem('prismtask_access_token');
        const refreshToken = localStorage.getItem('prismtask_refresh_token');
        if (accessToken) {
          set({ accessToken, refreshToken });
          try {
            await get().fetchUser();
          } catch {
            // Non-critical
          }
        }
      } else {
        setFirebaseUid(null);
        set({
          firebaseUser: null,
          firebaseUid: null,
          user: null,
          accessToken: null,
          refreshToken: null,
          isAuthenticated: false,
          isLoading: false,
        });
      }
    });
    return unsubscribe;
  },

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
    signOut(firebaseAuth).catch(() => {});
    setFirebaseUid(null);
    localStorage.removeItem('prismtask_access_token');
    localStorage.removeItem('prismtask_refresh_token');
    set({
      user: null,
      firebaseUser: null,
      firebaseUid: null,
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
    // Firebase Auth listener handles Firebase state automatically.
    // Here we just restore JWT tokens for the backend.
    const accessToken = localStorage.getItem('prismtask_access_token');
    const refreshToken = localStorage.getItem('prismtask_refresh_token');

    if (accessToken) {
      set({ accessToken, refreshToken });
      try {
        await get().fetchUser();
      } catch {
        if (refreshToken) {
          try {
            await get().refreshAccessToken();
            await get().fetchUser();
          } catch {
            // Non-critical — Firestore works without JWT
          }
        }
      }
    }

    // If no Firebase user is signed in yet, mark loading as done.
    // The Firebase auth listener will update state when it fires.
    if (!firebaseAuth.currentUser) {
      set({ isLoading: false });
    }
  },

  fetchUser: async () => {
    const user = await authApi.me();
    set({ user });
  },
}));
