# AuraC2 Phase 1–4 Verification Report

---

## 1. Verification Metadata

| Field | Value |
|---|---|
| Date / Time | 2026-05-22 (local Jordan time, UTC+3) |
| Branch | `codex-phase4-contest-scoreboard-hardening` |
| Latest commit (HEAD, committed) | `53b7743a` — Merge PR #9 from codex-phase3-security-hardening |
| Uncommitted working-tree changes | 15 modified files (Phase 4 tests + docs + `ContestStatusSyncService.java`) — staged but NOT committed |
| Java version | 21.0.8 (detected from test output) |
| Node version | v22.17.1 |
| npm version | 10.9.2 |
| Backend test command | `cd backend && .\mvnw test` |
| Frontend test command | **NOT RUNNABLE** — `package.json` has no `test` script; no `vitest.config.ts` present |
| Backend test result | **183 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** |
| Frontend test result | 4 test files exist (`.test.tsx`) but cannot be executed — no runner configured |

---

## 2. Executive Summary

**Overall Status: MOSTLY VERIFIED**

All four phases are substantially implemented with strong, passing automated tests. No phase has a critical missing feature. The gaps are narrow: submission timing is tested via indirect mocking rather than against real time boundaries, and UI tests are written but have no vitest runner configured.

| Phase | Status | Summary |
|---|---|---|
| Phase 1 — Security & Reliability | **PASS** | HMAC callback security, stale protection, duplicate handling, publish-after-commit — all implemented and tested |
| Phase 2 — Judge0 Execution & Verdict Audit | **PASS** | Time/memory limits, status mapping, audit fields, aggregation, rejudge isolation — all implemented and tested |
| Phase 3 — Security & Authorization | **MOSTLY PASS** | /auth/register protected, role enforcement comprehensive, no-security guard implemented, cookie flags correct — one minor untested gap (team access to private test case expected output) |
| Phase 4 — Contest Lifecycle & Scoreboard | **MOSTLY PASS** | Lifecycle, ICPC scoring, freeze/reveal, SSE — implemented and tested with good coverage; some advanced scenarios (rejudge scoreboard impact, multi-pause submission timing) lack dedicated tests |

**Highest-risk remaining issues:**
1. Frontend test files (4 files, ~300 lines) are unrunnable — vitest is not configured.
2. Teams can call `GET /api/testcases/**` which returns `expectedOutput` — private test cases may be exposed.
3. Submission timing enforcement during pause uses a mock abstraction (`ContestService.getContestEntity()` throws) rather than time-based enforcement at the service layer — acceptable in current architecture but the lifecycle enforcement is delegated to the ContestStatusSyncScheduler, not enforced per-request.
4. `callback-secret` defaults to `dev-only-change-me` in application.yml — must be overridden in production.

---

## 3. Local Project Map

### Judge0 Pipeline
- `submissionServer/service/judge/Judge0Service.java` — sends each test case to Judge0 with signed callback URL and problem limits
- `submissionServer/dto/Judge0SubmissionDTO.java` — DTO with `cpu_time_limit`, `memory_limit`, `callback_url`
- `submissionServer/dto/Judge0Response.java` — receives Judge0 status back
- `submissionServer/entity/SubmissionJudgeResult.java` — stores per-test-case audit fields

### Callbacks
- `submissionServer/service/callback/CallbackHandler.java` — REST controller; validates signature before delegating
- `submissionServer/service/callback/Judge0CallbackSignatureService.java` — HMAC-SHA256 signer/validator
- `submissionServer/service/callback/Judge0CallbackService.java` — stale guard, duplicate guard, aggregation, finalization

### RabbitMQ
- `submissionServer/queue/submission/SubmissionProducer.java` — publishes submission ID
- `submissionServer/queue/submission/SubmissionConsumer.java` — consumes; guards duplicate queue messages via `QUEUEABLE_VERDICTS`
- `submissionServer/config/RabbitMQConfig.java`

### Rejudge
- `submissionServer/service/rejudge/RejudgeService.java` — reserves next `judgeRunId`, publishes after commit
- `submissionServer/controller/RejudgeController.java`
- `submissionServer/entity/Submission.java` — holds `judgeRunId`

### Security / Auth
- `authServer/config/SecurityConfiguration.java` — URL-level authorization, `@Profile("!no-security")`
- `authServer/config/NoSecurityProfileGuard.java` — runtime guard rejecting `no-security` in prod
- `authServer/filter/JwtAuthFilter.java`
- `authServer/controller/AuthController.java` — `@PreAuthorize("hasRole('ADMIN')")` on register
- `authServer/util/CookieUtil.java` — HttpOnly, Secure(prod), SameSite, path=/auth
- `authServer/service/logout/LogoutService.java`
- `resources/application.yml` — `judge0.callback-secret`, JWT secrets, scheduler config

### Contest Lifecycle
- `contestServer/service/ContestLifecycleService.java` — resolves effective state, end time, remaining millis, freeze time
- `contestServer/service/ContestService.java` — manual start/pause/resume/end
- `contestServer/service/ContestStatusSyncService.java` — orchestrates auto-transition scanning
- `contestServer/service/ContestStatusSyncExecutor.java` — per-contest transaction with REQUIRES_NEW
- `contestServer/scheduler/ContestStatusSyncScheduler.java`

