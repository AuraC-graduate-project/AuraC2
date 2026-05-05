// DTOs matching backend exactly

export interface ContestResponse {
  id: number;
  title: string;
  description: string;
  startTime: string; // ISO 8601
  durationMinutes: number;
  status: 'RUNNING' | 'PAUSED' | 'ENDED' | 'UPCOMING';
}

export interface ContestRequest {
  title: string;
  description: string;
  startTime: string; // ISO 8601
  durationMinutes: number;
}

export interface ContestUpdateRequest {
  title: string;
  startTime: string; // ISO 8601
  durationMinutes: number;
}

export interface ProblemRequest {
  contestId: number;
  title: string;
  description: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
}

export interface ProblemUpdateRequest {
  title: string;
  description: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
}

export interface ProblemResponse {
  id: number;
  contestId: number;
  title: string;
  description: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
}

export interface TestCaseRequest {
  inputData: string;
  expectedOutput: string;
  isPublic: boolean;
}

export interface TestCaseUpdateRequest {
  inputData: string;
  expectedOutput: string;
  isPublic: boolean;
}

export interface TestCaseResponse {
  id: number;
  problemId: number;
  inputData: string;
  expectedOutput: string;
  isPublic: boolean;
}

export type ClarificationStatus = 'PENDING' | 'ANSWERED' | 'CLOSED';
export type ClarificationType = 'PRIVATE' | 'PUBLIC';
export type StandardReply =
  | 'NO_COMMENT'
  | 'READ_PROBLEM_STATEMENT_CAREFULLY'
  | 'YES'
  | 'NO'
  | 'ANSWERED'
  | 'CUSTOM';

export interface ClarificationRequest {
  contestId: number;
  problemId: number | null;
  question: string;
}

export interface ReplyRequest {
  standardReply: StandardReply | null;
  reply: string | null;
  replyType: ClarificationType;
}

export interface ClarificationResponse {
  id: number;
  contestId: number;
  problemId: number | null;
  problemTitle: string | null;
  question: string;
  standardReply: StandardReply | null;
  reply: string | null;
  status: ClarificationStatus;
  replyType: ClarificationType | null;
  createdAt: string;
  repliedAt: string | null;
  username: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  role: 'TEAM' | 'ADMIN';
}

// -----------------------------
// Admin / Users
// -----------------------------

export interface UserResponse {
  id: number;
  username: string;
  role: string;
}

export interface UpdateUserNameRequest {
  // Backend DTO name is unknown; we send multiple keys to be resilient.
  newUsername?: string;
  username?: string;
  newName?: string;
}

export interface UpdatePasswordRequest {
  // Backend DTO name is unknown; we send multiple keys to be resilient.
  newPassword?: string;
  password?: string;
}

// -----------------------------
// Submissions
// -----------------------------

export type Verdict =
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TLE'
  | 'RUNTIME_ERROR'
  | 'COMPILATION_ERROR'
  | 'INTERNAL_ERROR'
  | 'PENDING'
  | 'RUNNING';

export interface SubmissionResponse {
  id: number;
  contestId: number;
  problemId: number;
  userId: number;
  language: string;
  code: string;
  verdict: Verdict | string;
  createdAt: string; // ISO
  executionTime: number | null;
  memoryUsage: number | null;
}
