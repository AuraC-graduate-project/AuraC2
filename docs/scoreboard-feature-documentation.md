# AuraC2 Real-Time ICPC Scoreboard Documentation

Last updated: 2026-05-15.

## Overview

The scoreboard feature provides deterministic ICPC-style standings for AuraC2 contests with live updates through the existing shared Server-Sent Events infrastructure. It covers public/team standings, admin live standings, contest freeze behavior, post-contest reveal, rejudge invalidation, and zero-test-case finalization.

No WebSocket layer is used. Scoreboard streams are built on `shared.sse`.

## Backend Package Map

```text
backend/src/main/java/com/server/contestControl/contestServer/scoreboard
├── controller
│   ├── AdminScoreboardController
│   ├── AdminScoreboardStreamController
│   ├── ScoreboardController
│   └── ScoreboardStreamController
├── dto
│   ├── ScoreboardMetadata
│   ├── ScoreboardProblemCell
│   ├── ScoreboardRevealResponse
│   ├── ScoreboardRow
│   ├── ScoreboardSnapshot
│   └── ScoreboardUpdatePayload
├── entity
│   ├── ScoreboardRevealCell
│   └── ScoreboardRevealState
├── enums
│   ├── RevealStatus
│   └── ScoreboardAudience
├── exception
│   └── InvalidScoreboardRevealStateException
├── model
│   └── ScoreboardCellKey
├── repository
│   ├── ScoreboardRevealCellRepository
│   └── ScoreboardRevealStateRepository
├── service
│   ├── ScoreboardCalculator
│   ├── ScoreboardFreezePolicy
│   ├── ScoreboardRankingService
│   ├── ScoreboardRevealService
│   ├── ScoreboardService
│   └── ScoreboardVersionService
└── sse
    ├── AdminScoreboardSseRegistry
    ├── ScoreboardSseAdapter
    ├── ScoreboardSsePublisher
    └── ScoreboardSseRegistry
```

Related integration files:

- `submissionServer/event/SubmissionFinalizedEvent`
- `submissionServer/event/SubmissionRejudgeQueuedEvent`
- `submissionServer/service/callback/Judge0CallbackService`
- `submissionServer/service/rejudge/RejudgeService`
- `submissionServer/queue/submission/SubmissionConsumer`
- `authServer/config/SecurityConfiguration`
- `authServer/repository/UserRepository`
- `contestServer/repository/ProblemRepository`
- `submissionServer/repository/SubmissionRepository`

## Scoring Rules

Only submissions from users with `Role.TEAM` are counted.

For each team/problem cell:

- First accepted submission solves the problem.
- Solved time is the number of whole minutes from `contest.actualStartTime` to the first accepted submission's `createdAt`.
- Cell penalty is `solvedTime + wrongAttemptsBeforeFirstAccepted * contest.penaltyMinutes`.
- Wrong attempts are `WRONG_ANSWER`, `TLE`, `COMPILATION_ERROR`, and `RUNTIME_ERROR`.
- `INTERNAL_ERROR` is terminal for event flow but does not add wrong-attempt penalty.
- Attempts after the first accepted submission do not affect scoring.
- Active states such as `PENDING`, `RUNNING`, and `PENDING_REJUDGE` are ignored by scoring.

Ranking:

1. Higher solved count ranks first.
2. Lower total penalty breaks ties.
3. Equal solved count and penalty share the same rank.
4. Display order is stable by team username, then team id.

First-to-solve:

- Calculated per problem from visible accepted submissions.
- Admin view calculates from all live submissions.
- Public/team view calculates from visible submissions only, so post-freeze first-to-solve appears when the relevant cell is revealed.

## Freeze Behavior

Freeze is delegated to existing contest lifecycle logic through `ContestLifecycleService` and `ScoreboardFreezePolicy`, preserving pause-aware contest timing.

Public/team scoreboard:

- Uses live standings until the effective freeze time.
- During `RUNNING` or `PAUSED`, hides terminal submissions at or after freeze time when lifecycle reports the scoreboard is frozen.
- After `ENDED`, stays frozen until reveal completes.
- When reveal is reset, returns to the frozen snapshot.

Admin scoreboard:

- Always displays live standings.
- Includes metadata that indicates whether the official public scoreboard is currently frozen.

Hidden cells:

- A hidden cell is any team/problem cell with terminal team submissions at or after freeze time.
- Hidden cells are represented in metadata and problem-cell DTOs.
- Public/team streams receive updates with freeze metadata without exposing hidden post-freeze scoring until reveal.

## Reveal Mode

Reveal state is persisted with:

- `scoreboard_reveal_states`
- `scoreboard_reveal_cells`

Statuses:

- `NOT_STARTED`
- `IN_PROGRESS`
- `COMPLETED`

Rules:

