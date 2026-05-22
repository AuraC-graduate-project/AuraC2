# AuraC2 Report Rebuild - Phase 1 Analysis Plan

This document is based on the current local codebase only. The instructor template was used for report structure. The old DOCX and PDF were used only for academic tone, formatting pattern, and identifying outdated sections or diagrams that need replacement.

## A. Feature Classification Table

| Feature | Status | Evidence files/classes | Reason | Report impact |
|---|---|---|---|---|
| Spring Boot backend application | Implemented | `backend/src/main/java/com/server/contestControl/AuraServerApplication.java`, `backend/pom.xml` | The backend is a Spring Boot 3.4.2 application using Java 21, Spring Web, Security, JPA, AMQP, PostgreSQL, and JWT libraries. | Describe AuraC2 as a Spring Boot backend with layered packages. |
| React frontend application | Implemented | `UI/src/App.tsx`, `UI/package.json`, `UI/src/admin/App.tsx`, `UI/src/team/App.tsx` | The frontend is a Vite React TypeScript application with role-based routing to admin or team interfaces. | Include React/Vite as the UI implementation and split admin/team screens in the module list. |
| Login | Implemented | `AuthController.login`, `LoginService.login`, `LoginPage`, `authApi.loginApi` | The system validates username/password, issues an access token, creates a refresh token, and routes by decoded role. | Include as implemented functional requirement and authentication use case. |
| Registration | Implemented | `AuthController.register`, `@PreAuthorize("hasRole('ADMIN')")`, `SecurityConfiguration` with `@EnableMethodSecurity`, `JwtAuthFilter`, `AuthControllerSecurityTest`, `JwtAuthFilterTest`, `RegistrationService.registerTeam`, `RegisterModal`, `registerUser` | Team account registration creates TEAM users with BCrypt-encoded passwords and is protected at route level and backend method level for administrators. `/auth/register` is not skipped by the JWT filter, so a bearer token can populate the security context before `@PreAuthorize` is evaluated. | Describe team registration as an administrator workflow protected by route-level and method-level authorization, not as open self-service registration. |
| Admin bootstrap account | Implemented | `AdminBootstrapRunner`, `AdminBootstrapProperties`, `UserRepository.countByRole` | On startup, the system ensures at most one admin exists, creates one if missing, and rotates the existing admin password into `admin-account.txt`. | Include in security design and implementation notes; mention operational risk of password rotation on restart. |
| JWT access token validation | Implemented | `JwtAuthFilter`, `JwtService`, `AccessTokenStrategy`, `SecurityConfiguration`, `JwtAuthFilterTest` | Bearer tokens are extracted from `Authorization`, validated, and converted into Spring Security authentication. The filter skip list is limited to public/no-bearer routes, and `/auth/register` is intentionally not skipped. | Include in security architecture and non-functional security requirement. |
| Refresh-token persistence | Implemented | `RefreshToken` entity, `RefreshTokenRepository`, `RefreshTokenRepoService`, `TokenIssuerUtil` | Refresh token records are persisted with device IP, creation time, expiry, token hash, user link, and revoked flag. | Update ER diagram to include `refresh_tokens` and explain token lifecycle. |
| Refresh-token rotation | Implemented | `RefreshTokenService.refresh`, `RefreshTokenValidator`, `TokenIssuerUtil` | Refresh validates the cookie token, revokes the old refresh token, and issues a new access token plus new refresh token. | Include in authentication activity diagram. |
| Refresh-token revocation | Implemented | `RefreshTokenRepoService.revokeToken`, `LogoutService`, `RefreshTokenValidator` | Tokens have a `revoked` flag and validation rejects revoked tokens. Logout receives the shared `/auth` refresh cookie and can revoke the persisted token. | Include as implemented refresh-token lifecycle behavior. |
| Refresh cookie behavior | Implemented | `CookieUtil.addRefreshToCookie`, `CookieUtil.clearRefreshCookie`, `CookieConfig`, `CookieUtilTest` | Refresh cookies are HTTP-only, same-site aware, use `.secure(isProd)`, and use the shared `/auth` path for creation and clearing. This path allows both `/auth/refresh` and `/auth/logout` to receive the same cookie. | Mark as implemented; explain local development uses non-secure cookies while production can enable Secure cookies through profile/environment behavior. |
| Logout | Implemented | `AuthController.logout`, `LogoutService.logout`, `CookieUtil.clearRefreshCookie`, `authApi.logoutApi`, `CookieUtilTest` | Backend logout validates the refresh cookie, revokes the persisted refresh token, clears the security context, and clears the same `/auth`-scoped cookie that was originally created. | Include logout as implemented refresh-token revocation and session cleanup. |
| Role-based authorization | Implemented | `SecurityConfiguration`, `@PreAuthorize` on controllers, `Role` enum, `RouteAuthorizationSecurityTest` | The code defines `ADMIN` and `TEAM`; endpoints are protected with explicit URL rules and method security. Tests cover admin-only, team-only, public, and callback route behavior. | Include implemented requirement and role-based use case diagram. |
| `no-security` profile guard | Implemented | `NoSecurityProfileGuard`, `NoSecurityProfileGuardTest`, `SecurityConfiguration @Profile("!no-security")` | The main filter chain is disabled by `no-security`, but startup now fails unless that profile is paired with `dev`, `local`, or `test`; `prod,no-security` and bare `no-security` are rejected. | Document as a local/test-only escape hatch, not a normal runtime profile. |
| Account flags | Implemented | `User` implements `UserDetails`, fields `accountNonLocked`, `credentialsNonExpired`, `accountNonExpired` | Account flags are persisted with defaults and are inherited by Spring Security user details. There is no admin UI for changing these flags. | Mention as implemented data model, partial management surface. |
| Email verification | Deprecated / Removed | `EmailNotVerifiedException`, `InvalidVerificationTokenException`, `VerificationTokenExpiredException`, `/verify/**` permit rule | Only exceptions and security allow-list remnants exist. There is no verification entity, controller, token creation, email workflow, or verification service. | Remove or classify as legacy/deprecated scaffolding. |
| Public contest status endpoints | Implemented | `SecurityConfiguration`, `ContestController.getActive/getUpcoming/getPaused/getEnded` | Active, upcoming, paused, and ended endpoints are public. The contest lifecycle SSE stream is admin-protected. | Include as implemented public read scope. |
| Contest creation | Implemented | `ContestController.createContest`, `ContestService.createContest`, `ContestRequest`, `CreateContestModal` | Admin can create future contests with duration, freeze, and penalty settings. Backend blocks creation if an effective scheduled/running/paused contest exists. | Include as implemented contest administration requirement. |
| Persisted contest state | Implemented | `Contest.status`, `ContestStatus`, `ContestRepository` | Database stores `UPCOMING`, `RUNNING`, `PAUSED`, or `ENDED`. | Explain persisted state separately from effective state. |
| Effective contest state | Implemented | `ContestLifecycleService.resolveEffectiveState`, `ContestService.toResponse`, `ContestResponse.effectiveState` | Effective state is calculated from persisted status, current time, schedule, duration, pause data, and status lock. | This must be a central design explanation and activity diagram. |
| Effective end-time calculation | Implemented | `ContestLifecycleService.resolveEffectiveEndTime`, `ContestResponse.effectiveEndTime` | Effective end time uses `actualStartTime + duration + totalPauseMillis`; it returns null for upcoming/paused contests. | Include as lifecycle design decision. |
| Pause and resume time handling | Implemented | `Contest.actualStartTime`, `pausedAt`, `totalPauseMillis`, `ContestService.updateStatus`, `ContestLifecycleService.resolveRemainingMillis` | Manual start stamps actual start; pause stamps `pausedAt`; resume accumulates pause duration and clears `pausedAt`. | Include separate pause/resume activity diagram. |
| Manual start/pause/resume/end | Implemented | `ContestController.start/resume/pause/end`, `ContestService.updateStatus` | Admin transitions are validated by current state. Manual end before effective end requires `juryOverride=true`. | Include as implemented requirements and use cases. |
| Jury override | Implemented | `ContestController.endContest`, `ContestService.updateStatus(..., juryOverride)` | Admin can force an early end with query parameter `juryOverride=true`. | Include in lifecycle controls and risk notes. |
| Auto start and auto end | Implemented | `ContestStatusSyncService`, `ContestStatusSyncExecutor`, `ContestTransitionScheduler`, `ContestStatusSyncScheduler` | Exact-time scheduler and fallback scheduler synchronize persisted state to effective state. Auto-start stamps `actualStartTime`; auto-end uses effective end. | Include exact-time and fallback scheduler diagrams. |
| `ContestUpdatedEvent` | Implemented | `ContestUpdatedEvent`, `ContestService`, `ContestStatusSyncService`, `ContestStreamBroadcaster`, `ContestTransitionScheduler` | Manual and automatic transitions publish event reasons used by scheduler and SSE broadcaster. | Include in event/listener list and architecture diagram. |
| `@TransactionalEventListener` after commit behavior | Implemented | `ContestTransitionScheduler.onContestUpdated`, `ContestStreamBroadcaster.onContestUpdated` | Listeners react after transaction completion, with fallback execution allowed when no transaction exists. | Explain why this avoids broadcasting or scheduling stale state. |
| `REQUIRES_NEW` in lifecycle scheduling/sync | Implemented | `ContestTransitionScheduler.onContestUpdated`, `ContestStatusSyncExecutor.syncContestStatus`, `ContestService.buildResponseForId` | New transactions are used for scheduler side effects, row locking, and fresh response reads. | Include in design decisions. |
| Row locking for contest sync | Implemented | `ContestRepository.findByIdWithLock`, `ContestStatusSyncExecutor.syncContestStatus` | Pessimistic write lock prevents concurrent scheduler calls from applying duplicate auto transitions. | Include as reliability and race-condition control. |
| Application startup scheduler recovery | Implemented | `ContestTransitionScheduler.onApplicationReady` | Existing contests are read from the database and rescheduled after application startup. | Include scheduler startup recovery diagram. |
| Scheduler configuration flags | Implemented | `application.yml`, `ContestStatusSyncScheduler`, `SchedulerConfig` | `contest.sync.enabled`, `delay-ms`, and `initial-delay-ms` configure fallback sync. ThreadPoolTaskScheduler pool size is 3. | Include in configuration list. |
| Lazy dependency injection for lifecycle cycles | Unknown / Needs Confirmation | No `@Lazy` usage found in lifecycle classes | The code splits sync writing into `ContestStatusSyncExecutor` rather than using lazy injection. There is no discovered lazy dependency injection for this area. | Do not claim lazy injection unless confirmed. |
| SSE stream endpoint | Implemented | `ContestStreamController.stream`, `ContestStreamBroadcaster`, `useContestStream` | Browser opens `/api/contest/stream`; backend returns an `SseEmitter`, sends a `snapshot`, registers emitter, and broadcasts `contest-update` events. | Include SSE architecture and connection activity diagrams. |
| SSE emitter registry and cleanup | Implemented | `ContestStreamBroadcaster.emitters`, `CopyOnWriteArrayList`, `register`, `safeSend` | Emitters are stored in a thread-safe list; completion, timeout, error, and failed send remove emitters. | Include in real-time design. |
| SSE heartbeat | Implemented | `ContestStreamBroadcaster.heartbeat` | A scheduled heartbeat comment is sent every 15 seconds. | Include in SSE diagram and non-functional reliability notes. |
| Frontend SSE snapshot and update handling | Implemented | `useContestStream`, `ContestOverview.applySnapshot`, `ContestOverview.placeContest`, `switchTabForReason` | Snapshot hydrates all contest buckets; updates move a contest between active/upcoming/paused/ended tabs. | Include as frontend real-time implementation. |
| REST fallback if no SSE snapshot arrives | Implemented | `ContestOverview` fallback timer | After 3 seconds without hydration, REST endpoints are called to populate contest state. | Include in SSE failure flow. |
| Polling fallback when SSE closes | Implemented | `ContestOverview` polling effect | When connection state is closed, the UI polls contest REST endpoints every 10 seconds. | Include in SSE failure flow. |
| Reconnection behavior | Implemented by browser | `useContestStream` | The hook relies on native `EventSource` retry behavior and updates UI state to connecting/open/closed. | Describe as native browser reconnection, not custom reconnect logic. |
| Problem creation | Implemented | `ProblemController.createProblem`, `ProblemService.createProblem`, `CreateProblemModal` | Admin creates contest-bound problems with title, description, time limit, memory limit, and difficulty. | Include in contest content management. |
| Problem retrieval/listing | Implemented | `ProblemController.getProblem`, `ProblemController.getProblemsByContest`, `ProblemsView`, `teamApi.getProblemsByContest` | Admin and team users can retrieve problems by id and by contest. | Include as implemented. |
| Problem update/delete | Implemented | `ProblemController.updateProblem`, `ProblemController.deleteProblem`, `ProblemService.updateProblem`, `ProblemService.deleteProblem`, `RouteAuthorizationSecurityTest` | Admin-only problem update and delete endpoints exist and are covered by route authorization tests. | Include as implemented admin content management. |
| Test-case creation | Implemented | `TestCaseController.addTestCase`, `TestCaseService.addTestCase`, `AddTestCaseModal` | Admin can attach test cases to a problem. | Include as implemented. |
| Test-case retrieval and visibility | Partially Implemented | `TestCaseController.getTestCases`, `TestCaseService.getTestCases`, `TestCaseResponse.fromPublicEntity` | Team users only receive public test cases, but public responses still include expected output. Private cases are hidden. There is no differentiated sample-only representation. | Explain accurately in requirements and limitations. |
| Test-case update/delete | Implemented | `TestCaseController.updateTestCase`, `TestCaseController.deleteTestCase`, `TestCaseService.updateTestCase`, `TestCaseService.deleteTestCase`, `RouteAuthorizationSecurityTest` | Admin-only test-case update and delete endpoints exist and are covered by route authorization tests. | Include as implemented admin content management. |
| Difficulty handling | Implemented | `Difficulty`, `Difficulty.fromString`, `ProblemService.createProblem`, `CreateProblemModal` | Difficulty is stored as enum and selected in UI. | Include in data model. |
| Time and memory limit fields | Partially Implemented | `Problem.timeLimit`, `Problem.memoryLimit`, `ProblemRequest`, `ProblemResponse`, `Judge0Service` | Limits are stored and displayed, but Judge0 request does not pass a CPU time limit or memory limit parameter. | Classify as metadata implemented, enforcement not implemented. |
| Submission creation | Implemented | `SubmissionController.submit`, `SubmissionService.submitCode`, `Submission` entity | Submissions are persisted after active contest/problem validation, then the RabbitMQ message is published only after the database transaction commits. | Include after-commit queue publish behavior in judging flow. |
| Validation of running contest | Implemented | `SubmissionService.submitCode`, `ContestService.getContestEntity` | Submission requires `getContestEntity`, which finds an effective RUNNING contest or throws. | Include as implemented. |
| Validation of authenticated user/team | Implemented | `SecurityConfiguration`, `SubmissionController`, `SecurityContextHolder` in `SubmissionService` | The controller allows TEAM and ADMIN, then uses the authenticated principal as the submission owner. | Include; note ADMIN can submit by policy. |
| RabbitMQ submission queue | Implemented | `RabbitMQConfig`, `SubmissionProducer`, `SubmissionConsumer` | Submissions are sent to durable `submissionQueue` through direct exchange after commit and consumed by `@RabbitListener`; the consumer locks the submission before claiming it to reduce duplicate dispatch risk. | Include asynchronous judging architecture and duplicate-message guard. |
| Result queue | Partially Implemented | `RabbitMQConfig.RESULT_QUEUE`, `ResultProducer`, `ResultConsumer` | Queue/exchange/binding constants and beans exist, but producer and consumer classes are empty. | Mark as scaffolded only. |
| Judge0 request dispatch | Implemented | `SubmissionConsumer.handleSubmission`, `Judge0Service.sendSingleTest`, `Judge0SubmissionDTO` | Consumer maps language, increments judge run, marks RUNNING, sends one Judge0 submission per test case with callback URL. | Include judging flow. |
| Judge0 language mapping | Implemented | `LanguageMapper.convertLanguage` | Supports C, C++, Java, Python, JavaScript, and Go IDs. Unsupported language throws `IllegalArgumentException`. | Include supported language list and risk for unhandled unsupported language. |
| Judge0 callback URL with run, test case, and signature | Implemented | `Judge0Service`, `Judge0CallbackSignatureService`, `CallbackHandler` | Callback URL includes `submissionId`, `judgeRunId`, `testCaseNumber`, and an HMAC signature query parameter. A signed legacy callback route also exists for run zero. | Include in stale callback and callback-security diagrams. |
| Judge0 callback processing | Implemented | `CallbackHandler`, `Judge0CallbackService.handleJudge0Callback`, `SubmissionRepository.findByIdForUpdate` | Callback signature verification runs before state mutation; the service then locks the submission, rejects stale or invalid callbacks, records terminal test-case results, and finalizes aggregate verdict when all expected tests arrive. | Include as implemented. |
| Per-test-case result tracking | Implemented | `SubmissionJudgeResult`, `SubmissionJudgeResultRepository`, `Judge0CallbackService.recordJudgeResult` | Each test case result is stored with unique `(submission_id, judge_run_id, test_case_number)`; duplicate callbacks are treated idempotently. | Update ER and judging diagrams. |
| Aggregate verdict calculation | Implemented | `Judge0CallbackService.finalVerdict`, `Judge0CallbackServiceTest` | Final verdict is the earliest non-accepted test case by test-case number, or ACCEPTED if all are accepted. | Replace old callback-ordering limitation with current fix. |
| Stale callback protection | Implemented | `Submission.judgeRunId`, `CallbackHandler`, `Judge0CallbackService.isStaleCallback`, `Judge0CallbackServiceTest.staleCallbackFromOlderRunIsIgnored` | Old callbacks are ignored if their judgeRunId does not match the current submission run. Legacy callbacks are stale once run id is greater than zero. | Include as implemented and explain why run tracking exists. |
| Zero-test-case behavior | Partially Implemented | `SubmissionConsumer`, `Judge0CallbackService` | Callback service marks INTERNAL_ERROR if a callback arrives for zero tests, but consumer sets submission RUNNING and sends no callbacks when the problem has zero test cases. | Mark as risk; no proactive zero-test guard exists. |
| Execution time and memory storage | Partially Implemented | `Judge0Response.getTimeAsInt`, `getMemoryAsInt`, `Judge0CallbackService.maxExecutionTime/maxMemoryUsage`, frontend submission views | Time is converted to milliseconds and maxed across cases. Memory is stored raw from Judge0 KB, while admin UI labels it as MB. | Include as implementation detail and unit-risk limitation. |
| Team submission history | Implemented | `SubmissionController.getAllMySubmissions`, `getAllSubmissionByProblem`, `teamApi`, `SubmissionHistory` | Team can view all own submissions and problem-filtered submissions. | Include as implemented. |
| Admin submission review | Implemented | `AdminController.getAllSubmissions`, `SubmissionsView` | Admin can list all submissions, search/filter, inspect code, and resolve user/problem labels. | Include as implemented. |
| Rejudge backend | Implemented | `RejudgeController`, `RejudgeService`, `RejudgeResponse`, `RejudgeSubmissionsRequest`, tests | Admin-only endpoints rejudge selected submissions, all submissions for a problem, or all submissions for a contest. Final verdicts are reset to PENDING_REJUDGE and republished after commit. | Include as implemented backend feature. |
| Rejudge UI | Implemented | `UI/src/admin/components/RejudgeView.tsx`, `UI/src/admin/services/api.ts`, `RejudgeController` | Admin frontend exposes rejudge actions for problem and contest workflows and calls backend rejudge endpoints. | Include as implemented admin workflow. |
| Rejudge old result handling | Implemented with notes | `RejudgeService`, `SubmissionConsumer`, `SubmissionJudgeResult` | Old per-test-case rows are not cleared or archived, but the consumer increments `judgeRunId`; new callbacks write a new run, and old callbacks are rejected. | Explain as superseding by run id, not deletion. |
| Rejudge partial publish failure reporting | Partially Implemented | `RejudgeService.publishSubmissions` | Failures while republishing are logged after commit, but the `RejudgeResponse` already counts submissions as queued and does not return publish failure details. | Include as operational limitation. |
| Clarification backend | Implemented | `Clarification`, `ClarificationController`, `ClarificationService`, `ClarificationRepository`, clarification enums/DTOs | Backend supports team submission during running contests, admin review/reply, public/private replies, standard replies, and public answered clarification listing. | Update old report: clarifications are not backend-planned-only anymore. |
| Clarification frontend | Implemented | `UI/src/admin/components/ClarificationsView.tsx`, `UI/src/team/components/Clarifications.tsx`, `UI/src/admin/services/api.ts`, `UI/src/team/services/api.ts`, `useClarificationStream` | Admin and team clarification screens call backend endpoints and use clarification SSE streams. | Describe clarifications as backend/frontend wired. |
| Scoreboard freeze metadata | Implemented | `Contest.scoreboardFreezeMinutes`, `penaltyMinutes`, `ContestLifecycleService.isScoreboardFrozen`, `ScoreboardService` | Contest stores freeze/penalty settings and scoreboard service applies public freeze/reveal behavior. | Include under scoreboard behavior and contest configuration. |
| Scoreboard ranking | Implemented | `contestServer/scoreboard`, `ScoreboardController`, `AdminScoreboardController`, `ScoreboardService`, UI scoreboard views | ICPC-style ranking, penalties, first-to-solve cells, public/admin snapshots, SSE streams, freeze, and reveal controls exist. Public endpoints remain public; admin reveal controls are admin-only. | Include as implemented scoreboard feature. |
| Security monitoring UI | Planned / Future Work | `AdminApp` Security Monitor placeholder, no backend security monitor package | Only a placeholder screen exists. Refresh token stores device IP, but no alerting, anomaly detection, or monitoring endpoint exists. | Mark as future work; do not claim monitoring is implemented. |
| Quick statistics panel | Planned / Future Work | `StatsPanel` values are dashes with tooltip "No endpoint yet" | UI placeholders exist without backend aggregate endpoints. | Mark as placeholder. |
| Announcements | Planned / Future Work | No announcement entity/controller/service/UI found | No implementation discovered. | Include only in future scope if desired. |
| Notification/result dissemination | Partially Implemented | Empty `ResultProducer`, `ResultConsumer`, no frontend live verdict stream | Verdicts are persisted, but no result queue consumer/producer or push notification flow exists. | Classify as scaffold/future. |
| LAN-first/offline operation | Partially Implemented | `docker-compose.yml`, `application.yml` default Judge0 URL | Docker Compose runs PostgreSQL, RabbitMQ, backend, and frontend locally, but Judge0 defaults to external `https://ce.judge0.com` unless configured otherwise. | Mark local deployment supported, full offline judging not guaranteed. |
| Contest participation/join workflow | Planned / Future Work | No Team entity, contest_membership table, participation controller, or join UI | Teams are users with `TEAM` role and see the active contest; there is no explicit enrollment per contest. | Mark future work or out of current scope. |
| Placeholder UI surfaces | Partially Implemented | `StatsPanel`, security monitor placeholder | Quick statistics and security monitoring are still placeholder surfaces. Clarifications are wired to backend APIs. | Call out remaining placeholders without misclassifying clarifications. |

