import { apiJson } from "./http";

const API_BASE = (import.meta as any).env?.VITE_API_BASE_URL ?? "";

export type ContestResponse = {
  id: number;
  title: string;
  description: string;
  durationMinutes: number;
  status: string;
  effectiveState: string;
  startTime: string;
  endTime?: string | null;
  effectiveEndTime?: string | null;
};

export type ContestRequest = {
  title: string;
  description: string;
  startTime: string; // ISO string (LocalDateTime.toString())
  durationMinutes: number;
};

// Endpoints from your backend:
// - POST   /api/contest                 (ADMIN)
// - PUT    /api/contest/{id}/start      (ADMIN)
// - PUT    /api/contest/{id}/pause      (ADMIN)
// - PUT    /api/contest/{id}/end        (ADMIN)
// - GET    /api/contest/active
// - GET    /api/contest/upcoming
// - GET    /api/contest/paused
// - GET    /api/contest/ended

export function createContest(body: ContestRequest) {
  return apiJson<ContestResponse>(`${API_BASE}/api/contest`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function startContest(id: number) {
  return apiJson<ContestResponse>(`${API_BASE}/api/contest/${id}/start`, { method: "PUT" });
}

export function pauseContest(id: number) {
  return apiJson<ContestResponse>(`${API_BASE}/api/contest/${id}/pause`, { method: "PUT" });
}

export function endContest(id: number) {
  return apiJson<ContestResponse>(`${API_BASE}/api/contest/${id}/end`, { method: "PUT" });
}

export function getActiveContest() {
  return apiJson<ContestResponse>(`${API_BASE}/api/contest/active`);
}

export function getUpcomingContests() {
  return apiJson<ContestResponse[]>(`${API_BASE}/api/contest/upcoming`);
}

export function getPausedContest() {
  return apiJson<ContestResponse>(`${API_BASE}/api/contest/paused`);
}

export function getEndedContests() {
  return apiJson<ContestResponse[]>(`${API_BASE}/api/contest/ended`);
}
