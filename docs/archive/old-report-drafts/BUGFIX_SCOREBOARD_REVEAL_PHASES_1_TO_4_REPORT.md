# Bugfix Scoreboard Reveal Phases 1 To 4 Report

## Changed Files

- `backend/src/main/java/com/server/contestControl/contestServer/scoreboard/service/ScoreboardService.java`
- `backend/src/test/java/com/server/contestControl/contestServer/scoreboard/service/ScoreboardRevealVisibilityTest.java`
- `UI/src/admin/components/ScoreboardView.tsx`
- `UI/src/admin/components/ScoreboardView.test.tsx`
- `UI/src/components/scoreboard/ScoreboardTable.tsx`
- `UI/src/components/scoreboard/ScoreboardTable.test.tsx`
- `UI/src/team/components/Scoreboard.tsx`
- `UI/src/team/components/Scoreboard.test.tsx`
- `UI/src/test/scoreboardFixtures.ts`
- `UI/src/index.css`

## Phase 1 Findings

- `ScoreboardCalculator` ranks and calculates first-to-solve from the submission list it receives, so hidden frozen results must be removed before calculation for public/team views.
- `ScoreboardService` already had an inline public visibility filter, but admin and public snapshots shared the same hidden/revealed cell marker sets. Admin-live rows could include full frozen scores while cells appeared locked.
- The admin fullscreen reveal display reused the admin-live REST snapshot and admin SSE stream. That meant reveal display rankings, solved counts, and penalties could expose unrevealed frozen accepted submissions.
- Public/team REST and SSE both flow through `ScoreboardService`, so backend visibility policy changes cover snapshots and live payloads together.
- Scoreboard colors were pale and hidden/revealed/rank-change states were not visually distinct enough.

## What Was Fixed

- Public/team scoreboard calculations now use an explicit public-official visibility policy:
  - pre-freeze submissions are visible;
  - frozen submissions are visible only when their team/problem cell has been revealed;
  - ranking, solved count, penalty, accepted time, and first-to-solve are calculated from that same visible set.
- Admin-live scoreboard now stays truly live and no longer marks frozen cells as locked while still including full live totals.
- Fullscreen reveal display now loads and streams the public/reveal-visible scoreboard instead of admin-live data.
- Reveal steps now show rank up/down indicators and clearer row/cell highlights.
- Scoreboard colors were improved only in scoreboard/reveal UI scope.

## Behavior Before / After

- Before: Admin reveal display could show live rank, solved count, and penalty for unrevealed frozen ACs.
- After: Reveal display shows the same official visibility as public/team scoreboard until cells are revealed.

- Before: Admin-live cells could look hidden while row totals reflected hidden accepted submissions.
- After: Admin-live scoreboard consistently shows live cells and live totals.

- Before: Hidden/revealed/wrong/pending/first-to-solve colors were low contrast.
- After: Solved cells use stronger green, first-to-solve has an amber badge, hidden cells show lock + Hidden label, revealed cells animate with amber/blue, and rank movement uses green/rose indicators.

## Backend Changes

- Added explicit `ADMIN_LIVE` and `PUBLIC_OFFICIAL` view modes in `ScoreboardService`.
- Added helper methods for:
  - building visible submissions;
  - public submission visibility;
  - frozen-submission detection;
  - revealed-cell detection.
- Kept public/team calculations based on visible submissions before row ranking.
- Kept admin-live calculations based on full submission data.
- Preserved existing controllers, SSE adapter flow, reveal service, rejudge, submission, clarification, lifecycle, and Judge0 logic.

## Frontend Changes

- Admin fullscreen reveal display uses `getPublicScoreboard` and public scoreboard SSE.
- Admin main scoreboard remains admin-live.
- Team/public scoreboard tracks rank movement from incoming scoreboard payloads.
- Shared `ScoreboardTable` now renders:
  - hidden frozen cells with lock + Hidden label;
  - first-to-solve with a gold/amber medal badge;
  - stronger solved, wrong, pending, hidden, revealed, and rank-change colors.
- Scoreboard-only CSS animations were updated for reveal and row update states.

## Tests Added / Updated

- Added `ScoreboardRevealVisibilityTest` with coverage for:
  - frozen accepted submission hidden before reveal;
  - reveal step changing score and rank;
  - reveal all matching admin live final scoreboard;
  - first-to-solve during freeze appearing only after reveal;
  - wrong attempts before hidden AC applied only after reveal;
  - rejudge recalculation for revealed frozen cells;
  - admin-live keeping frozen cells visible.
- Updated focused frontend tests for:
  - hidden frozen cell rendering;
  - reveal/rank change indicators;
  - reveal cell highlight classes.

## Commands Run

- `mvn -Dtest=ScoreboardRevealVisibilityTest test`
  - Failed because `mvn` is not installed on this machine.
- `.\mvnw.cmd -Dtest=ScoreboardRevealVisibilityTest test`
  - Passed: 7 tests.
- `npx vitest run src/components/scoreboard/ScoreboardTable.test.tsx src/team/components/Scoreboard.test.tsx src/admin/components/ScoreboardView.test.tsx --globals --environment jsdom`
  - First run needed elevated permission for local esbuild spawn.
  - Final result passed: 3 files, 5 tests.
- `.\mvnw.cmd test`
  - Passed: 74 tests.
  - Non-fatal RabbitMQ connection-refused logs appeared because local RabbitMQ was not running.
- `npm run build`
  - Passed.
  - Vite emitted the existing large chunk warning.
- `npx tsc --noEmit`
  - Failed.

## Build / Test Results

- Backend full test suite: PASS.
- Focused frontend scoreboard tests: PASS.
- Frontend production build: PASS.
- TypeScript no-emit check: FAIL.

## Remaining Limitations

- `npx tsc --noEmit` fails because the current project TypeScript config includes test files without Vitest global types, and it also reports an existing `src/team/components/ui/resizable.tsx` `ref` typing issue. This is broader project/tooling configuration, not caused by scoreboard runtime behavior.
- The requested `mvn test` command cannot be run directly because `mvn` is unavailable; the Maven wrapper `.\mvnw.cmd test` was used successfully.

## Manual Verification Steps

1. Create or use a contest with a freeze window and end it with at least one frozen accepted submission.
2. Open the team/public scoreboard before reveal: the frozen AC cell should show Hidden/locked, with no solved state, penalty, accepted time, first-to-solve, solved count increase, or rank change.
3. Start reveal and click Next for that cell: the cell should reveal, solved count and penalty should update, first-to-solve should appear if applicable, and rank movement should be indicated.
4. Open admin scoreboard: admin-live should show full live results.
5. Open fullscreen reveal display from admin: it should match public/reveal-visible behavior, not admin-live hidden results.
6. Run reveal all: public/reveal display should match admin-live final official ranking.