## B. Current Architecture Summary

AuraC2 is currently a layered web application composed of:

| Layer | Current implementation |
|---|---|
| Frontend | React + TypeScript + Vite under `UI/src`, with separate auth, admin, and team interfaces. |
| Backend | Spring Boot monolith under `backend/src/main/java/com/server/contestControl`, organized into `authServer`, `contestServer`, and `submissionServer` packages. |
| Persistence | PostgreSQL through Spring Data JPA entities and repositories. |
| Security | Stateless Spring Security with JWT access tokens, HTTP-only refresh-token cookie flow, explicit route guards, method security, signed callback verification, and a guarded local/test-only `no-security` profile. |
| Asynchronous processing | RabbitMQ submission queue for judging dispatch. Result queue exists as scaffold only. |
| External judging | Judge0 API invoked by backend through `RestTemplate`, with callback endpoint under `/api/callback/judge0`. |
| Real-time contest updates | SSE stream for contest lifecycle updates, snapshots, heartbeats, and frontend fallback polling. |
| Deployment | Docker Compose provisions PostgreSQL, RabbitMQ, backend, and frontend. Judge0 remains externally configured unless the deployment overrides it. |

The backend is best described as a modular monolith rather than separate microservices. Package names use `authServer`, `contestServer`, and `submissionServer`, but all modules run in one Spring Boot process and share one database.

