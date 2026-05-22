import { apiJson } from "./http";

const API_BASE = (import.meta as any).env?.VITE_API_BASE_URL ?? "";

export type TestCaseRequest = {
  inputData: string;
  expectedOutput: string;
  isPublic: boolean;
};

export type TestCaseResponse = {
  id: number;
  inputData: string;
  expectedOutput: string;
  isPublic: boolean;
};

// Backend:
// - POST /api/testcases/{problemId}
// - GET  /api/testcases/problem/{problemId} (admin)
// - GET  /api/testcases/public/problem/{problemId} (team samples)

export function addTestCase(problemId: number, body: TestCaseRequest) {
  return apiJson<TestCaseResponse>(`${API_BASE}/api/testcases/${problemId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function getTestCases(problemId: number) {
  return apiJson<TestCaseResponse[]>(`${API_BASE}/api/testcases/problem/${problemId}`);
}

export function getPublicTestCases(problemId: number) {
  return apiJson<TestCaseResponse[]>(`${API_BASE}/api/testcases/public/problem/${problemId}`);
}
