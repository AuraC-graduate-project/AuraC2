// DTOs matching backend exactly

export type ContestLifecycleState = 'RUNNING' | 'PAUSED' | 'ENDED' | 'UPCOMING';

export interface  ContestResponse {
  id: number;
  title: string;
  description: string;
  startTime: string; // Scheduled start (ISO 8601 UTC) — planning/display only
  actualStartTime: string | null; // Set when admin manually starts the contest
  pausedAt: string | null; // Set while PAUSED
  totalPauseMillis: number; // Accumulated pause duration across all pause/resume cycles
  remainingMillis: number; // Pause-aware countdown value
  endTime: string | null; // Scheduled end: startTime + durationMinutes
  effectiveEndTime: string | null; // Live pause-aware end; null when UPCOMING or PAUSED
  durationMinutes: number;
  status: ContestLifecycleState;
  effectiveState: ContestLifecycleState;
  scoreboardFreezeMinutes: number | null;
  scoreboardFreezeTime: string | null;
  penaltyMinutes: number;
  scoreboardFrozen: boolean;
}

export type ContestUpdateReason =
  | 'CREATED'
  | 'MANUAL_START'
  | 'MANUAL_PAUSE'
  | 'MANUAL_RESUME'
  | 'MANUAL_END'
  | 'AUTO_START'
  | 'AUTO_END';

export interface ContestStreamSnapshot {
  active: ContestResponse | null;
  upcoming: ContestResponse | null;
  paused: ContestResponse | null;
  ended: ContestResponse[];
}

export interface ContestStreamUpdate {
  reason: ContestUpdateReason;
  snapshot: ContestResponse;
}

export interface ContestRequest {
  title: string;
  description: string;
  startTime: string; // ISO 8601
  durationMinutes: number;
  scoreboardFreezeMinutes?: number | null;
  penaltyMinutes?: number;
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