## C. Updated Module List

| Module | Main files/classes | Responsibility |
|---|---|---|
| Application bootstrap | `AuraServerApplication`, `application.yml`, `docker-compose.yml` | Starts Spring Boot and configures profiles, database, RabbitMQ, Judge0, JWT, bootstrap admin, and scheduler properties. |
| Security/configuration | `SecurityConfiguration`, `NoSecurityProfileGuard`, `ApplicationConfiguration`, `CookieConfig`, `SchedulerConfig`, `SwaggerConfig` | Security filter chain, password encoder, user-details service, no-security profile guard, cookie environment check, scheduler thread pool, and API documentation config. |
| Authentication | `AuthController`, `AuthFacade`, `LoginService`, `RegistrationService`, `RefreshTokenService`, `LogoutService`, `JwtAuthFilter` | Login, registration, refresh rotation, logout, JWT filtering, and token issuance. |
| User administration | `AdminController`, `UserService`, `UserRepository`, user DTOs | Admin list/update/delete users and review all submissions. |
| Contest lifecycle | `ContestController`, `ContestService`, `ContestLifecycleService`, `ContestRepository`, `ContestUpdatedEvent` | Contest creation, manual transitions, effective state calculation, pause-aware timing, event publishing. |
| Contest scheduling | `ContestTransitionScheduler`, `ContestStatusSyncScheduler`, `ContestStatusSyncService`, `ContestStatusSyncExecutor` | Exact-time auto transition scheduling, fallback periodic sync, row-locked status updates, startup recovery. |
| SSE real-time updates | `ContestStreamController`, `ContestStreamBroadcaster`, `ContestStreamSnapshot`, `useContestStream`, `ContestOverview` | Initial snapshot, lifecycle update broadcast, heartbeat, emitter cleanup, frontend bucket placement and fallback polling. |
| Problem management | `ProblemController`, `ProblemService`, `ProblemRepository`, problem DTOs | Admin problem create/update/delete and role-protected problem retrieval. |
| Test-case management | `TestCaseController`, `TestCaseService`, `TestCaseRepository`, test-case DTOs | Admin test-case create/update/delete and role-filtered public/private test-case listing. |
| Clarifications | `ClarificationController`, `ClarificationService`, `ClarificationRepository`, clarification entity/enums/DTOs, `ClarificationsView`, team `Clarifications` | Team clarification submission, admin reply, public/private answer visibility, and admin/team frontend integration. |
| Submission management | `SubmissionController`, `SubmissionService`, `SubmissionRepository`, `Submission` | Store submissions, retrieve team/admin histories, enforce owner read access for individual submission. |
| Judging | `RabbitMQConfig`, `SubmissionProducer`, `SubmissionConsumer`, `Judge0Service`, `LanguageMapper`, `CallbackHandler`, `Judge0CallbackService` | Queue submissions, dispatch per test case to Judge0, process callbacks, persist per-test-case results, aggregate verdict. |
| Rejudge | `RejudgeController`, `RejudgeService`, `RejudgeSubmissionsRequest`, `RejudgeResponse` | Admin rejudge of selected submissions, problem submissions, or contest submissions. |
| Frontend auth | `App.tsx`, `LoginPage`, `authApi`, `http.ts`, `jwt.ts`, `tokenStore.ts` | Login, silent refresh, JWT role routing, access-token storage, logout. |
| Frontend admin | `admin/App.tsx`, `ContestOverview`, `ProblemsView`, `TeamsView`, `SubmissionsView`, `RejudgeView`, `ClarificationsView`, `StatsPanel`, `PlaceholderView` | Admin dashboard, contest controls, problem/test-case management, user management, submission review, rejudge, clarifications, and remaining placeholders for statistics/security monitor. |
| Frontend team | `team/App.tsx`, `CodeEditor`, `ProblemSidebar`, `SubmissionHistory`, `Header`, `Clarifications`, `useCodeDraft` | Team active-contest workspace, code draft persistence, submissions, problem sidebar, timer, and clarification workflow. |