### Scoreboard / Freeze / Reveal
- `contestServer/scoreboard/service/ScoreboardCalculator.java` — ICPC scoring, freeze filtering, first-to-solve
- `contestServer/scoreboard/service/ScoreboardFreezePolicy.java` — audience-aware freeze decisions
- `contestServer/scoreboard/service/ScoreboardRevealService.java` — reveal state machine
- `contestServer/scoreboard/service/ScoreboardRankingService.java` — stable tie-breaking
- `contestServer/scoreboard/service/ScoreboardService.java` — snapshot builder
- `contestServer/scoreboard/controller/ScoreboardController.java` — public endpoint
- `contestServer/scoreboard/controller/AdminScoreboardController.java` — admin reveal endpoints

### SSE
- `shared/sse/SseEmitterRegistry.java`
- `shared/sse/SsePublisher.java`
- `contestServer/scoreboard/sse/ScoreboardSseAdapter.java` — listens to `SubmissionFinalizedEvent` / `ContestUpdatedEvent`
- `contestServer/scoreboard/sse/ScoreboardSsePublisher.java`
- `contestServer/sse/contest/ContestSseAdapter.java`
- `submissionServer/sse/SubmissionSsePublisher.java`

### Tests
- **31 backend test classes** under `backend/src/test/`
- **4 UI test files** (`.test.tsx`) — `ScoreboardView.test.tsx`, `ScoreboardTable.test.tsx`, `Scoreboard.test.tsx`, `useScoreboardStream.test.tsx`

### Docs
- `docs/report-rebuild/report-draft-google-docs.md` — university academic report template
- `docs/report-rebuild/analysis-plan.md`
- `README.md` — project overview

---

## 4. Phase 1 Verification — Critical Security and Reliability

| Task | Status | Evidence | Notes |
|---|---|---|---|
| Judge0 callback has signed callback verification | **PASS** | `Judge0CallbackSignatureService.java` — HMAC-SHA256 over `submissionId:judgeRunId:testCaseNumber` | Secret validated at startup |
| Signature binds submissionId, judgeRunId, testCaseNumber | **PASS** | `payload()` method: `submissionId + ":" + judgeRunId + ":" + testCaseNumber` | Tested in `Judge0CallbackSignatureServiceTest` |
| Missing/invalid signature rejected before state change | **PASS** | `CallbackHandler.java:31-33` — returns 401 if `!callbackSignatureService.isValid(...)` before `callbackService.handleJudge0Callback()` | Tested in `CallbackHandlerSecurityTest` |
| Callback endpoint publicly reachable but signature-gated | **PASS** | `SecurityConfiguration.java:57` — `/api/callback/judge0/**` in `.permitAll()` block | Security at application layer, not HTTP auth layer |
| judgeRunId stale callback protection | **PASS** | `Judge0CallbackService.isStaleCallback()` — rejects any `callbackJudgeRunId != currentJudgeRunId` | Tested: `staleCallbackFromOlderRunIsIgnored`, `staleCallbackAfterForceRejudgeIsIgnored` |
| Duplicate callback idempotency (pre-existing record) | **PASS** | `Judge0CallbackService.recordJudgeResult()` — queries DB first, returns false if record exists | Tested: `duplicateCallbackForExistingResultIsIdempotent` |
| Concurrent duplicate callback race (unique constraint) | **PASS** | `recordJudgeResult()` catches `DataIntegrityViolationException` on `save()`, returns false | Tested via mock: `duplicateCallbackUniqueConstraintRaceIsIdempotent`; no real concurrency test |
| New submission RabbitMQ publish after DB commit | **PASS** | `SubmissionService.java:95-98` — `publishAfterCommit(() -> publishSubmission(...))` | Tested: `newSubmissionMessageIsPublishedOnlyAfterCommit`, `rollbackBeforeCommitDoesNotPublishSubmissionMessage` |
| Rejudge publish-after-commit preserved | **PASS** | `RejudgeService.java:235-241` — `publishAfterCommit(queuedIds)` and `registerAfterCommit` for SSE | Tested in `RejudgeServiceTest` |
| Duplicate queue messages do not dispatch twice | **PASS** | `SubmissionConsumer.java:52-59` — `QUEUEABLE_VERDICTS` check (only PENDING/PENDING_REJUDGE proceed) | Tested in `SubmissionConsumerTest` |
| Zero-test submissions → INTERNAL_ERROR | **PASS** | Consumer: `SubmissionConsumer.java:70-91`; Callback path: `Judge0CallbackService.java:72-83` | Tested in both consumer and callback service tests |
| Submission contest/problem validation not weakened | **PASS** | `SubmissionService.java:64-78` — validates contestId match and problem-to-contest membership | Tested in `SubmissionServiceValidationTest` |
| Tests for callback signature | **PASS** | `Judge0CallbackSignatureServiceTest` — 4 tests: valid, blank/null, prod-reject-dev-secret, blank-secret-rejected | — |
| Tests for stale callbacks | **PASS** | `Judge0CallbackServiceTest` — `staleCallbackFromOlderRunIsIgnored`, `staleCallbackAfterForceRejudgeIsIgnored`, `legacyCallbackWithoutJudgeRunIdIsIgnoredForNonLegacyRuns` | — |
| Tests for duplicate callbacks | **PASS** | `Judge0CallbackServiceTest` — `duplicateCallbackForExistingResultIsIdempotent`, `duplicateCallbackUniqueConstraintRaceIsIdempotent` | — |
| Tests for publish-after-commit | **PASS** | `SubmissionServiceValidationTest` — `newSubmissionMessageIsPublishedOnlyAfterCommit`, `rollbackBeforeCommitDoesNotPublishSubmissionMessage` | — |
| Tests for duplicate queue messages | **PASS** | `SubmissionConsumerTest` — `skipsSubmissionAlreadyInTerminalVerdict` | — |
| Tests for rejudge callback ordering | **PASS** | `Judge0CallbackServiceTest` — `currentCallbackAfterForceRejudgeIsAccepted`, stale tests cover run-id isolation | — |
| Production secret enforcement | **PARTIAL** | `Judge0CallbackSignatureService.validateConfiguration()` rejects dev fallback in prod profile | Default in `application.yml` is `dev-only-change-me` — must set `JUDGE0_CALLBACK_SECRET` env var in production |