- Reveal operations require the contest effective state to be `ENDED`.
- `start` rebuilds the hidden-cell queue from the frozen public snapshot.
- Reveal order walks the frozen board from lower ranks upward, then problem order within each row.
- Reveal unit is one team/problem cell.
- After each step, the public/team snapshot recalculates rows and ranks from the newly visible set.
- `all` reveals every hidden cell.
- `reset` hides all queued cells and returns public/team view to the frozen snapshot.

## REST API

Public/team endpoints:

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/scoreboard/contests/{contestId}` | Returns freeze-respected public/team scoreboard snapshot. |
| `GET` | `/api/scoreboard/contests/{contestId}/stream` | Opens public/team scoreboard SSE stream. |

Admin endpoints:

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/admin/scoreboard/contests/{contestId}` | Returns live admin scoreboard snapshot. |
| `GET` | `/api/admin/scoreboard/contests/{contestId}/stream` | Opens live admin scoreboard SSE stream. |
| `GET` | `/api/admin/scoreboard/contests/{contestId}/reveal` | Returns persisted reveal state and next cell metadata. |
| `POST` | `/api/admin/scoreboard/contests/{contestId}/reveal/start` | Starts reveal and builds the hidden-cell queue. |
| `POST` | `/api/admin/scoreboard/contests/{contestId}/reveal/next` | Reveals the next hidden team/problem cell. |
| `POST` | `/api/admin/scoreboard/contests/{contestId}/reveal/all` | Reveals all hidden cells. |
| `POST` | `/api/admin/scoreboard/contests/{contestId}/reveal/reset` | Resets reveal back to the frozen public/team view. |

Security:

- `/api/scoreboard/**` is public/team readable.
- `/api/admin/scoreboard/**` remains admin-only through `/api/admin/**` security and controller `@PreAuthorize("hasRole('ADMIN')")`.

## Snapshot Contract

`ScoreboardSnapshot`:

```json
{
  "metadata": {
    "contestId": 1,
    "contestTitle": "ICPC Local",
    "audience": "PUBLIC",
    "version": 12,
    "generatedAt": "2026-05-15T10:00:00Z",
    "contestStatus": "RUNNING",
    "effectiveState": "RUNNING",
    "adminLive": false,
    "scoreboardFrozen": true,
    "freezeTime": "2026-05-15T11:00:00Z",
    "freezeMinutes": 60,
    "penaltyMinutes": 20,
    "revealStatus": "IN_PROGRESS",
    "revealedCells": 3,
    "totalHiddenCells": 12,
    "problemColumns": [
      { "problemId": 10, "label": "A", "title": "Warmup" }
    ]
  },
  "rows": [
    {
      "rank": 1,
      "teamId": 100,
      "teamName": "alpha",
      "solvedCount": 2,
      "totalPenalty": 95,
      "problemCells": [
        {
          "problemId": 10,
          "label": "A",
          "solved": true,
          "attempts": 2,
          "wrongAttempts": 1,
          "solvedTimeMinutes": 75,
          "penalty": 95,
          "firstToSolve": true,
          "hidden": false,
          "revealed": false
        }
      ]
    }
  ]
}
```

## SSE Contract

Streams send an initial `snapshot` event, followed by update events:

- `scoreboard-update`
- `scoreboard-freeze`
- `scoreboard-reveal-step`
- shared `ping` keepalive events

`ScoreboardUpdatePayload`:

```json
{
  "eventType": "scoreboard-update",
  "reason": "SUBMISSION_FINALIZED_ACCEPTED",
  "contestId": 1,
  "version": 13,
  "previousVersion": 12,
  "fullSnapshot": false,
  "changedTeamIds": [100],
  "changedRows": [],
  "metadata": {},
  "snapshot": null
}
```

Client behavior:

- Apply `changedRows` by `teamId` and re-sort rows by rank.
- Highlight `changedTeamIds`.
- If `fullSnapshot` is true, replace the whole snapshot.
- If the client detects `version > currentVersion + 1`, refetch the REST snapshot.

## Event Flow

Accepted submission:

```text
Judge0 callback
  -> Submission verdict finalized as ACCEPTED
  -> SubmissionFinalizedEvent
  -> ScoreboardSseAdapter
  -> ScoreboardService recalculates admin snapshot
  -> ScoreboardService recalculates public/team snapshot with freeze/reveal policy
  -> ScoreboardSsePublisher emits scoreboard-update or scoreboard-freeze
  -> UI hook patches changed rows or refetches on version gap
```

Non-accepted final submission:

```text
Final verdict
  -> SubmissionFinalizedEvent
  -> Scoreboard recalculation
  -> Cell attempts can update, ranking changes only if scoring data changes
```

Zero test cases:

```text
SubmissionConsumer sees no test cases
  -> submission marked INTERNAL_ERROR
  -> submission final SSE emitted
  -> SubmissionFinalizedEvent emitted
  -> scoreboard stream stays consistent
```