## D. Updated Entity List

| Entity | Key fields | Relationships | Notes |
|---|---|---|---|
| `User` | `id`, `username`, `password`, account flags, `role` | One user has many `RefreshToken`; user also relates to submissions and clarifications. | There is no separate Team entity. A team is a `User` with role `TEAM`. |
| `RefreshToken` | `id`, `tokenHash`, `deviceIp`, `createdAt`, `expiresAt`, `revoked` | Many refresh tokens belong to one user. | Token hash is stored after JWT refresh token is generated. |
| `Contest` | `id`, `title`, `startTime`, `durationMinutes`, `actualStartTime`, `pausedAt`, `totalPauseMillis`, `description`, `status`, `statusLocked`, `scoreboardFreezeMinutes`, `penaltyMinutes` | One contest has many problems, submissions, and clarifications. | Supports persisted vs effective lifecycle state. |
| `Problem` | `id`, `title`, `description`, `timeLimit`, `memoryLimit`, `difficulty` | Many problems belong to one contest; one problem has many test cases and submissions. | Time/memory limits are metadata; Judge0 request does not enforce them yet. |
| `TestCase` | `id`, `inputData`, `expectedOutput`, `isPublic` | Many test cases belong to one problem. | Public/private visibility is applied by filtering in service. |
| `Clarification` | `id`, `question`, `createdAt`, `standardReply`, `reply`, `repliedAt`, `status`, `replyType` | Belongs to contest, optional problem, user, and optional admin user who replied. | Backend and admin/team frontend workflows are wired. |
| `Submission` | `id`, `code`, `language`, `verdict`, `createdAt`, `executionTime`, `memoryUsage`, `judgeRunId` | Belongs to contest, problem, and user. Has many `SubmissionJudgeResult` rows conceptually. | `judgeRunId` enables rejudge and stale callback protection. |
| `SubmissionJudgeResult` | `id`, `judgeRunId`, `testCaseNumber`, `verdict`, `executionTime`, `memoryUsage`, `receivedAt` | Many results belong to one submission. | Unique constraint on submission/run/test-case. No direct `TestCase` relation; result refers to test case number. |

