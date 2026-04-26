export interface User {
  id: number;
  email: string;
  name: string;
  display_name?: string | null;
  avatar_url?: string | null;
  tier: 'FREE' | 'PRO';
  effective_tier?: 'FREE' | 'PRO';
  is_admin?: boolean;
  created_at: string;
  updated_at: string;
}

export interface FirebaseUser {
  uid: string;
  email: string | null;
  displayName: string | null;
  photoURL: string | null;
}

export interface UserCreate {
  email: string;
  name: string;
  password: string;
}

export interface UserLogin {
  email: string;
  password: string;
}

export interface AuthTokens {
  access_token: string;
  refresh_token: string;
  token_type: string;
}

export interface FirebaseTokenLogin {
  firebase_token: string;
  name?: string;
}

export interface TokenRefresh {
  refresh_token: string;
}

/**
 * Status of in-app account deletion. The backend's
 * `/api/v1/auth/me/deletion` family returns this shape from GET (status)
 * and POST (request — confirms the freshly-set state). When
 * `deletion_pending_at` is null, the account is active and the other
 * fields are also null.
 *
 * The 30-day grace window is server-driven: the user can sign back in
 * before `deletion_scheduled_for` to revert to active. After that
 * point the next sign-in (or admin sweep) permanently removes the
 * account; the web client never calls `/me/purge` directly.
 */
export interface DeletionStatus {
  deletion_pending_at: string | null;
  deletion_scheduled_for: string | null;
  deletion_initiated_from: 'android' | 'web' | 'email' | null;
}

/** Origin of a deletion request — passed in the POST body. */
export type DeletionInitiatedFrom = 'android' | 'web' | 'email';