Rejudge:

```text
Admin queues rejudge
  -> RejudgeService marks affected submissions PENDING_REJUDGE
  -> SubmissionRejudgeQueuedEvent
  -> scoreboard invalidates/recalculates
  -> Judge0 callbacks finalize new verdicts
  -> SubmissionFinalizedEvent
  -> scoreboard recalculates again
```

Reveal:

```text
Admin reveal action
  -> ScoreboardRevealService persists state/cell changes
  -> AdminScoreboardController publishes freeze/reveal SSE event
  -> public/team rows recalculate from newly visible cells
  -> admin stream remains live
```

Contest update/freeze transition:

```text
ContestUpdatedEvent
  -> ScoreboardSseAdapter
  -> public/team stream receives scoreboard-freeze when the public snapshot is frozen
  -> admin stream receives scoreboard-update with live data and freeze metadata
```

## Frontend Package Map

```text
UI/src/hooks/useScoreboardStream.ts
UI/src/components/scoreboard/ScoreboardTable.tsx
UI/src/admin/components/ScoreboardView.tsx
UI/src/team/components/Scoreboard.tsx
```

Modified integration files:

- `UI/src/admin/types/api.ts`
- `UI/src/admin/services/api.ts`
- `UI/src/team/services/teamApi.ts`
- `UI/src/admin/App.tsx`
- `UI/src/admin/components/Sidebar.tsx`
- `UI/src/team/TeamWorkspace.tsx`

Frontend behavior:

- Admin page loads REST snapshot, opens admin SSE stream, patches row updates, shows official freeze status, and exposes reveal controls.
- Team workspace opens the public/team scoreboard dialog, loads REST snapshot, opens public SSE stream, and respects freeze/reveal visibility.
- `useScoreboardStream` handles auth refresh, reconnect backoff, keepalive watchdog, event parsing, version-gap detection, and role-specific endpoints.
- `ScoreboardTable` renders rank, team, solved count, penalty, problem cells, hidden locks, first-to-solve medals, accepted cells, failed attempts, and row-level highlight.

## Testing

Backend tests:

- `ScoreboardCalculatorTest`
  - ICPC penalty calculation.
  - first-to-solve.
  - admin exclusion.
  - tie ranking.
  - hidden cell detection after freeze.
- `ScoreboardServiceTest`
  - full snapshot payloads.
  - row-level changed payloads.
- `ScoreboardRevealServiceTest`
  - default reveal state.
  - reveal start validation.
  - reveal-next persistence and completion.
- `ScoreboardSsePublisherTest`
  - public registry routing.
  - admin registry routing.
- Existing submission/rejudge tests were extended to publish scoreboard domain events.
- `SubmissionConsumerTest` covers the zero-test-case `INTERNAL_ERROR` path.

Frontend validation:

- `npm run build` verifies TypeScript and production bundling.
- `npm run test` runs Vitest + React Testing Library coverage for scoreboard stream parsing, version-gap detection, shared table rendering, admin view loading, and team freeze rendering.
- Manual E2E checklist below should be run in a browser against the backend.

Manual E2E checklist:

- Submit `ACCEPTED` before freeze: admin and public/team rows update live.
- Submit non-AC final verdict: attempts update, ranking changes only when score changes.
- Enter freeze period: admin remains live, public/team shows frozen metadata and hidden cells.
- Submit `ACCEPTED` during freeze: admin updates immediately, public/team remains frozen.
- End contest and start reveal: hidden queue is created.
- Reveal next: one cell appears and ranks recalculate.
- Reveal all: final public/team board matches admin scoring.
- Reset reveal: public/team board returns to frozen state.
- Rejudge `ACCEPTED -> WRONG_ANSWER`: solved count and penalty recalculate.
- Rejudge `WRONG_ANSWER -> ACCEPTED`: solved count, penalty, first-to-solve, and ranks recalculate.
- Admin submissions are excluded from ranking.
- Zero-test-case problem marks submissions `INTERNAL_ERROR` and still emits finalization.
- Paused contest preserves lifecycle freeze behavior and streams remain connected.

## Migration Notes

With Spring JPA schema generation, the new entities produce:

- `scoreboard_reveal_states`
- `scoreboard_reveal_cells`
- unique constraint on `(reveal_state_id, team_id, problem_id)`

For production migrations, create explicit DDL matching the JPA mapping before deployment.

## Operational Notes

- Scoreboard versions are process-local. If the backend is scaled horizontally, move version state and last-snapshot diff state to a shared store or route a contest stream consistently to one node.
- SSE clients already refetch on version gaps, so missed events degrade to a safe REST refresh.
- Admin reveal endpoints publish events after persistence; reveal service itself does not directly broadcast.
- Public/team snapshots never expose hidden post-freeze scoring until the relevant reveal cell is visible.
