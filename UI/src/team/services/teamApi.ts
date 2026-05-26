import {
    ContestResponse,
    ClarificationRequest,
    ClarificationResponse,
    ProblemResponse,
    ScoreboardSnapshot,
    SubmissionResponse,
    TestCaseResponse,
} from "../../admin/types/api";

const API_BASE_URL =
    (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";

function url(path: string): string {
    if (!path.startsWith("/")) path = `/${path}`;
    return API_BASE_URL ? `${API_BASE_URL}${path}` : path;
}

/* ================= AUTH (SAME AS ADMIN) ================= */

const ACCESS_TOKEN_KEY = "access_token";

function getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
}

let refreshInFlight: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
    const res = await fetch(url("/auth/refresh"), {
        method: "POST",
        credentials: "include",
    });

    if (!res.ok) {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
        return null;
    }

    const data = await res.json();
    const token =
        data?.accessToken ?? data?.access_token ?? data?.token ?? null;

    if (token) {
        localStorage.setItem(ACCESS_TOKEN_KEY, token);
        return token;
    }

    return null;
}

async function ensureRefreshedOnce(): Promise<string | null> {
    if (!refreshInFlight) {
        refreshInFlight = refreshAccessToken().finally(() => {
            refreshInFlight = null;
        });
    }
    return refreshInFlight;
}

async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);

    const token = getAccessToken();
    if (token && !headers.has("Authorization")) {
        headers.set("Authorization", `Bearer ${token}`);
    }

    const res = await fetch(url(path), {
        ...init,
        headers,
        credentials: "include",
    });

    if (res.status === 401) {
        const newToken = await ensureRefreshedOnce();
        if (newToken) {
            headers.set("Authorization", `Bearer ${newToken}`);
            const retry = await fetch(url(path), {
                ...init,
                headers,
                credentials: "include",
            });
            if (!retry.ok) throw await responseError(retry);
            if (retry.status === 204) return undefined as T;
            return retry.json();
        }
    }

    if (!res.ok) throw await responseError(res);
    if (res.status === 204) return undefined as T;
    return res.json();
}

async function responseError(response: Response): Promise<Error> {
    try {
        const body = await response.json();
        return new Error(body?.message ?? `Request failed with status ${response.status}`);
    } catch {
        return new Error(`Request failed with status ${response.status}`);
    }
}

/* ================= TEAM ENDPOINTS ================= */

export function getActiveContest(): Promise<ContestResponse> {
    return apiFetch("/api/contest/active");
}

export function getUpcomingContest(): Promise<ContestResponse> {
    return apiFetch("/api/contest/upcoming");
}

export function getPausedContest(): Promise<ContestResponse> {
    return apiFetch("/api/contest/paused");
}

export function getEndedContests(): Promise<ContestResponse[]> {
    return apiFetch("/api/contest/ended");
}

export function getProblemsByContest(
    contestId: number
): Promise<ProblemResponse[]> {
    return apiFetch(`/api/problems/contest/${contestId}`);
}

export async function getPublicTestCasesForProblem(
    problemId: number
): Promise<TestCaseResponse[]> {
    return apiFetch<TestCaseResponse[]>(
        `/api/testcases/public/problem/${problemId}`
    );
}

export async function getMySubmissions(
    problemId: number
): Promise<SubmissionResponse[]> {
    return apiFetch<SubmissionResponse[]>(
        `/api/submissions/my?problemID=${problemId}`
    );
}

export async function getMyAllSubmissions(): Promise<SubmissionResponse[]> {
    return apiFetch<SubmissionResponse[]>("/api/submissions/my/all");
}

export function submitCode(body: {
    contestId: number;
    problemId: number;
    language: string;
    code: string;
}): Promise<SubmissionResponse> {
    return apiFetch("/api/submissions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });
}

export interface CustomTestCaseResponse {
    id: number;
    contestId: number;
    problemId: number;
    input: string;
    expectedOutput: string | null;
    createdAt: string | null;
    updatedAt: string | null;
}

export interface RunCaseResult {
    caseNumber: number;
    caseType: "PUBLIC_SAMPLE" | "CUSTOM" | string;
    caseId: number | null;
    label: string;
    input: string;
    expectedOutput: string | null;
    actualOutput: string | null;
    stderr: string | null;
    status: string;
    diagnostic: string | null;
    runtimeMillis: number | null;
    memoryKb: number | null;
}

export interface RunResponse {
    compileStatus: string;
    compileOutput: string | null;
    scoring: boolean;
    publicSampleCount: number;
    customTestCount: number;
    results: RunCaseResult[];
}

export function getCustomTests(problemId: number): Promise<CustomTestCaseResponse[]> {
    return apiFetch<CustomTestCaseResponse[]>(`/api/team/problems/${problemId}/custom-tests`);
}

export function createCustomTest(
    problemId: number,
    body: { input: string; expectedOutput?: string | null }
): Promise<CustomTestCaseResponse> {
    return apiFetch<CustomTestCaseResponse>(`/api/team/problems/${problemId}/custom-tests`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });
}

export function updateCustomTest(
    problemId: number,
    customTestId: number,
    body: { input: string; expectedOutput?: string | null }
): Promise<CustomTestCaseResponse> {
    return apiFetch<CustomTestCaseResponse>(`/api/team/problems/${problemId}/custom-tests/${customTestId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });
}

export function deleteCustomTest(problemId: number, customTestId: number): Promise<void> {
    return apiFetch<void>(`/api/team/problems/${problemId}/custom-tests/${customTestId}`, {
        method: "DELETE",
    });
}

export function runCode(
    problemId: number,
    body: {
        languageId: number;
        language: string;
        sourceCode: string;
        includePublicSamples: boolean;
        customTestCaseIds: number[];
    }
): Promise<RunResponse> {
    return apiFetch<RunResponse>(`/api/team/problems/${problemId}/run`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });
}

export function submitClarification(
    body: ClarificationRequest
): Promise<ClarificationResponse> {
    return apiFetch("/api/clarifications", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });
}

export function getMyClarifications(
    contestId: number
): Promise<ClarificationResponse[]> {
    return apiFetch(`/api/clarifications/my/${contestId}`);
}

export function getPublicScoreboard(contestId: number): Promise<ScoreboardSnapshot> {
    return apiFetch(`/api/scoreboard/contests/${contestId}`);
}
