import { apiJson } from "./http";

const API_BASE = (import.meta as any).env?.VITE_API_BASE_URL ?? "";

export type ProblemRequest = {
  contestId: number;
  title: string;
  description: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: "EASY" | "MEDIUM" | "HARD";
};

export type ProblemResponse = {
  id: number;
  title: string;
  description: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: string;
  contestId: number;
};

// Backend:
// - POST /api/problems
// - GET  /api/problems/{id}
// - GET  /api/problems/contest/{id}

export function createProblem(body: ProblemRequest) {
  return apiJson<ProblemResponse>(`${API_BASE}/api/problems`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function getProblem(id: number) {
  return apiJson<ProblemResponse>(`${API_BASE}/api/problems/${id}`);
}

export function getProblemsByContestId(contestId: number) {
  return apiJson<ProblemResponse[]>(`${API_BASE}/api/problems/contest/${contestId}`);
}
