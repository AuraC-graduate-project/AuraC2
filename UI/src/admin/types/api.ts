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
  | 'UPDATED'
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
  description: string;
  startTime: string; // ISO 8601
  durationMinutes: number;
  scoreboardFreezeMinutes: number | null;
  penaltyMinutes: number;
}

export interface ProblemRequest {
  contestId: number;
  title: string;
  description: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  comparePolicy: ComparePolicy;
  floatAbsoluteEpsilon?: number | null;
  floatRelativeEpsilon?: number | null;
  validationMode?: ValidationMode;
  validatorEnabled?: boolean;
  validatorLanguageId?: number | null;
  validatorSource?: string;
  balloonColor: string;
}

export interface ProblemUpdateRequest {
  title: string;
  description: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  comparePolicy: ComparePolicy;
  floatAbsoluteEpsilon?: number | null;
  floatRelativeEpsilon?: number | null;
  validationMode?: ValidationMode;
  validatorEnabled?: boolean;
  validatorLanguageId?: number | null;
  validatorSource?: string;
  balloonColor: string;
}

export type ComparePolicy =
  | 'EXACT'
  | 'NORMALIZED_TEXT'
  | 'TOKEN_NORMALIZED'
  | 'FLOAT_TOLERANCE';

export type ValidationMode =
  | 'BUILTIN_COMPARE_POLICY'
  | 'CUSTOM_VALIDATOR';

