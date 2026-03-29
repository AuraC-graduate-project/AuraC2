import { apiJson } from "./http";

const API_BASE = (import.meta as any).env?.VITE_API_BASE_URL ?? "";

export type SubmissionRequest = {
  contestId: number;
  problemId: number;
  language: string;
  code: string;
};

export type SubmissionResponse = {
  id: number;
  contestId: number;
  problemId: number;
  userId: number;
  language: string;
  code: string;
  verdict: string;
  createdAt: string;
  executionTime?: number;
  memoryUsage?: number;
};

// Backend:
// - POST /api/submissions
// - GET  /api/submissions/{id}

export function submitCode(body: SubmissionRequest) {
  return apiJson<SubmissionResponse>(`${API_BASE}/api/submissions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function getSubmission(id: number) {
  return apiJson<SubmissionResponse>(`${API_BASE}/api/submissions/${id}`);
}
