import { getStoredToken, storeToken, clearToken } from "../auth/tokenStore";
import { refreshApi } from "./authApi";

/**
 * Fetch wrapper:
 * - Attaches Authorization: Bearer <accessToken>
 * - On 401, tries POST /auth/refresh once (uses HttpOnly refresh_token cookie)
 * - If refresh succeeds -> retry original request once
 * - If refresh fails -> clears token (forces login)
 */

let refreshInFlight: Promise<string | null> | null = null;

async function ensureFreshAccessToken(): Promise<string | null> {
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    try {
      const res = await refreshApi();
      if (!res?.accessToken) return null;
      storeToken(res.accessToken);
      return res.accessToken;
    } catch {
      clearToken();
      return null;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const token = getStoredToken();
  const headers = new Headers(init.headers || {});
  if (token) headers.set("Authorization", `Bearer ${token}`);

  // Always send cookies (refresh_token)
  const firstRes = await fetch(input, {
    ...init,
    headers,
    credentials: "include",
  });

  if (firstRes.status !== 401) return firstRes;

  // 401 -> attempt refresh once
  const newToken = await ensureFreshAccessToken();
  if (!newToken) return firstRes;

  const retryHeaders = new Headers(init.headers || {});
  retryHeaders.set("Authorization", `Bearer ${newToken}`);

  return fetch(input, {
    ...init,
    headers: retryHeaders,
    credentials: "include",
  });
}

export async function apiJson<T>(url: string, init: RequestInit = {}): Promise<T> {
  const res = await apiFetch(url, init);
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `${res.status} ${res.statusText}`);
  }
  const text = await res.text();
  if (!text) return {} as T;
  return JSON.parse(text) as T;
}