## E. Updated Endpoint List

| Endpoint | Method | Access | Current status | Code evidence |
|---|---:|---|---|---|
| `/auth/register` | POST | ADMIN by URL rule and method security | Implemented | `AuthController.register`, `@PreAuthorize("hasRole('ADMIN')")`, `SecurityConfiguration`, `JwtAuthFilter` |
| `/auth/login` | POST | Public | Implemented | `AuthController.login`, `LoginService` |
| `/auth/refresh` | POST | Public, cookie-based | Implemented | `AuthController.refreshToken`, `RefreshTokenService` |
| `/auth/logout` | POST | Public by URL config, expects `/auth`-scoped refresh cookie | Implemented | `AuthController.logout`, `LogoutService`, `CookieUtil` |
| `/api/admin/users` | GET | ADMIN | Implemented | `AdminController.getAllUsers` |
| `/api/admin/users/{userId}/name` | PUT | ADMIN | Implemented | `AdminController.updateUserName` |
| `/api/admin/users/{userId}/password` | PUT | ADMIN | Implemented | `AdminController.updateUserPassword` |
| `/api/admin/users/{userId}` | DELETE | ADMIN | Implemented, admin deletion blocked | `AdminController.deleteUser`, `UserService.deleteUser` |
| `/api/admin/users/submissions` | GET | ADMIN | Implemented | `AdminController.getAllSubmissions` |
| `/api/contest` | POST | ADMIN | Implemented | `ContestController.createContest` |
| `/api/contest/{id}/start` | PUT | ADMIN | Implemented | `ContestController.startContest` |
| `/api/contest/{id}/resume` | PUT | ADMIN | Implemented | `ContestController.resumeContest` |
| `/api/contest/{id}/pause` | PUT | ADMIN | Implemented | `ContestController.pauseContest` |
| `/api/contest/{id}/end?juryOverride=true|false` | PUT | ADMIN | Implemented | `ContestController.endContest` |
| `/api/contest/active` | GET | Public | Implemented | `ContestController.getActive` |
| `/api/contest/upcoming` | GET | Public | Implemented | `ContestController.getUpcoming` |
| `/api/contest/paused` | GET | Public | Implemented | `ContestController.getPaused` |
| `/api/contest/ended` | GET | Public | Implemented | `ContestController.getEnded` |
| `/api/contest/stream` | GET SSE | ADMIN | Implemented | `ContestStreamController.stream`, `SecurityConfiguration` |
| `/api/problems` | POST | ADMIN | Implemented | `ProblemController.createProblem` |
| `/api/problems/{id}` | PUT | ADMIN | Implemented | `ProblemController.updateProblem`, `ProblemService.updateProblem` |
| `/api/problems/{id}` | DELETE | ADMIN | Implemented | `ProblemController.deleteProblem`, `ProblemService.deleteProblem` |
| `/api/problems/{id}` | GET | ADMIN or TEAM | Implemented | `ProblemController.getProblem` |
| `/api/problems/contest/{id}` | GET | ADMIN or TEAM | Implemented | `ProblemController.getProblemsByContest` |
| `/api/testcases/{problemId}` | POST | ADMIN | Implemented | `TestCaseController.addTestCase` |
| `/api/testcases/{id}` | PUT | ADMIN | Implemented | `TestCaseController.updateTestCase`, `TestCaseService.updateTestCase` |
| `/api/testcases/{id}` | DELETE | ADMIN | Implemented | `TestCaseController.deleteTestCase`, `TestCaseService.deleteTestCase` |
| `/api/testcases/problem/{problemId}` | GET | ADMIN or TEAM | Implemented with filtering | `TestCaseController.getTestCases`, `TestCaseService` |
| `/api/scoreboard/contests/{contestId}` | GET | Public | Implemented | `ScoreboardController`, `ScoreboardService` |
| `/api/scoreboard/contests/{contestId}/stream` | GET SSE | Public | Implemented | `ScoreboardStreamController`, `ScoreboardBroadcaster` |
| `/api/admin/scoreboard/contests/{contestId}` | GET | ADMIN | Implemented | `AdminScoreboardController` |
| `/api/admin/scoreboard/contests/{contestId}/stream` | GET SSE | ADMIN | Implemented | `AdminScoreboardStreamController` |
| `/api/admin/scoreboard/contests/{contestId}/reveal/*` | POST | ADMIN | Implemented | `AdminScoreboardController` |
| `/api/clarifications` | POST | TEAM | Implemented | `ClarificationController.submitClarification`, team clarification UI |
| `/api/clarifications/my/{contestId}` | GET | TEAM | Implemented | `ClarificationController.getMyClarifications`, team clarification UI |
| `/api/clarifications/admin/contest/{contestId}` | GET | ADMIN | Implemented | `ClarificationController.getContestClarifications`, admin clarification UI |
| `/api/clarifications/admin/all` | GET | ADMIN | Implemented | `ClarificationController.getAllClarifications`, admin clarification UI |
| `/api/clarifications/admin/{id}/reply` | PUT | ADMIN | Implemented | `ClarificationController.replyClarification`, admin clarification UI |
| `/api/clarifications/public/{contestId}` | GET | Public | Implemented | `ClarificationController.getPublicClarifications` |
| `/api/submissions` | POST | TEAM or ADMIN | Partially implemented due validation gaps | `SubmissionController.submit`, `SubmissionService.submitCode` |
| `/api/submissions/{id}` | GET | TEAM or ADMIN | Implemented with owner check for non-admin | `SubmissionController.getSubmission`, `SubmissionService.getSubmissionById` |
| `/api/submissions/my?problemID={id}` | GET | TEAM or ADMIN | Implemented | `SubmissionController.getAllSubmission`, `teamApi.getMySubmissions` |
| `/api/submissions/my/all` | GET | TEAM | Implemented | `SubmissionController.getAllMySubmissions` |
| `/api/admin/rejudge/submissions` | POST | ADMIN | Implemented | `RejudgeController.rejudgeSubmissions` |
| `/api/admin/rejudge/problem/{problemId}` | POST | ADMIN | Implemented | `RejudgeController.rejudgeProblem`, `RejudgeView` |
| `/api/admin/rejudge/contests/{contestId}` | POST | ADMIN | Implemented | `RejudgeController.rejudgeContest`, `RejudgeView` |
| `/api/callback/judge0/{submissionId}/{testCaseNumber}?signature=...` | PUT | Public callback, HMAC protected | Legacy signed route for run zero; stale after run id exists | `CallbackHandler.handleLegacyJudge0Callback` |
| `/api/callback/judge0/{submissionId}/{judgeRunId}/{testCaseNumber}?signature=...` | PUT | Public callback, HMAC protected | Implemented; rejects missing or invalid signatures before state mutation | `CallbackHandler.handleJudge0Callback` |

