import type {
  ScoreboardMetadata,
  ScoreboardRevealResponse,
  ScoreboardRow,
  ScoreboardSnapshot,
  ScoreboardUpdatePayload,
} from "../admin/types/api";

export function scoreboardMetadata(
  overrides: Partial<ScoreboardMetadata> = {}
): ScoreboardMetadata {
  return {
    contestId: 1,
    contestTitle: "ICPC Local",
    audience: "PUBLIC",
    version: 1,
    generatedAt: "2026-05-15T10:00:00Z",
    contestStatus: "RUNNING",
    effectiveState: "RUNNING",
    adminLive: false,
    scoreboardFrozen: false,
    freezeTime: null,
    freezeMinutes: 60,
    penaltyMinutes: 20,
    revealStatus: "NOT_STARTED",
    revealedCells: 0,
    totalHiddenCells: 0,
    problemColumns: [
      { problemId: 10, label: "A", title: "Warmup" },
      { problemId: 20, label: "B", title: "Graphs" },
    ],
    ...overrides,
  };
}

export function scoreboardRow(overrides: Partial<ScoreboardRow> = {}): ScoreboardRow {
  return {
    rank: 1,
    teamId: 100,
    teamName: "alpha",
    solvedCount: 1,
    totalPenalty: 42,
    problemCells: [
      {
        problemId: 10,
        label: "A",
        solved: true,
        attempts: 2,
        wrongAttempts: 1,
        solvedTimeMinutes: 22,
        penalty: 42,
        firstToSolve: true,
        hidden: false,
        revealed: false,
      },
      {
        problemId: 20,
        label: "B",
        solved: false,
        attempts: 0,
        wrongAttempts: 0,
        solvedTimeMinutes: null,
        penalty: null,
        firstToSolve: false,
        hidden: true,
        revealed: false,
      },
    ],
    ...overrides,
  };
}

export function scoreboardSnapshot(
  overrides: {
    metadata?: Partial<ScoreboardMetadata>;
    rows?: ScoreboardRow[];
  } = {}
): ScoreboardSnapshot {
  return {
    metadata: scoreboardMetadata(overrides.metadata),
    rows: overrides.rows ?? [scoreboardRow()],
  };
}

export function scoreboardPayload(
  overrides: Partial<Omit<ScoreboardUpdatePayload, "metadata">> & {
    metadata?: Partial<ScoreboardMetadata>;
  } = {}
): ScoreboardUpdatePayload {
  const { metadata: metadataOverrides, ...payloadOverrides } = overrides;
  const metadata = scoreboardMetadata({ version: 2, ...(metadataOverrides ?? {}) });
  return {
    eventType: "scoreboard-update",
    reason: "SUBMISSION_FINALIZED_ACCEPTED",
    contestId: 1,
    version: metadata.version,
    previousVersion: metadata.version - 1,
    fullSnapshot: false,
    changedTeamIds: [100],
    changedRows: [scoreboardRow({ totalPenalty: 44 })],
    metadata,
    snapshot: null,
    ...payloadOverrides,
  };
}

export function revealResponse(overrides: Partial<ScoreboardRevealResponse> = {}): ScoreboardRevealResponse {
  return {
    contestId: 1,
    status: "NOT_STARTED",
    totalCells: 2,
    revealedCells: 0,
    nextTeamId: 100,
    nextProblemId: 20,
    startedAt: null,
    updatedAt: null,
    completedAt: null,
    ...overrides,
  };
}
