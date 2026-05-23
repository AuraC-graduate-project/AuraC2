import { apiJson } from "./http";

const API_BASE = (import.meta as any).env?.VITE_API_BASE_URL ?? "";

export type ProblemRequest = {
  contestId: number;
  title: string;
  description: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  comparePolicy: ComparePolicy;
  floatAbsoluteEpsilon?: number | null;
  floatRelativeEpsilon?: number | null;
  validationMode?: ValidationMode;
  validatorEnabled?: boolean;
  validatorLanguageId?: number | null;
  validatorSource?: string;
  balloonColor: string;
};

export type ComparePolicy =
  | "EXACT"
  | "NORMALIZED_TEXT"
  | "TOKEN_NORMALIZED"
  | "FLOAT_TOLERANCE";

export type ValidationMode =
  | "BUILTIN_COMPARE_POLICY"
  | "CUSTOM_VALIDATOR";

export type ProblemResponse = {
  id: number;
  title: string;
  description: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: string;
  comparePolicy: ComparePolicy;
  floatAbsoluteEpsilon: number | null;
  floatRelativeEpsilon: number | null;
  validationMode: ValidationMode;
  validatorEnabled: boolean;
  validatorLanguageId: number | null;
  validatorSourceHash: string | null;
  contestId: number;
  balloonColor: string;
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
