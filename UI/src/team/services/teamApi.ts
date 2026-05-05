import {
    ContestResponse,
    ClarificationRequest,
    ClarificationResponse,
    ProblemResponse,
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
            if (!retry.ok) throw new Error("Unauthorized");
            return retry.json();
        }
    }

    if (!res.ok) throw new Error("Request failed");
    return res.json();
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
    const testCases = await apiFetch<TestCaseResponse[]>(
        `/api/testcases/problem/${problemId}`
    );
    return testCases.filter((testCase) => testCase.isPublic);
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
