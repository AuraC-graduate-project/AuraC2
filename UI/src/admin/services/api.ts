import {
  ContestResponse,
  ContestRequest,
  ContestUpdateRequest,
  ProblemRequest,
  ProblemUpdateRequest,
  ProblemResponse,
  RejudgeResponse,
  SubmissionResponse,
  UpdatePasswordRequest,
  UpdateUserNameRequest,
  UserResponse,
  TestCaseRequest,
  TestCaseUpdateRequest,
  TestCaseResponse,
  RegisterRequest,
  ClarificationRequest,
  ClarificationResponse,
  ReplyRequest,
  ScoreboardRevealResponse,
  ScoreboardSnapshot,
} from "../types/api";

/**
 * Base URL rules:
 * - If VITE_API_BASE_URL is set (e.g. "http://localhost:8080"), requests go directly there.
 * - Otherwise, requests use relative paths ("/api/..."), so you can rely on Vite proxy to avoid CORS.
 */
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";

function url(path: string): string {
  if (!path.startsWith("/")) path = `/${path}`;
  return API_BASE_URL ? `${API_BASE_URL}${path}` : path;
}

// -----------------------------
// Refresh-token / 401 handling
// -----------------------------

/**
 * Refresh token is stored in an HttpOnly cookie (server sets "refresh_token").
 * Access token is stored in localStorage and sent via Authorization: Bearer ...
 */