## F. Updated Event / Listener / Scheduler List

| Item | Type | Status | Purpose |
|---|---|---|---|
| `ContestUpdatedEvent` | Application event | Implemented | Carries lifecycle reason and contest snapshot. |
| `ContestStreamBroadcaster.onContestUpdated` | `@TransactionalEventListener(fallbackExecution = true)` | Implemented | Broadcasts `contest-update` SSE events after contest changes. |
| `ContestTransitionScheduler.onContestUpdated` | `@TransactionalEventListener` + `REQUIRES_NEW` | Implemented | Reschedules or cancels exact-time transition tasks after contest changes. |
| `ContestTransitionScheduler.onApplicationReady` | `@EventListener(ApplicationReadyEvent.class)` | Implemented | Restores scheduled transition tasks from persisted contests after restart. |
| `ContestTransitionScheduler` | One-shot task scheduler | Implemented | Schedules next auto-start or auto-end at exact time. |
| `ContestStatusSyncScheduler` | `@Scheduled` fallback | Implemented | Periodically syncs persisted contest state with effective state. |
| `ContestStreamBroadcaster.heartbeat` | `@Scheduled(fixedDelay = 15000)` | Implemented | Sends SSE keepalive comments. |
| `SchedulerConfig.taskScheduler` | `ThreadPoolTaskScheduler` | Implemented | Shared scheduler with pool size 3 and graceful shutdown. |

## G. Updated Queue and Asynchronous Flow List