---

## 5. Phase 2 Verification — Judge0 Execution and Verdict Audit

| Task | Status | Evidence | Notes |
|---|---|---|---|
| Problem.timeLimit passed to Judge0 | **PASS** | `Judge0Service.java:39` — `toJudge0CpuTimeLimitSeconds(submission.getProblem().getTimeLimit())` | ms → seconds via BigDecimal, 3 decimal places |
| Problem.memoryLimit passed to Judge0 | **PASS** | `Judge0Service.java:40` — `toJudge0MemoryLimitKilobytes(submission.getProblem().getMemoryLimit())` | MB → KB via `Math.multiplyExact` |
| Judge0SubmissionDTO has correct limit fields | **PASS** | `Judge0SubmissionDTO.java` — `@JsonProperty("cpu_time_limit")` Double, `@JsonProperty("memory_limit")` Integer; `@JsonInclude(NON_NULL)` | Null fields omitted from JSON when not configured |
| Unit conversion correct and tested | **PASS** | `Judge0ServiceTest` — `judge0RequestIncludesProblemTimeAndMemoryLimits`: 1500ms → 1.5, 256MB → 262144KB | Also: null/non-positive → null tested |
| Judge0 dispatch failures → safe error state | **PASS** | `SubmissionConsumer.markInternalError()` — sets INTERNAL_ERROR, saves audit record, publishes SSE | Tested: `judge0DispatchFailureMarksSubmissionInternalErrorAndStoresAuditResult` |
| Partial dispatch failures handled | **PASS** | Consumer exits on first dispatch failure, marking INTERNAL_ERROR immediately | No partial-dispatch test for multi-test-case split failure; acceptable |
| Unknown Judge0 statuses → INTERNAL_ERROR | **PASS** | `Verdict.fromJudge0Status()` — `default -> INTERNAL_ERROR` in switch | Tested: `unknownJudge0StatusMapsToInternalErrorAndStoresAuditDetails` |
| ACCEPTED (3), WRONG_ANSWER (4), TLE (5), CE (6) mappings correct | **PASS** | `Verdict.java:15-27` — explicit case for each | Tested via `VerdictTest` and multiple callback service tests |
| Runtime errors (7-12) → RUNTIME_ERROR | **PASS** | `Verdict.java:23` — `case 7, 8, 9, 10, 11, 12 -> RUNTIME_ERROR` | Covers SIGSEGV, SIGXFSZ, SIGFPE, SIGABRT, NZEC, Other |
| MLE mapping | **PARTIAL** | No dedicated MLE verdict; Judge0 CE has no specific MLE status code — falls into RUNTIME_ERROR | Judge0 CE does not emit a distinct MLE status; mapping is appropriate for the API used |
| Non-terminal statuses (1,2) do not finalize | **PASS** | `Judge0CallbackService.java:97-106` — `!isTerminalVerdict(verdict)` → returns without storing | Tested: `nonTerminalJudge0StatusDoesNotStoreOrFinalize` |
| Final verdict aggregation waits for all test cases | **PASS** | `Judge0CallbackService.java:120-137` — `receivedCount < expectedTestCaseCount` → returns early | Tested: `callbackFinalizesOnlyAfterAllTestCasesArrive` |
| Final verdict uses first failing test case by order | **PASS** | `Judge0CallbackService.finalVerdict()` — `.min(Comparator.comparing(getTestCaseNumber))` on non-ACCEPTED results | Tested: `finalVerdictUsesFirstFailingTestCaseByTestCaseNumber` |
| Rejudge aggregation uses current judgeRunId only | **PASS** | `CallbackHandler` passes `judgeRunId` in path; `isStaleCallback` rejects old runs; `recordJudgeResult` scoped by `judgeRunId` | Tested: stale callback tests with force-rejudge scenario |
| Verdict audit fields exist | **PASS** | `SubmissionJudgeResult` entity: `judge0StatusId`, `judge0StatusDescription`, `diagnostic` | Set in `recordJudgeResult()` via `Judge0AuditUtil` |
| Audit details stored for unknown/error statuses | **PASS** | `unknownJudge0StatusMapsToInternalErrorAndStoresAuditDetails` test captures all three fields | — |
| Hidden expected outputs not exposed to teams | **PARTIAL** | `Judge0SubmissionDTO` uses `@JsonInclude(NON_NULL)` and callback responses don't contain `expected_output`; however `GET /api/testcases/**` allows TEAM role and `TestCaseResponse` likely includes `expectedOutput` field | Security config grants TEAM read access to test cases; private test case hiding may be incomplete |
| Tests for limits, dispatch failure, aggregation, rejudge | **PASS** | `Judge0ServiceTest` (3 tests), `Judge0CallbackServiceTest` (12 tests), `SubmissionConsumerTest` (7 tests), `RejudgeServiceTest` (8 tests) | — |