export interface ProblemResponse {
  id: number;
  contestId: number;
  title: string;
  description: string;
  timeLimit: number;
  memoryLimit: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  comparePolicy: ComparePolicy;
  floatAbsoluteEpsilon: number | null;
  floatRelativeEpsilon: number | null;
  validationMode: ValidationMode;
  validatorEnabled: boolean;
  validatorLanguageId: number | null;
  validatorSourceHash: string | null;
  balloonColor: string;
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

export type ClarificationStreamEventType =
  | 'CLARIFICATION_CREATED'
  | 'CLARIFICATION_REPLIED'
  | 'CLARIFICATION_PUBLIC_ANSWERED';

export interface ClarificationStreamEvent {
  type: ClarificationStreamEventType;
  contestId: number;
  clarificationId: number;
  problemId: number | null;
  teamUserId: number;
  status: ClarificationStatus;
  replyType: ClarificationType | null;
  occurredAt: string;
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

export interface BulkTeamGenerationRequest {
  prefix: string;
  startNumber: number;
  endNumber: number;
  passwordLength: number;
}

export interface GeneratedTeamCredentialResponse {
  username: string;
  password: string;
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
  | 'PENDING_REJUDGE'
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

// -----------------------------
// Submission SSE
// -----------------------------

export type SubmissionStreamEventType =
  | 'CREATED'
  | 'RUNNING'
  | 'FINALIZED'
  | 'REJUDGE_QUEUED';

export interface SubmissionStreamEvent {
  eventType: SubmissionStreamEventType;
  submissionId: number;
  contestId: number;
  problemId: number;
  userId: number;
  username: string;
  verdict: Verdict | string;
  judgeRunId: number;
  executionTime: number | null;
  memoryUsage: number | null;
  createdAt: string;
  occurredAt: string;
}

// -----------------------------
// Scoreboard
// -----------------------------

export type ScoreboardAudience = 'ADMIN' | 'PUBLIC';
export type ScoreboardRevealStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';

export interface ScoreboardProblemColumn {
  problemId: number;
  label: string;
  title: string;
  balloonColor: string;
}

export interface ScoreboardMetadata {
  contestId: number;
  contestTitle: string;
  audience: ScoreboardAudience;
  version: number;
  generatedAt: string;
  contestStatus: ContestLifecycleState | string;
  effectiveState: ContestLifecycleState | string;
  adminLive: boolean;
  scoreboardFrozen: boolean;
  freezeTime: string | null;
  freezeMinutes: number | null;
  penaltyMinutes: number | null;
  revealStatus: ScoreboardRevealStatus;
  revealedCells: number;
  totalHiddenCells: number;
  problemColumns: ScoreboardProblemColumn[];
}

export interface ScoreboardProblemCell {
  problemId: number;
  label: string;
  solved: boolean;
  attempts: number;
  wrongAttempts: number;
  pendingCount: number;
  solvedTimeMinutes: number | null;
  penalty: number | null;
  firstToSolve: boolean;
  hidden: boolean;
  revealed: boolean;
  pending?: boolean;
}

export interface ScoreboardRow {
  rank: number;
  teamId: number;
  teamName: string;
  solvedCount: number;
  totalPenalty: number;
  problemCells: ScoreboardProblemCell[];
}

export interface ScoreboardSnapshot {
  metadata: ScoreboardMetadata;
  rows: ScoreboardRow[];
}

export interface ScoreboardUpdatePayload {
  eventType: 'scoreboard-update' | 'scoreboard-freeze' | 'scoreboard-reveal-step' | string;
  reason: string;
  contestId: number;
  version: number;
  previousVersion: number;
  fullSnapshot: boolean;
  changedTeamIds: number[];
  changedRows: ScoreboardRow[];
  metadata: ScoreboardMetadata;
  snapshot: ScoreboardSnapshot | null;
}

export interface ScoreboardRevealResponse {
  contestId: number;
  status: ScoreboardRevealStatus;
  totalCells: number;
  revealedCells: number;
  nextTeamId: number | null;
  nextProblemId: number | null;
  startedAt: string | null;
  updatedAt: string | null;
  completedAt: string | null;
}

// -----------------------------
// Rejudge
// -----------------------------

export interface RejudgeResponse {
  scope: string;
  scopeId: number | null;
  requestedCount: number;
  foundCount: number;
  queuedCount: number;
  skippedCount: number;
  queuedSubmissionIds: number[];
  skippedSubmissionIds: number[];
  missingSubmissionIds: number[];
}

// -----------------------------
// Hybrid oracle / generated tests
// -----------------------------

export interface OracleProgramRequest {
  languageId: number;
  source: string;
  active?: boolean;
  defaultTestCount?: number | null;
}

export interface OracleProgramResponse {
  id: number;
  problemId: number;
  languageId: number;
  sourceHash: string;
  active: boolean;
  defaultTestCount: number | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface GeneratedTestBatchRequest {
  testCount?: number | null;
  seed?: number | null;
  submissionId?: number | null;
}

export interface GeneratedTestPromotionRequest {
  generatedTestCaseIds: number[];
}

export type GeneratedTestBatchStatus = 'COMPLETED' | 'PARTIAL' | 'FAILED';

export type GeneratedTestCaseStatus =
  | 'GENERATED'
  | 'INVALID_INPUT'
  | 'GENERATOR_FAILED'
  | 'REFERENCE_FAILED';

export interface GeneratedTestCaseResponse {
  id: number;
  batchId: number;
  problemId: number;
  testNumber: number;
  seed: number;
  inputData: string | null;
  referenceOutput: string | null;
  status: GeneratedTestCaseStatus;
  promoted: boolean;
  promotedTestCaseId: number | null;
  diagnostic: string | null;
  createdAt: string | null;
}

export interface GeneratedTestBatchResponse {
  id: number;
  problemId: number;
  seed: number;
  generatorSourceHash: string;
  referenceSolutionSourceHash: string;
  status: GeneratedTestBatchStatus;
  requestedCount: number;
  generatedCount: number;
  invalidCount: number;
  counterexampleCount: number;
  diagnostic: string | null;
  createdAt: string | null;
  completedAt: string | null;
  testCases: GeneratedTestCaseResponse[];
}

export interface CounterexampleResponse {
  id: number;
  problemId: number;
  submissionId: number;
  generatedTestCaseId: number;
  judgeRunId: number;
  generatedInput: string;
  referenceOutput: string;
  teamOutput: string | null;
  verdict: Verdict | string;
  comparePolicy: ComparePolicy | string | null;
  validationMode: ValidationMode | string | null;
  diagnostic: string | null;
  promoted: boolean;
  promotedTestCaseId: number | null;
  createdAt: string | null;
}
