export interface User {
  id: number;
  email: string;
  name: string;
  display_name?: string | null;
  avatar_url?: string | null;
  tier: 'FREE' | 'PRO' | 'PREMIUM' | 'ULTRA';
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

export interface TokenRefresh {
  refresh_token: string;
}
