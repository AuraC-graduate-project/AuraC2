export type LoginResponse = {
  accessToken: string;
  message?: string;
};

export type RefreshResponse = {
  accessToken: string;
};

const API_BASE = (import.meta as any).env?.VITE_API_BASE_URL ?? "";

/**
 * Backend (from your Spring controllers):
 * - POST {API_BASE}/auth/login    body: {username,password} -> {accessToken,message}
 * - POST {API_BASE}/auth/refresh  (uses HttpOnly cookie refresh_token) -> {accessToken}
 * - POST {API_BASE}/auth/logout   (clears refresh cookie) -> {message}
 *
 * IMPORTANT:
 * - We always set credentials:'include' so refresh_token cookie is sent/received.
 * - In dev, easiest is to use Vite proxy so API_BASE can be '' (same origin).
 */

async function jsonFetch<T>(url: string, init: RequestInit): Promise<T> {
  const res = await fetch(url, init);

  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`;
    try {
      const data = await res.json();
      if (data?.message) msg = String(data.message);
    } catch {}
    throw new Error(msg);
  }

  // Some endpoints may return empty body; handle safely
  const text = await res.text();
  if (!text) return {} as T;
  return JSON.parse(text) as T;
}

export async function loginApi(username: string, password: string): Promise<LoginResponse> {
  return jsonFetch<LoginResponse>(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ username, password }),
  });
}

export async function refreshApi(): Promise<RefreshResponse> {
  return jsonFetch<RefreshResponse>(`${API_BASE}/auth/refresh`, {
    method: "POST",
    credentials: "include",
  });
}

export async function logoutApi(): Promise<{ message?: string }> {
  return jsonFetch<{ message?: string }>(`${API_BASE}/auth/logout`, {
    method: "POST",
    credentials: "include",
  });
}