| Flow | Status | Components | Notes |
|---|---|---|---|
| Submission queue | Implemented | `RabbitMQConfig.SUBMISSION_QUEUE`, `SubmissionProducer`, `SubmissionConsumer` | Stores only submission ID as message payload. New submissions publish after commit; consumer uses a pessimistic lock before dispatch. |
| Judge0 dispatch | Implemented | `SubmissionConsumer`, `LanguageMapper`, `Judge0Service` | Consumer sends one Judge0 request per test case. |
| Judge0 callback processing | Implemented | `CallbackHandler`, `Judge0CallbackSignatureService`, `Judge0CallbackService`, `SubmissionJudgeResultRepository` | Callback verifies HMAC signature, writes each per-case result idempotently, and finalizes after all tests arrive. |
| Rejudge republish | Implemented backend | `RejudgeService`, `SubmissionProducer`, `SubmissionConsumer` | Rejudge resets final submissions to `PENDING_REJUDGE` and republishes after commit. |
| Result queue | Partially Implemented | `RabbitMQConfig.RESULT_QUEUE`, empty `ResultProducer`, empty `ResultConsumer` | Queue exists but no result notification flow is implemented. |
| SSE contest updates | Implemented | `ContestUpdatedEvent`, `ContestStreamBroadcaster`, `ContestOverview` | Pushes contest lifecycle updates, not submission verdict updates. |
| Team verdict refresh | Partially Implemented | `teamApi.getMySubmissions`, `SubmissionHistory` | Team UI reads history via REST but has no live verdict push or polling loop discovered. |

## H. Updated Frontend Screen / Hook / Component List

| UI area | Component/hook/service | Status | Notes |
|---|---|---|---|
| Root routing | `UI/src/App.tsx` | Implemented | Decodes JWT role and routes to admin or team app. |
| Login | `LoginPage`, `authApi`, `tokenStore`, `jwt` | Implemented | Login, local access-token storage, silent refresh. |
| Admin layout | `admin/App.tsx`, `Sidebar`, `TopNav` | Implemented | Overview, Teams, Problems, Submissions, placeholder views. |
| Contest overview | `ContestOverview`, `CreateContestModal`, `useContestStream` | Implemented | Create contest, manual lifecycle controls, SSE, fallback hydration/polling, freeze badge. |
| Problems admin | `ProblemsView`, `CreateProblemModal` | Implemented for create/list/view/edit/delete | Backend supports update/delete; admin UI is wired for problem management. |
| Test cases admin | `TestCasesPanel`, `AddTestCaseModal` | Implemented for add/list/edit/delete | Backend supports update/delete; admin UI is wired for test-case management. |
| Teams admin | `TeamsView`, `RegisterModal` | Implemented | List users, register team, update username/password, delete non-admin. |
| Admin submissions | `SubmissionsView`, `RejudgeView` | Implemented | Search/filter, code dialog, user/problem label enrichment, and rejudge workflows. |
| Admin clarifications | `ClarificationsView`, `useClarificationStream` | Implemented | Loads backend clarifications, replies, filters, and receives SSE updates. |
| Security monitor | `PlaceholderView` through `admin/App.tsx` | Planned / Future Work | No backend endpoints. |
| Quick statistics | `StatsPanel` | Planned / Future Work | Static dashes and tooltip "No endpoint yet". |
| Team workspace | `team/App.tsx`, `Header`, `ProblemSidebar`, `CodeEditor`, `SubmissionHistory` | Partially Implemented | Uses active contest, problems, submissions, code editor. Problem statement display is minimal and no live submission updates. |
| Code draft persistence | `useCodeDraft`, `DraftIndicator` | Implemented, indicator appears unused | Draft hook saves to localStorage. `DraftIndicator` exists but is not visibly integrated into `CodeEditor`. |
| Team clarifications | `team/components/Clarifications.tsx`, `useClarificationStream` | Implemented | Submits questions, loads own/public clarifications, and receives SSE updates. |

## I. Current Limitations and Risks

1. Email verification is no longer implemented despite verification exception classes and `/verify/**` security remnants.
2. Admin bootstrap rotates the existing admin password on every startup, which is operationally sensitive and should be explained.
3. New submission RabbitMQ publish failures after commit are logged, but the team response already contains the committed submission.
4. Problem time and memory limits are stored but not passed to Judge0 for enforcement.
5. Zero-test-case submissions are marked `INTERNAL_ERROR`; this protects judging state but should still be prevented earlier by problem authoring validation.
6. Unsupported language throws in the RabbitMQ consumer path without a user-facing validation response at submission time.
7. Result queue is configured but producer/consumer are empty.
8. Clarifications are wired end-to-end; remaining risk is workflow polish and operator review around public/private reply behavior.
9. Rejudge backend and admin UI exist; publish failures are logged after commit but not reflected in `RejudgeResponse`.
10. Memory is stored from Judge0 in KB, while admin UI labels memory as MB.
11. Scoreboard ranking, freeze, reveal, public/admin snapshots, and streams are implemented; remaining risk is broader contest-report/export coverage.
12. Security Monitor and Quick Statistics are placeholders.
13. There is no explicit contest participation/join workflow or team-contest membership model.
14. Team workspace does not appear to poll or subscribe for verdict changes after submission.
15. Test-case public response includes expected output for public tests. This may be acceptable for sample tests, but the report should distinguish sample/public tests from hidden tests.
16. There are duplicate exception packages under `contestServer.exception` and `contestServer.exceptions`, which may confuse documentation and maintenance.

## J. Recommended Report Outline

Use the instructor template as the required structure, with these code-aligned section emphases:

1. Introduction
2. Project Overview and Objectives
   1. Project Overview
   2. Current System Architecture
   3. Main Modules
   4. Objectives and Target Audience
   5. Current Scope vs Future Scope
3. Literature Review
   1. PC2, Codeforces, DOMjudge, and online judge systems as academic comparison targets
   2. Make it clear these comparisons contextualize AuraC2 and are not source of truth for AuraC2 implementation
4. Requirement Phase
   1. Implemented Functional Requirements
   2. Partially Implemented Functional Requirements
   3. Planned / Future Work Requirements
   4. Deprecated / Removed Requirements
   5. Non-Functional Requirements
5. Analysis Phase
   1. Use Case Diagrams split by subsystem
   2. Use Case Specifications
   3. Activity Diagrams for authentication, contest lifecycle, SSE, judging, rejudge, and clarifications
6. Design Phase
   1. Application Architecture Design / Context Diagram
   2. Data Architecture Design
   3. ER Diagram and relational schema
   4. Gap Analysis: Current System vs Intended Design