---

## 6. Phase 3 Verification — Security and Authorization

| Task | Status | Evidence | Notes |
|---|---|---|---|
| `/auth/register` protected from anonymous users | **PASS** | `SecurityConfiguration.java:60` — `hasRole("ADMIN")` at URL level | Tested: `unauthenticatedRegisterIsRejected` → 401 |
| TEAM cannot register users | **PASS** | URL-level + `@PreAuthorize("hasRole('ADMIN')")` on `AuthController.register()` | Tested: `teamAuthenticatedRegisterIsRejected` → 403 |
| ADMIN can register users | **PASS** | Dual protection: URL filter + method security | Tested: `adminAuthenticatedRegisterSucceeds` → 200 |
| Method security (`@EnableMethodSecurity`) confirmed active | **PASS** | `SecurityConfiguration` has `@EnableMethodSecurity`; tested via reflection in `AuthControllerSecurityTest.methodSecurityIsEnabled()` | — |
| Admin-only contest mutations protected | **PASS** | `SecurityConfiguration.java:70` — `/api/contest/**` → `hasRole("ADMIN")`; lifecycle endpoints tested | Tested: `contestMutationsAreAdminOnly` (anonymous 401, TEAM 403, ADMIN 200) |
| Admin-only problem mutations protected | **PASS** | `SecurityConfiguration.java:74` — POST/PUT/DELETE `/api/problems/**` → ADMIN | Tested: `problemMutationsAreAdminOnly` |
| Admin-only test case mutations protected | **PASS** | `SecurityConfiguration.java:75-76` | Tested: `testCaseMutationsAreAdminOnly` |
| Admin-only rejudge endpoint protected | **PASS** | `/api/admin/**` → `hasRole("ADMIN")` | Tested: `rejudgeEndpointsAreAdminOnly` |
| Admin-only reveal endpoint protected | **PASS** | `/api/admin/scoreboard/**` → ADMIN | Tested: `scoreboardPublicAndAdminRoutesKeepSeparateAuthorization` |
| Team-only submission endpoint accessible | **PASS** | `/api/submissions/**` → `hasAnyRole("TEAM","ADMIN")` | Tested: `submissionEndpointsRequireAuthenticationAndTeamsCanReadOwnHistory` |
| Team cannot access other team's submissions | **PASS** | `SubmissionService.getSubmissionById()` checks ownership for TEAM role | Tested: `teamCannotAccessAnotherTeamsSubmissionById` |
| Public scoreboard accessible without auth | **PASS** | `/api/scoreboard/**` → `.permitAll()` | Tested: `scoreboardPublicAndAdminRoutesKeepSeparateAuthorization` |
| Clarification routes follow public/team/admin policy | **PASS** | `SecurityConfiguration.java:79-84` — granular per-path policy | Tested: `clarificationRoutesMatchPublicTeamAndAdminPolicy` |
| JWT filter skip paths are minimal | **PASS** | Only: `/`, static assets, swagger, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/verify/**`, `/api/callback/judge0/**`, some public scoreboard/contest read paths | All sensitive mutations require auth |
| `/auth/register` not accidentally in skip list | **PASS** | Not in `.permitAll()` block; explicitly `hasRole("ADMIN")` | — |
| `no-security` profile guarded against prod | **PASS** | `NoSecurityProfileGuard.java` — `ApplicationRunner.run()` validates; rejects `no-security` unless `dev`/`local`/`test` also active | Tested: `NoSecurityProfileGuardTest` (4 tests) |
| `SecurityConfiguration` excludes itself from `no-security` profile | **PASS** | `@Profile("!no-security")` on class | When `no-security` active, `SecurityConfiguration` bean is not registered |
| Cookie `HttpOnly` flag | **PASS** | `CookieUtil.java:20` — `.httpOnly(true)` | — |
| Cookie `Secure` flag in production | **PASS** | `CookieUtil.java:21` — `.secure(isProd)` | Secure=false in dev, true in prod |
| Cookie `SameSite` attribute | **PASS** | `CookieUtil.java:22` — `"Strict"` in prod, `"Lax"` in dev | — |
| Cookie path consistent | **PASS** | `REFRESH_COOKIE_PATH = "/auth"` used in both set and clear | — |
| Logout clears same cookie | **PASS** | `CookieUtil.clearRefreshCookie()` uses same path, httpOnly, secure settings, `maxAge(0)` | — |
| Refresh/logout cookie tested | **PASS** | `CookieUtilTest.java` exists and tests both add and clear paths | — |
| Judge0 callback compatible with Phase 1 | **PASS** | `/api/callback/judge0/**` in `.permitAll()`; signature check is at application layer, not HTTP auth | — |
| Route authorization security tests | **PASS** | `RouteAuthorizationSecurityTest` — 7 tests covering all route categories with anonymous/TEAM/ADMIN scenarios | — |
| Admin bulk team generation test | **PASS** | `AdminControllerBulkTeamGenerationSecurityTest`, `UserServiceBulkTeamGenerationTest` | — |

---

## 7. Phase 4 Verification — Contest Lifecycle and Scoreboard

| Task | Status | Evidence | Notes |
|---|---|---|---|
| Contest UPCOMING state | **PASS** | `ContestLifecycleService.resolveEffectiveState()` returns UPCOMING before start | Tested: `upcomingContestStaysUpcomingBeforeScheduledStart` |
| Contest auto-start (UPCOMING → RUNNING) | **PASS** | Lifecycle: `!now.isBefore(startTime) && !statusLocked` | Tested: `scheduledStartBoundaryIsInclusiveWhenContestIsUnlocked` |
| statusLocked blocks auto-start | **PASS** | Guard: `!Boolean.TRUE.equals(contest.getStatusLocked())` | Tested: `lockedUpcomingContestDoesNotAutoStartAtBoundary` |
| Manual start | **PASS** | `ContestService.updateStatus()` stamps `actualStartTime`, publishes MANUAL_START event | Tested: `ContestServiceTest.manualStartShouldStampActualStartTimeAndPublishEvent` |
| Pause / Resume | **PASS** | `ContestService.updateStatus()` handles PAUSED, sets `pausedAt`; resume accumulates `totalPauseMillis` | Tested in `ContestServiceTest` (17 tests total) |
| Multiple pauses accumulate correctly | **PASS** | `Contest.totalPauseMillis` accumulates on each resume; `resolveEffectiveEndTime` includes total pause | Tested: `accumulatedPauseDurationExtendsEffectiveEndTime` |
| Effective end time after pause | **PASS** | `resolveEffectiveEndTime()` = `actualStart + duration + totalPause` | Tested: `accumulatedPauseDurationExtendsEffectiveEndTime` |
| Paused contest has no wall-clock end time | **PASS** | `resolveEffectiveEndTime()` returns null when PAUSED | Tested: `pausedContestFreezesRemainingTimeAndHasNoWallClockEnd` |
| Auto-end (RUNNING → ENDED) | **PASS** | Lifecycle: effective end time check in scheduler cycle; tested via `ContestStatusSyncServiceTest` | — |
| Manual end | **PASS** | `ContestService.updateStatus(ENDED)` — tested: `manualEndShouldPublishJuryEndEvent` | — |
| statusLocked blocks auto-end | **PASS** | Same `statusLocked` guard applied to RUNNING → ENDED | Confirmed in `ContestLifecycleService.java:37` |
| Invalid state transitions rejected | **PASS** | `ContestService` throws `InvalidContestStateException` for illegal transitions | Tested: multiple invalid-transition tests in `ContestServiceTest` |
| Effective end boundary inclusive | **PASS** | `!now.isBefore(effectiveEndTime)` — boundary is inclusive | Tested: `effectiveEndBoundaryIsInclusiveForUnlockedRunningContest` |
| Submission before contest start rejected | **PARTIAL** | `SubmissionServiceValidationTest` tests this via mocking `ContestService.getContestEntity()` throwing `ContestNotFoundException` — indirectly correct but no direct time-boundary unit test | Existing architecture delegates to ContestService; behavior is correct but tested indirectly |
| Submission during pause rejected | **PARTIAL** | Same indirect approach as above | `assertNoActiveContestRejectsSubmission()` is reused for before-start, during-pause, after-end tests — behavior is correct, detail is in how ContestService determines "active" |
| Submission after end rejected | **PARTIAL** | Same as above | — |
| ICPC penalty calculation | **PASS** | `ScoreboardCalculator.scoreCell()` — `penalty = solvedMinutes + wrongAttempts * penaltyMinutes` | Tested: `calculatesIcpcScorePenaltyFirstToSolveAndExcludesAdmins` |
| Wrong attempts before AC count for penalty | **PASS** | `countWrongPenaltyAttempts()` counts before accepted submission only | Tested: penalty=50 for 1 wrong + 30min solve with 20min penalty |
| Wrong attempts after AC ignored | **PASS** | `beforeAccepted` filter in `scoreCell()` | Tested: `wrongAttemptsAfterAcceptedSubmissionDoNotChangePenaltyOrAttempts` |
| Compilation Error counts as penalty | **PASS** | `WRONG_PENALTY_VERDICTS` includes `COMPILATION_ERROR` | Tested: `compilationErrorCountsAsPenaltyButInternalErrorDoesNot` |
| INTERNAL_ERROR does NOT count as penalty | **PASS** | Not in `WRONG_PENALTY_VERDICTS` | Tested as above |
| Pending submissions tracked without changing rank | **PASS** | `countPendingAttempts()` separate from penalty counting | Tested: `pendingAttemptsAreExposedWithoutChangingRankOrPenalty` |
| Tie ranking with stable sort | **PASS** | `ScoreboardRankingService.rankRows()` — ties get same rank, stable by name then ID | Tested: `tiedRowsShareRankAndRemainStableByNameThenId` |
| First-to-solve tracking | **PASS** | `firstSolveByProblem()` — earliest accepted submission per problem | Tested: `calculatesIcpcScorePenaltyFirstToSolveAndExcludesAdmins` — beta gets first-solve badge |
| Teams with no solves ranked with 0 penalty | **PASS** | All teams included; 0 solved = ranked below any solver | Tested: `teamsWithNoSolvesRemainRankedWithZeroPenalty` |
| Admin submissions excluded from scoreboard | **PASS** | `isTeamSubmission()` filters by `role == Role.TEAM` | Tested: admin's ACCEPTED submission not counted for beta in first test |
| Public scoreboard hides frozen data | **PASS** | `ScoreboardFreezePolicy.isFrozenForAudience()` — PUBLIC audience returns true in freeze window | Tested: `publicAudienceFreezesInsideRunningFreezeWindow` |
| Admin view never frozen | **PASS** | `isFrozenForAudience()` returns false immediately for `ADMIN` audience | Tested: `adminAudienceIsNeverFrozen` |
| Ended public scoreboard stays frozen until COMPLETED reveal | **PASS** | Freeze condition checks `revealStatus != RevealStatus.COMPLETED` for ENDED contests | Tested: `endedPublicScoreboardStaysFrozenUntilRevealCompletes` |
| Freeze time slides with pauses | **PASS** | `resolveEffectiveScoreboardFreezeTime()` = `effectiveEnd - freezeMinutes` where effectiveEnd already includes pauses | Tested: `freezeWindowStartsAtPauseAwareFreezeBoundaryAndEndsWithContest` |
| Reveal start requires allowed state | **PASS** | `ScoreboardRevealService.start()` validates contest state | Tested in `ScoreboardRevealServiceTest` (6 tests) |
| Reveal next/all/reset | **PASS** | `ScoreboardRevealService` — reveal state machine with `IN_PROGRESS`, `COMPLETED` states | Tested in `ScoreboardRevealServiceTest` and `ScoreboardRevealVisibilityTest` (7 tests) |
| Reveal visibility correctness | **PASS** | Hidden cells vs. revealed cells logic; admin sees full, public sees frozen | Tested: `ScoreboardRevealVisibilityTest` |
| SSE scoreboard updates on submission finalized | **PASS** | `ScoreboardSseAdapter.onSubmissionFinalized()` listens to `SubmissionFinalizedEvent`, publishes admin and public updates | Tested: `ScoreboardSseAdapterTest` |
| SSE scoreboard updates on contest state change | **PASS** | `ScoreboardSseAdapter.onContestUpdated()` listens to `ContestUpdatedEvent` | Tested: `contestUpdatePublishesVersionedAdminAndPublicScoreboardUpdates` |
| Rejudge impact on scoreboard | **PARTIAL** | `SubmissionRejudgeQueuedEvent` is published; `ScoreboardSseAdapter` should react; not explicitly tested in scoreboard tests | `ScoreboardSseAdapter` may not handle `SubmissionRejudgeQueuedEvent`; needs verification |
| UI scoreboard tests present | **PARTIAL** | 4 test files exist (`ScoreboardView.test.tsx`, `ScoreboardTable.test.tsx`, `Scoreboard.test.tsx`, `useScoreboardStream.test.tsx`) | **Cannot run**: no `test` script in `package.json`, no vitest config |
| Documentation updated | **PARTIAL** | Docs exist in `docs/` but academic report template still has placeholders `[Insert Team Members]` | Functional docs (`docs/contest_lifecycle_guide.md`, `docs/scoreboard-feature-documentation.md`) appear accurate |

---

## 8. Test Results

### Backend Tests
**Command:** `cd backend && .\mvnw test`
**Result: BUILD SUCCESS — 183 tests, 0 failures, 0 errors, 0 skipped**

| Test Class | Tests | Result |
|---|---|---|
| `AuraServerApplicationTests` | 1 | PASS |
| `NoSecurityProfileGuardTest` | 4 | PASS |
| `AuthControllerSecurityTest` | 4 | PASS |
| `GlobalExceptionHandlerTest` | (included) | PASS |
| `JwtAuthFilterTest` | (included) | PASS |
| `RouteAuthorizationSecurityTest` | 7 | PASS |
| `UserServiceBulkTeamGenerationTest` | (included) | PASS |
| `CookieUtilTest` | (included) | PASS |
| `AdminControllerBulkTeamGenerationSecurityTest` | (included) | PASS |
| `ContestStatusSyncSchedulerTest` | 2 | PASS |
| `ContestTransitionSchedulerTest` | 3 | PASS |
| `ScoreboardCalculatorTest` | 7 | PASS |
| `ScoreboardFreezePolicyTest` | 4 | PASS |
| `ScoreboardRevealServiceTest` | 6 | PASS |
| `ScoreboardRevealVisibilityTest` | 7 | PASS |
| `ScoreboardServiceTest` | 2 | PASS |
| `ScoreboardSseAdapterTest` | 2 | PASS |
| `ScoreboardSsePublisherTest` | 2 | PASS |
| `ContestLifecycleServiceTest` | 7 | PASS |
| `ContestServiceTest` | 17 | PASS |
| `ContestStatusSyncServiceTest` (nested) | 8 | PASS |
| `ContestStatusSyncExecutorTest` | 4 | PASS |
| `SseEmitterRegistryTest` | 4 | PASS |
| `SubmissionResponseTest` | 1 | PASS |
| `VerdictTest` | 2 | PASS |
| `SubmissionConsumerTest` | 7 | PASS |
| `CallbackHandlerSecurityTest` | 3 | PASS |
| `Judge0CallbackServiceTest` | 12 | PASS |
| `Judge0CallbackSignatureServiceTest` | 4 | PASS |
| `Judge0ServiceTest` | 3 | PASS |
| `RejudgeServiceTest` | 8 | PASS |
| `SubmissionServiceValidationTest` | 14 | PASS |
| **TOTAL** | **183** | **ALL PASS** |

### Frontend Tests
**Result: NOT RUNNABLE**

`package.json` scripts contain only `dev`, `build`, `preview`. No `test` script exists. No `vitest.config.ts` or `vitest.config.js` is present in the `UI/` directory. The 4 test files use `vi.mock(...)` and `describe/it` patterns consistent with vitest, but vitest itself is not in `devDependencies`.

**Test files present but unrunnable:**
- `UI/src/admin/components/ScoreboardView.test.tsx`
- `UI/src/components/scoreboard/ScoreboardTable.test.tsx`
- `UI/src/team/components/Scoreboard.test.tsx`
- `UI/src/hooks/useScoreboardStream.test.tsx`
- `UI/src/test/scoreboardFixtures.ts` (shared fixtures)
- `UI/src/test/setup.ts`

**Missing important tests (backend):**
- Real time-boundary submission rejection tests (existing tests use abstraction, which is correct but indirect)
- `ScoreboardSseAdapter` handling of `SubmissionRejudgeQueuedEvent` — no test for rejudge → scoreboard update propagation
- Integration tests for full callback → scoreboard chain

---

## 9. Documentation and Diagram Consistency Check

| Claim | Status | Notes |
|---|---|---|
| Scoreboard is NOT falsely claimed as future-only | **PASS** | Scoreboard is implemented; docs (`docs/scoreboard-feature-documentation.md`, `docs/report-rebuild/`) describe it as implemented |
| Rejudge UI is NOT falsely claimed as missing | **PASS** | `admin/components/RejudgeView.tsx` exists; rejudge docs present |
| Clarifications are NOT falsely claimed as mock-only | **PASS** | `ClarificationService.java`, `ClarificationController.java`, UI components all exist |
| Problem/test-case update/delete paths NOT falsely missing | **PASS** | `ProblemService`, `TestCaseService`, `RouteAuthorizationSecurityTest` confirms PUT/DELETE endpoints work |
| Submission contest/problem validation NOT falsely missing | **PASS** | `SubmissionService.java` validates both; `SubmissionServiceValidationTest` proves it |
| Advanced judging features correctly NOT claimed | **PASS** | No compare policies, floating-point tolerance, custom validators, oracle, ML judging in codebase |
| Fixed input/output via Judge0 expected_output | **PASS** | `Judge0SubmissionDTO` has `expected_output` field; matching is done by Judge0, not application code |
| Academic report template has placeholders | **PARTIAL** | `report-draft-google-docs.md` contains `[Insert Team Members]`, `[Insert Supervisor Name]`, `[Insert Date]` — not finalized but not inaccurate |

---

## 10. Remaining Risks and Recommended Next Actions

### Critical

_(None identified. All 183 tests pass; no phase has an outright unimplemented feature.)_

### High

**1. Frontend tests are unrunnable**
- **Evidence:** No `test` script in `UI/package.json`, no vitest in devDependencies
- **Why it matters:** 4 UI test files exist and were written as part of Phase 4; they provide no safety guarantee without a runner
- **Recommended next action:** Add `vitest`, `@testing-library/react`, `@testing-library/user-event`, `jsdom` to devDependencies; add `"test": "vitest run"` script; add `vitest.config.ts`

**2. Private test case expected output may be exposed to team users**
- **Evidence:** `SecurityConfiguration.java:75` — `GET /api/testcases/**` → `hasAnyRole("TEAM","ADMIN")`; `TestCaseResponse` in `RouteAuthorizationSecurityTest` includes `expectedOutput`
- **Why it matters:** Teams could query test case IDs discovered from submission history and retrieve expected outputs for private test cases, defeating the purpose of hidden test cases
- **Recommended next action:** Either filter `expectedOutput` from `TestCaseResponse` for TEAM role, or restrict GET to ADMIN only for non-public test cases

**3. Default `judge0.callback-secret` is `dev-only-change-me`**
- **Evidence:** `application.yml:51` — `callback-secret: ${JUDGE0_CALLBACK_SECRET:dev-only-change-me}` 
- **Why it matters:** If `JUDGE0_CALLBACK_SECRET` is not set in production deployment, the fallback secret is well-known; `prod` profile validation in `Judge0CallbackSignatureService` catches this, but only if `prod` is the active Spring profile
- **Recommended next action:** Document deployment requirement; ensure `prod` profile is always active in production; add CI check for env var presence

### Medium

**4. No explicit test for rejudge → scoreboard SSE propagation**
- **Evidence:** `SubmissionRejudgeQueuedEvent` is published by `RejudgeService`, but `ScoreboardSseAdapterTest` only tests `SubmissionFinalizedEvent` and `ContestUpdatedEvent`
- **Why it matters:** If `ScoreboardSseAdapter` doesn't handle rejudge queued events, the public scoreboard may show stale data during a mass rejudge
- **Recommended next action:** Check if `ScoreboardSseAdapter` has a `@EventListener` for `SubmissionRejudgeQueuedEvent`; add test if not covered

**5. Submission timing tested indirectly**
- **Evidence:** `SubmissionServiceValidationTest` tests before-start, during-pause, after-end via mocking `ContestService.getContestEntity()` throwing; not via direct time-window evaluation
- **Why it matters:** The enforcement is correct architecturally (ContestService delegates to the sync state), but exact time-boundary behavior (e.g., "at exactly T=end" boundary) is not verified
- **Recommended next action:** Add a `ContestService` integration test or parameterized test covering exact boundary conditions with real `ContestLifecycleService` + `Instant.now()` manipulation

### Low

**6. Concurrent stale-callback test is mock-only**
- **Evidence:** `duplicateCallbackUniqueConstraintRaceIsIdempotent` mocks `DataIntegrityViolationException` being thrown; no multi-thread test
- **Why it matters:** The unique constraint approach is correct, but concurrent race behavior is not verified under load
- **Recommended next action:** Consider a `@RepeatedTest` with thread interleaving, or document as acceptable risk given DB-level constraint guarantee

**7. Academic report document has template placeholders**
- **Evidence:** `docs/report-rebuild/report-draft-google-docs.md` contains `[Insert Team Members]`, `[Insert Date]`
- **Why it matters:** Cosmetic; not a functional or security issue
- **Recommended next action:** Fill in before submission

---

## 11. Final Checklist

### Phase 1 — Critical Security and Reliability
- [x] Judge0 callback has HMAC-SHA256 signature verification
- [x] Signature binds submissionId, judgeRunId, testCaseNumber
- [x] Missing/invalid signature rejected before any state change
- [x] Callback endpoint remains publicly reachable
- [x] judgeRunId stale callback protection implemented
- [x] Duplicate callback idempotency via DB check + unique constraint fallback
- [x] New submission publish occurs after DB commit
- [x] Rejudge publish occurs after DB commit
- [x] Duplicate queue messages skipped via QUEUEABLE_VERDICTS guard
- [x] Zero-test submissions → INTERNAL_ERROR
- [x] Submission contest/problem validation preserved
- [x] Tests for signature, stale, duplicate, publish-after-commit, queue dedup, rejudge isolation
- [ ] Production secret enforcement end-to-end verified (partial — profile guard exists, deployment docs incomplete)

### Phase 2 — Judge0 Execution and Verdict Audit
- [x] Problem.timeLimit → Judge0 cpu_time_limit (ms → seconds)
- [x] Problem.memoryLimit → Judge0 memory_limit (MB → KB)
- [x] Judge0SubmissionDTO has correct field names and NON_NULL inclusion
- [x] Unit conversion correct and tested
- [x] Dispatch failures → INTERNAL_ERROR
- [x] Unknown Judge0 statuses → INTERNAL_ERROR
- [x] Non-terminal statuses do not finalize verdict
- [x] Final aggregation waits for all expected callbacks
- [x] Final verdict uses first failing test case by index
- [x] Rejudge aggregation uses current judgeRunId only
- [x] Audit fields (judge0StatusId, judge0StatusDescription, diagnostic) exist on SubmissionJudgeResult
- [ ] Private test case expected output hidden from team users (gap — GET /api/testcases/** accessible to TEAM)

### Phase 3 — Security and Authorization
- [x] /auth/register protected at URL level AND method level
- [x] Anonymous register → 401
- [x] TEAM register → 403
- [x] ADMIN register → 200
- [x] Contest/problem/test-case mutations → ADMIN only
- [x] Rejudge → ADMIN only
- [x] Admin scoreboard reveal → ADMIN only
- [x] Team submissions → TEAM+ADMIN
- [x] Clarification routes enforce public/team/admin split
- [x] JWT skip paths are minimal and safe
- [x] no-security profile guarded via NoSecurityProfileGuard
- [x] Cookie flags: HttpOnly, Secure(prod), SameSite(Strict/Lax), path=/auth
- [x] Logout clears same cookie
- [x] Judge0 callback compatible with Phase 1 signature
- [x] Security tests cover all route categories

### Phase 4 — Contest Lifecycle and Scoreboard
- [x] Upcoming → Running (auto and manual) tested
- [x] Paused state and remaining time calculation tested
- [x] Multiple pause accumulation tested
- [x] Effective end time with pauses tested
- [x] statusLocked blocks auto-transitions tested
- [x] Invalid state transitions rejected and tested
- [x] Submission timing (before/during/after) tested (indirectly)
- [x] ICPC penalty calculation tested
- [x] Wrong attempts before AC count; after AC ignored
- [x] Compilation Error counts as penalty; INTERNAL_ERROR does not
- [x] Pending submissions tracked, not penalized
- [x] Tie ranking with stable sort tested
- [x] First-to-solve tracking tested
- [x] Teams with no solves included and ranked last
- [x] Admin submissions excluded from scoring
- [x] Public scoreboard freezes in window
- [x] Admin view never frozen
- [x] Ended contest stays frozen until reveal COMPLETED
- [x] Freeze time slides with accumulated pauses
- [x] Reveal start, next, all, reset tested
- [x] SSE updates on submission and contest events tested
- [ ] Rejudge → scoreboard SSE propagation not explicitly tested
- [ ] Frontend UI tests exist but cannot run (no vitest runner)

### Documentation
- [x] Scoreboard not falsely claimed as future-only
- [x] Rejudge UI not falsely claimed as missing
- [x] Clarifications not falsely claimed as mock-only
- [x] Advanced judging features correctly not claimed
- [ ] Academic report has unfilled placeholders (cosmetic)
- [ ] Frontend test runner not configured

---

## 12. Final Verdict

**MOSTLY VERIFIED: Minor gaps remain.**

Phases 1 through 4 are substantially complete. All 183 backend tests pass. The implementation covers the core requirements of each phase: signed HMAC callback security, Judge0 limit propagation with correct unit conversion, comprehensive route authorization, and ICPC-correct scoreboard with freeze/reveal behavior.

The remaining gaps are narrow in scope:

1. **Frontend tests cannot run** — vitest is not configured. The test files were written but are inert without a runner.
2. **Private test case expected outputs** may be accessible to TEAM users via `GET /api/testcases/**`. This is an unresolved authorization gap.
3. **Submission timing tests** use an indirect mocking approach; the behavior is correct, but exact time-boundary edge cases are not verified at the service layer.
4. **Rejudge-to-scoreboard SSE propagation** is not covered by any test.

None of these issues represent a missing Phase feature, but the first two warrant attention before production deployment.
