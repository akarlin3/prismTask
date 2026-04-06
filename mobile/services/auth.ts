import * as SecureStore from "expo-secure-store";
import axios from "axios";
import { API_BASE_URL } from "../constants/api";

const ACCESS_KEY = "access_token";
const REFRESH_KEY = "refresh_token";

export async function storeTokens(access: string, refresh: string) {
  await SecureStore.setItemAsync(ACCESS_KEY, access);
  await SecureStore.setItemAsync(REFRESH_KEY, refresh);
}

export async function getAccessToken(): Promise<string | null> {
  return SecureStore.getItemAsync(ACCESS_KEY);
}

export async function getRefreshToken(): Promise<string | null> {
  return SecureStore.getItemAsync(REFRESH_KEY);
}

export async function clearTokens() {
  await SecureStore.deleteItemAsync(ACCESS_KEY);
  await SecureStore.deleteItemAsync(REFRESH_KEY);
}

export async function refreshAccessToken(): Promise<string | null> {
  const refresh = await getRefreshToken();
  if (!refresh) return null;

  try {
    const resp = await axios.post(`${API_BASE_URL}/auth/refresh`, {
      refresh_token: refresh,
    });
    const { access_token, refresh_token } = resp.data;
    await storeTokens(access_token, refresh_token);
    return access_token;
  } catch {
    await clearTokens();
    return null;
  }
}