7. Implementation Phase
   1. Language Used
   2. Tools Used
   3. Templates / UI Structure
   4. Screenshots
   5. Scenarios
   6. Sample Reports
8. References
9. Appendix A: Code
10. Appendix B: Screenshots of the System Screens
11. Appendix C: CD / Deployment Package

## K. Recommended Figure List

| Figure | Title | Status |
|---:|---|---|
| 1 | System Context Diagram | Recommended |
| 2 | Backend Modular Architecture Diagram | Recommended |
| 3 | Current Scope vs Future Scope Diagram | Recommended |
| 4 | Contest Administration Use Case Diagram | Recommended |
| 5 | Team Contest Workspace Use Case Diagram | Recommended |
| 6 | Authentication and Session Lifecycle Activity Diagram | Recommended |
| 7 | Persisted State vs Effective State Diagram | Recommended |
| 8 | Exact-Time Scheduler and Fallback Sync Diagram | Recommended |
| 9 | SSE Connection and Contest Update Flow | Recommended |
| 10 | Judge0 Callback and Per-Test-Case Result Flow | Recommended |
| 11 | Rejudge Workflow Diagram | Recommended |
| 12 | Clarification Backend vs Frontend Gap Diagram | Recommended |
| 13 | Contest Lifecycle Architecture Diagram | Recommended |
| 14 | Submission and Asynchronous Judging Architecture Diagram | Recommended |
| 15 | Current ER Diagram | Recommended |
| 16 | Submission Judging Mini ER Diagram | Recommended |
| 17 | Relational Schema Diagram | Recommended |

## L. Recommended Diagram Split Strategy

Large old diagrams should be split by subsystem. No diagram should combine authentication, contest lifecycle, judging, clarification, scoreboard, and security monitoring in one view. Use these split rules:

1. Keep use case diagrams actor-focused: authentication, contest admin, team workspace, judging/rejudge, and future features.
2. Keep lifecycle diagrams small: create/schedule, manual start, auto start, pause/resume, end, effective state, startup recovery, fallback sync.
3. Keep judging diagrams small: intake, queue dispatch, callback processing, verdict aggregation, rejudge, stale callback protection.
4. Use a full ER diagram for entities, but also add a mini ER for submission/judge results because that is a key change from the old report.
5. Show SSE separately from general architecture because it has snapshot, incremental update, heartbeat, and fallback behavior.

## M. Old Report Sections That Must Be Replaced

1. Any ER section claiming only six persistent entities must be replaced. Current code has eight main entities: `User`, `RefreshToken`, `Contest`, `Problem`, `TestCase`, `Clarification`, `Submission`, and `SubmissionJudgeResult`.
2. Any claim that clarifications are only planned or frontend-only must be updated: backend and admin/team frontend workflows are wired.
3. Any claim that callback ordering remains unresolved must be replaced: per-test-case results and `judgeRunId` now address stale callbacks and out-of-order results.
4. Any submission workflow that lacks rejudge must be replaced with the current rejudge backend flow.
5. Any contest lifecycle explanation without effective state, pause-aware time, exact-time scheduler, fallback scheduler, row locking, and SSE must be replaced.
6. Any architecture diagram that omits SSE, schedulers, `SubmissionJudgeResult`, `Clarification`, or rejudge is outdated.
7. Any old statement that treats the scoreboard as unimplemented must be replaced: ranking, freeze, reveal, public/admin snapshots, and streams are implemented.
8. Any statement that security monitoring is implemented must be replaced with placeholder/future-work status.
9. Any statement that the system is fully LAN/offline must be qualified because Judge0 defaults to an external URL.

## N. Old Diagrams That Should Be Discarded or Split

| Old diagram type | Action | Reason |
|---|---|---|
| Large full-system flow diagram | Split | Too broad; should become context, lifecycle, SSE, judging, and rejudge diagrams. |
| Old six-entity ER diagram | Replace | Missing `Clarification` and `SubmissionJudgeResult`; submission result modeling changed. |
| Old submission workflow | Replace | Current flow includes judgeRunId, per-test-case result rows, stale callback rejection, and rejudge. |
| Old clarification workflow | Replace/update | Backend and admin/team UI are now wired; diagrams should show the implemented workflow. |
| Old intended full-system use case diagram | Split and mark future features | Future work should not appear as implemented use cases. |
| Old architecture diagram without SSE | Replace | SSE and scheduler/event architecture are major current implementation features. |

## O. Old Sections That Can Be Reused With Updates

1. Formal front matter style, acknowledgement tone, and table-of-contents pattern.
2. General introduction of AuraC2 as a university contest control system.
3. High-level comparison to contest systems, after adding proper references in Phase 2.
4. Basic explanation of role-based admin/team interfaces, updated with exact code evidence.
5. General description of RabbitMQ and Judge0 as asynchronous judging technologies, updated with per-test-case and rejudge details.
6. Functional requirement style, but requirements must be reclassified against current code.
7. Use case specification format, with new or revised scenarios for SSE, rejudge, and clarifications.

## P. Questions / Unknowns That Need Human Confirmation

1. Should the final report keep the project name as `AuraC2` or use the styled `AuraC^2`/`AuraC squared` form on the title page?
2. Should the team/member/supervisor/date information from the old report be reused exactly in the final DOCX?
3. Should the report include a dedicated clarification workflow screenshot now that admin and team clarification screens are wired?
4. Should scoreboard export/reporting be treated as future work while live ranking/freeze/reveal remains implemented?
5. Should the final report include screenshots from the current running UI, including implemented clarification and rejudge screens?
6. Should the final report include code appendix excerpts or only file/class references?
7. Do you want the literature review references gathered from external sources in Phase 2, or should we keep it source-neutral and brief?
8. Should old diagrams be visually referenced in an appendix, or fully replaced with new smaller diagrams?

## Q. Human Decisions Before Final DOCX

1. Should admin password rotation on startup remain as-is, or should it be changed to create-once/manual-rotate behavior before final submission?
2. Which screenshots should be inserted into the Implementation Phase and Appendix B?
3. Should remaining placeholder screens such as security monitoring and quick statistics be included honestly or excluded from screenshots?
4. Which external references should be used in the Literature Review: PC2, DOMjudge, Codeforces, Judge0, Spring Boot, Spring Security, RabbitMQ, PostgreSQL, React, and SSE are recommended.
5. Should all diagrams stay in the body, or should some move to an appendix? For now, all diagrams remain in the body as requested.
6. Confirm final title, team members, supervisor name, course information, university formatting, and submission date details.