const ACCESS_TOKEN_KEY = "access_token";

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(token: string | null): void {
  if (!token) localStorage.removeItem(ACCESS_TOKEN_KEY);
  else localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

let refreshInFlight: Promise<string | null> | null = null;

async function parseBodySafe(response: Response): Promise<any> {
  const text = await response.text();
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/**
 * Calls POST /auth/refresh (sends refresh_token cookie), expects JSON containing a new access token.
 * We accept multiple common field names to be resilient to DTO naming:
 * - accessToken
 * - access_token
 * - token
 */
async function refreshAccessToken(): Promise<string | null> {
  const res = await fetch(url("/auth/refresh"), {
    method: "POST",
    credentials: "include",
  });

  if (!res.ok) {
    // Refresh failed -> clear access token so app behaves as logged-out
    setAccessToken(null);
    return null;
  }

  const data = await parseBodySafe(res);
  const newToken: string | undefined =
    data?.accessToken ?? data?.access_token ?? data?.token ?? data?.access;

  if (typeof newToken === "string" && newToken.length > 0) {
    setAccessToken(newToken);
    return newToken;
  }

  // If the backend returns some other shape, we can’t infer the token safely.
  setAccessToken(null);
  return null;
}

// Exported so the SSE hook can share the same coalesced refresh promise
// (refreshInFlight) as apiFetch — preventing a stream 401 and a REST 401
// from racing two concurrent /auth/refresh requests.
export async function ensureRefreshedOnce(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = refreshAccessToken().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

type ApiFetchOptions = Omit<RequestInit, "headers"> & {
  headers?: Record<string, string>;
  /** If true, skips attaching Authorization header (useful for login/refresh). */
  skipAuth?: boolean;
  /** If true, disables automatic refresh+retry behavior. */
  noRetry?: boolean;
};

async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const {
    headers: headerObj,
    skipAuth = false,
    noRetry = false,
    ...init
  } = options;

  const headers = new Headers(headerObj ?? {});

  // Attach access token (Bearer) if present
  if (!skipAuth) {
    const token = getAccessToken();
    if (token && !headers.has("Authorization")) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }

  // Default credentials include so refresh_token cookie works (and logout can clear it)
  const res = await fetch(url(path), {
    ...init,
    headers,
    credentials: "include",
  });

  // If first attempt is 401, try refresh once and re-send the request
  if (res.status === 401 && !noRetry) {
    const newToken = await ensureRefreshedOnce();
    if (newToken) {
      const retryHeaders = new Headers(headers);
      retryHeaders.set("Authorization", `Bearer ${newToken}`);
      const retryRes = await fetch(url(path), {
        ...init,
        headers: retryHeaders,
        credentials: "include",
      });

      if (!retryRes.ok) {
        const body = await parseBodySafe(retryRes);
        throw new Error(
          typeof body === "string" && body.length > 0
            ? body
            : `Request failed (${retryRes.status})`
        );
      }

      // Handle empty body
      const contentType = retryRes.headers.get("content-type") ?? "";
      if (!contentType.includes("application/json")) return undefined as T;
      return (await retryRes.json()) as T;
    }
  }

  if (!res.ok) {
    const body = await parseBodySafe(res);
    throw new Error(
      typeof body === "string" && body.length > 0 ? body : `Request failed (${res.status})`
    );
  }

  const contentType = res.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) return undefined as T;
  return (await res.json()) as T;
}

// -----------------------------
// Contest endpoints
// -----------------------------

//This is used when the UI wants to display the contest that is currently running.
export async function getActiveContest(): Promise<ContestResponse> {
  return apiFetch<ContestResponse>("/api/contest/active");
}
//Fetches the next contest that has not started yet, Used in the UI when the user selects the Upcoming tab.
export async function getUpcomingContest(): Promise<ContestResponse> {
  return apiFetch<ContestResponse>("/api/contest/upcoming");
}

export async function createContest(data: ContestRequest): Promise<ContestResponse> {
  return apiFetch<ContestResponse>("/api/contest", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function updateContestDetails(
  id: number,
  data: ContestUpdateRequest
): Promise<ContestResponse> {
  return apiFetch<ContestResponse>(`/api/contest/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function startContest(id: number): Promise<ContestResponse> {
  return apiFetch<ContestResponse>(`/api/contest/${id}/start`, { method: "PUT" });
}

export async function resumeContest(id: number): Promise<ContestResponse> {
  return apiFetch<ContestResponse>(`/api/contest/${id}/resume`, { method: "PUT" });
}

export async function pauseContest(id: number): Promise<ContestResponse> {
  return apiFetch<ContestResponse>(`/api/contest/${id}/pause`, { method: "PUT" });
}

export async function endContest(id: number, juryOverride = false): Promise<void> {
  const params = juryOverride ? "?juryOverride=true" : "";
  await apiFetch<void>(`/api/contest/${id}/end${params}`, { method: "PUT" });
}


export async function getPausedContest(): Promise<ContestResponse> {
  return apiFetch<ContestResponse>("/api/contest/paused");
}

export async function getEndedContests(): Promise<ContestResponse[]> {
  return apiFetch<ContestResponse[]>("/api/contest/ended");
}

// -----------------------------
// Problem endpoints
// -----------------------------

export async function createProblem(data: ProblemRequest): Promise<ProblemResponse> {
  return apiFetch<ProblemResponse>("/api/problems", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function getProblem(id: number): Promise<ProblemResponse> {
  return apiFetch<ProblemResponse>(`/api/problems/${id}`);
}

export async function getProblemsByContest(contestId: number): Promise<ProblemResponse[]> {
  return apiFetch<ProblemResponse[]>(`/api/problems/contest/${contestId}`);
}

export async function updateProblem(
  id: number,
  data: ProblemUpdateRequest
): Promise<ProblemResponse> {
  return apiFetch<ProblemResponse>(`/api/problems/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function deleteProblem(id: number): Promise<void> {
  await apiFetch<void>(`/api/problems/${id}`, { method: "DELETE" });
}

// -----------------------------
// Admin / Users
// -----------------------------

export async function getAllUsers(): Promise<UserResponse[]> {
  return apiFetch<UserResponse[]>(`/api/admin/users`);
}

export async function updateUserName(userId: number, newUsername: string): Promise<void> {
  // Send multiple keys to be resilient to backend DTO naming.
  const payload: UpdateUserNameRequest = {
    newUsername,
    username: newUsername,
    newName: newUsername,
  };
  await apiFetch<void>(`/api/admin/users/${userId}/name`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export async function updateUserPassword(userId: number, newPassword: string): Promise<void> {
  const payload: UpdatePasswordRequest = {
    newPassword,
    password: newPassword,
  };
  await apiFetch<void>(`/api/admin/users/${userId}/password`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export async function deleteUser(userId: number): Promise<void> {
  await apiFetch<void>(`/api/admin/users/${userId}`, { method: 'DELETE' });
}

// -----------------------------
// Admin / Submissions
// -----------------------------

export async function getAllSubmissions(): Promise<SubmissionResponse[]> {
  return apiFetch<SubmissionResponse[]>(`/api/admin/users/submissions`);
}

// -----------------------------
// Rejudge endpoints
// -----------------------------

export async function rejudgeSubmissions(submissionIds: number[]): Promise<RejudgeResponse> {
  return apiFetch<RejudgeResponse>("/api/admin/rejudge/submissions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ submissionIds }),
  });
}

export async function rejudgeProblem(problemId: number): Promise<RejudgeResponse> {
  return apiFetch<RejudgeResponse>(`/api/admin/rejudge/problem/${problemId}`, {
    method: "POST",
  });
}

export async function rejudgeContest(contestId: number): Promise<RejudgeResponse> {
  return apiFetch<RejudgeResponse>(`/api/admin/rejudge/contests/${contestId}`, {
    method: "POST",
  });
}

export async function forceRejudgeSubmissions(submissionIds: number[]): Promise<RejudgeResponse> {
  return apiFetch<RejudgeResponse>("/api/admin/rejudge/force/submissions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ submissionIds }),
  });
}

export async function forceRejudgeProblem(problemId: number): Promise<RejudgeResponse> {
  return apiFetch<RejudgeResponse>(`/api/admin/rejudge/force/problem/${problemId}`, {
    method: "POST",
  });
}

export async function forceRejudgeContest(contestId: number): Promise<RejudgeResponse> {
  return apiFetch<RejudgeResponse>(`/api/admin/rejudge/force/contests/${contestId}`, {
    method: "POST",
  });
}

// -----------------------------
// Scoreboard
// -----------------------------

export async function getPublicScoreboard(contestId: number): Promise<ScoreboardSnapshot> {
  return apiFetch<ScoreboardSnapshot>(`/api/scoreboard/contests/${contestId}`);
}

export async function getAdminScoreboard(contestId: number): Promise<ScoreboardSnapshot> {
  return apiFetch<ScoreboardSnapshot>(`/api/admin/scoreboard/contests/${contestId}`);
}

export async function getScoreboardRevealState(contestId: number): Promise<ScoreboardRevealResponse> {
  return apiFetch<ScoreboardRevealResponse>(`/api/admin/scoreboard/contests/${contestId}/reveal`);
}

export async function startScoreboardReveal(contestId: number): Promise<ScoreboardRevealResponse> {
  return apiFetch<ScoreboardRevealResponse>(`/api/admin/scoreboard/contests/${contestId}/reveal/start`, {
    method: "POST",
  });
}

export async function revealNextScoreboardCell(contestId: number): Promise<ScoreboardRevealResponse> {
  return apiFetch<ScoreboardRevealResponse>(`/api/admin/scoreboard/contests/${contestId}/reveal/next`, {
    method: "POST",
  });
}

export async function revealAllScoreboardCells(contestId: number): Promise<ScoreboardRevealResponse> {
  return apiFetch<ScoreboardRevealResponse>(`/api/admin/scoreboard/contests/${contestId}/reveal/all`, {
    method: "POST",
  });
}

export async function resetScoreboardReveal(contestId: number): Promise<ScoreboardRevealResponse> {
  return apiFetch<ScoreboardRevealResponse>(`/api/admin/scoreboard/contests/${contestId}/reveal/reset`, {
    method: "POST",
  });
}

// -----------------------------
// Test Case endpoints
// -----------------------------

export async function addTestCase(
  problemId: number,
  data: TestCaseRequest
): Promise<TestCaseResponse> {
  return apiFetch<TestCaseResponse>(`/api/testcases/${problemId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function getTestCasesForProblem(problemId: number): Promise<TestCaseResponse[]> {
  return apiFetch<TestCaseResponse[]>(`/api/testcases/problem/${problemId}`);
}

export async function updateTestCase(
  id: number,
  data: TestCaseUpdateRequest
): Promise<TestCaseResponse> {
  return apiFetch<TestCaseResponse>(`/api/testcases/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function deleteTestCase(id: number): Promise<void> {
  await apiFetch<void>(`/api/testcases/${id}`, { method: "DELETE" });
}

// -----------------------------
// Clarifications
// -----------------------------

export async function getAllClarifications(): Promise<ClarificationResponse[]> {
  return apiFetch<ClarificationResponse[]>("/api/clarifications/admin/all");
}

export async function getContestClarifications(
  contestId: number
): Promise<ClarificationResponse[]> {
  return apiFetch<ClarificationResponse[]>(
    `/api/clarifications/admin/contest/${contestId}`
  );
}

export async function submitClarification(
  data: ClarificationRequest
): Promise<ClarificationResponse> {
  return apiFetch<ClarificationResponse>("/api/clarifications", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function getMyClarifications(
  contestId: number
): Promise<ClarificationResponse[]> {
  return apiFetch<ClarificationResponse[]>(`/api/clarifications/my/${contestId}`);
}

export async function replyClarification(
  id: number,
  data: ReplyRequest
): Promise<ClarificationResponse> {
  return apiFetch<ClarificationResponse>(`/api/clarifications/admin/${id}/reply`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

// -----------------------------
// Auth endpoints
// -----------------------------

export async function registerUser(data: RegisterRequest): Promise<void> {
  await apiFetch<void>("/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

/**
 * Optional helper if you already receive an access token from somewhere else (e.g. login screen).
 */
export function bootstrapAccessToken(token: string): void {
  setAccessToken(token);
}

export async function logout(): Promise<void> {
  // Make sure refresh_token cookie is sent so server can clear it.
  await apiFetch<void>("/auth/logout", {
    method: "POST",
    skipAuth: false,
    // If you want to avoid refresh-loop during logout when access token is expired:
    noRetry: true,
  });
  setAccessToken(null);
}
