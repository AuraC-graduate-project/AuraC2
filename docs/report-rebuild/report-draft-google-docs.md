# AuraC2 - System Analysis and Design Report Draft

Yarmouk University  
Information Technology and Computer Science  
Information Systems (IS)  

Project Title: AuraC2 - Aura Contest Control  

Team Members: [Insert Team Members and Student IDs]  
Supervisor: [Insert Supervisor Name]  
Date: [Insert Date]  

## Acknowledgement

We would like to express our sincere gratitude to our supervisor for the guidance, feedback, and academic support provided throughout the development and analysis of this project. This support helped transform AuraC2 from a contest-control idea into a structured software system with a clear architecture, defined requirements, and an implementation that can be evaluated against its design goals.

We also extend our appreciation to Yarmouk University and the Information Systems Department for providing the academic environment that made this work possible.

## Table of Contents

[Insert generated Table of Contents here]

## Table of Figures

Figure 1. System Context Diagram  
Figure 2. Backend Modular Architecture Diagram  
Figure 3. Current Scope vs Future Scope Diagram  
Figure 4. Contest Administration Use Case Diagram  
Figure 5. Team Contest Workspace Use Case Diagram  
Figure 6. Authentication and Session Lifecycle Activity Diagram  
Figure 7. Persisted State vs Effective State Diagram  
Figure 8. Exact-Time Scheduler and Fallback Sync Diagram  
Figure 9. SSE Connection and Contest Update Flow  
Figure 10. Judge0 Callback and Per-Test-Case Result Flow  
Figure 11. Rejudge Workflow Diagram  
Figure 12. Clarification Workflow Diagram
Figure 13. Contest Lifecycle Architecture Diagram  
Figure 14. Submission and Asynchronous Judging Architecture Diagram  
Figure 15. Current ER Diagram  
Figure 16. Submission Judging Mini ER Diagram  
Figure 17. Relational Schema Diagram

# 1. Introduction

AuraC2, also referred to as Aura Contest Control, is a web-based programming contest management system designed for university competitive programming environments. The system supports two main roles: administrators who prepare and control contests, and team users who participate in active contests by reading problems, writing code, submitting solutions, and reviewing submission history.

The current implementation is a real web application composed of a Spring Boot backend, a React frontend, PostgreSQL persistence, RabbitMQ asynchronous messaging, and Judge0 integration for code execution. The system is not only a static design proposal; it includes implemented authentication, contest lifecycle control, problem and test-case management, asynchronous judging, per-test-case result tracking, backend/frontend clarification handling, real-time contest lifecycle updates using server-sent events, and backend/admin rejudge functionality.

This report rebuilds the previous system analysis and design documentation so that it reflects the current local codebase. The codebase is treated as the source of truth. Features that exist only as placeholder UI or unused scaffolding are not described as complete. Features that were previously planned but now have backend implementation are reclassified accordingly. Features that exist only as unused scaffolding or old exception remnants are identified as partial, future work, or deprecated.

# 2. Project Overview and Objectives

## 2.1 Project Overview

AuraC2 is a contest control platform for programming competitions. It provides role-based authentication, administrator contest control, problem and test-case authoring, team code submission, asynchronous judging, and submission review. The application separates administrative workflows from team workflows in the frontend while maintaining a single Spring Boot backend.

From the current codebase, the system is best described as a modular monolith. The backend uses separate package areas named `authServer`, `contestServer`, and `submissionServer`, but these packages run in one Spring Boot application and share a common database. This architecture allows the system to be organized by responsibility while avoiding the deployment complexity of separate microservices.

The system uses RabbitMQ for judging workflow decoupling. When a team submits code, the backend persists the submission first, then publishes a submission identifier to a queue after the database transaction commits. A consumer later dispatches the submission to Judge0 per test case. Judge0 sends signed callbacks to the backend, and the backend verifies the signature, stores each test-case result idempotently, and then calculates the final verdict.

## 2.2 Current System Architecture

The current architecture contains the following primary elements:

| Element | Current implementation |
|---|---|
| Frontend | React and TypeScript application built with Vite. |
| Backend | Spring Boot application using Java 21. |
| Database | PostgreSQL through Spring Data JPA repositories. |
| Authentication | JWT access tokens and HTTP-only refresh-token cookies. |
| Queue | RabbitMQ submission queue for asynchronous judging. |
| Judge | Judge0 API through backend HTTP requests and callback endpoint. |
| Real-time updates | Server-sent events for contest lifecycle snapshots and updates. |
| Deployment support | Docker Compose for PostgreSQL, RabbitMQ, backend, and frontend. |

[Insert Figure 1 here: System Context Diagram]

Figure 1. System Context Diagram

Purpose: To show the main runtime systems around AuraC2.  
Description: The diagram should show the browser-based React frontend, the Spring Boot backend, PostgreSQL, RabbitMQ, and Judge0. It should also show REST API calls, SSE contest updates, submission queue messages, Judge0 requests, and Judge0 callbacks.  
Code Alignment: `UI/src/App.tsx`, `backend/src/main/java/com/server/contestControl`, `RabbitMQConfig`, `Judge0Service`, `ContestStreamController`, and `docker-compose.yml`.  
Current Status: Implemented as a modular monolith with external Judge0 dependency.

## 2.3 Main Modules

| Module | Status | Description |
|---|---|---|
| Authentication and Security | Implemented | Handles login, administrator-protected team registration, JWT validation, refresh-token persistence, rotation, logout revocation, cookie clearing, explicit route guards, and the guarded local/test-only `no-security` profile. |
| Admin Bootstrap | Implemented | Creates or rotates the single admin account at startup and writes generated credentials to `admin-account.txt`; password rotation on every startup is operationally sensitive. |
| Contest Lifecycle | Implemented | Supports creation, manual transitions, automatic transitions, pause/resume timing, and effective state resolution. |
| Real-Time Contest Updates | Implemented | Uses SSE snapshot, contest-update events, heartbeat, and frontend fallback polling. |
| Problem Management | Implemented | Supports contest-bound problem creation, retrieval, update, and deletion with admin-only mutation endpoints. |
| Test-Case Management | Implemented | Supports public/private test-case visibility by role and admin-only create/update/delete endpoints. |
| Submission and Judging | Implemented with noted judging limitations | Supports submission persistence, after-commit RabbitMQ queueing, Judge0 dispatch, signed callbacks, per-case results, and final verdict calculation. |
| Rejudge Backend and UI | Implemented | Admin-only backend endpoints and admin UI requeue selected/problem/contest submissions for rejudging. |
| Clarifications | Implemented | Backend and admin/team frontend support team questions, admin public/private replies, public answered clarifications, and clarification SSE updates. |
| Scoreboard Ranking | Implemented | Public/admin snapshots, SSE streams, ICPC-style ranking, freeze behavior, and admin reveal controls are implemented. |
| Security Monitoring | Planned / Future Work | UI placeholder exists, but no backend monitoring subsystem exists. |

[Insert Figure 2 here: Backend Modular Architecture Diagram]

Figure 2. Backend Modular Architecture Diagram

Purpose: To present the modular monolith structure.  
Description: The diagram should show `authServer`, `contestServer`, and `submissionServer`, with their controllers, services, entities, repositories, queues, schedulers, and SSE responsibilities.  
Code Alignment: Backend package tree under `backend/src/main/java/com/server/contestControl`.  
Current Status: Implemented as a single deployable Spring Boot application.

## 2.4 Objectives and Target Audience

The main objective of AuraC2 is to support controlled programming contests in a university environment. The administrator should be able to prepare contests, create problems, add test cases, manage team accounts, control contest lifecycle state, and review submissions. Team users should be able to authenticate, enter the contest workspace, choose a problem, write code, submit a solution, and review previous submissions.

The target audience includes contest administrators, programming teams, instructors, and technical operators responsible for contest deployment. The current system emphasizes contest control, judging, scoreboard visibility, clarification handling, and administrative workflows. Future versions should improve reporting/export, security monitoring, announcements, participation management, and offline operation.

## 2.5 Current Scope vs Future Scope

| Category | Features |
|---|---|
| Implemented | Login, administrator-protected team registration, refresh-token rotation, logout refresh-token revocation, production-aware refresh-cookie flags, admin bootstrap, role-based authorization, user management, contest creation, manual lifecycle controls, automatic lifecycle synchronization, SSE contest updates, problem creation/listing, test-case creation/listing, submission persistence, after-commit RabbitMQ judging, signed Judge0 callbacks, per-test-case results, stale callback protection, backend/admin rejudge, backend/frontend clarifications, scoreboard ranking, freeze, and reveal. |
| Partially Implemented | Team workspace completeness, test-case visibility detail, result queue, time/memory limit enforcement, local/offline deployment. |
| Planned / Future Work | Announcements, full security monitoring, statistics endpoints, explicit contest participation/join workflow, full LAN-first Judge0 deployment. |
| Deprecated / Removed | Email verification workflow. Only exception classes and security allow-list remnants remain. |

[Insert Figure 3 here: Current Scope vs Future Scope Diagram]

Figure 3. Current Scope vs Future Scope Diagram

Purpose: To visually separate implemented features from partial, future, and removed features.  
Description: The diagram should use four groups: Implemented, Partially Implemented, Planned/Future Work, and Deprecated/Removed.  
Code Alignment: Feature classification based on controllers, services, entities, frontend components, and configuration.  
Current Status: Recommended for final report.

# 3. Literature Review

Competitive programming contest systems generally combine contest administration, problem delivery, submission management, automated judging, ranking, and communication between contestants and judges. AuraC2 belongs to this family of systems, but its current implementation focuses on a university-controlled contest environment rather than a public large-scale online contest platform.

Systems such as PC2, DOMjudge, Codeforces, and similar online judges provide useful comparison points. PC2 and DOMjudge are commonly associated with formal contest operations, team accounts, problem sets, submissions, judging, and scoreboard behavior. Codeforces is a broader online programming platform that combines contests, practice, user profiles, rating, submissions, and community functions. Compared with these systems, AuraC2 currently implements the local contest-control foundation: authentication, contest lifecycle control, problem/test-case management, asynchronous judging, scoreboard behavior, and submission review.

The main difference is scope. AuraC2 does not currently implement online community functions, announcements, explicit contest participation, or a complete monitoring subsystem. However, it includes design decisions that are important for contest reliability, such as persisted submission records before judging, RabbitMQ-based asynchronous dispatch, per-test-case result storage, signed callbacks, `judgeRunId` stale callback protection, and server-sent events for real-time contest and judging updates.

[Insert References here for PC2, DOMjudge, Codeforces, Judge0, Spring Boot, RabbitMQ, PostgreSQL, and server-sent events during Phase 2.]

# 4. Requirement Phase

## 4.1 Functional Requirements

### Implemented Requirements

| ID | Requirement | Actors | Current evidence | Status |
|---|---|---|---|---|
| FR-IMP-01 | Authenticate users with username and password. | Visitor, Admin, Team | `AuthController`, `LoginService`, `LoginPage` | Implemented |
| FR-IMP-02 | Route users by role after login. | Admin, Team | `App.tsx`, `jwt.ts`, `Role` enum | Implemented |
| FR-IMP-03 | Persist, rotate, and revoke refresh tokens. | Authenticated User | `RefreshToken`, `RefreshTokenService`, `RefreshTokenValidator`, `LogoutService`, `CookieUtil` | Implemented |
| FR-IMP-04 | Bootstrap one admin account. | System Operator | `AdminBootstrapRunner` | Implemented |
| FR-IMP-05 | Manage team/user accounts, including administrator-protected team registration. | Administrator | `AdminController`, `UserService`, `TeamsView`, `AuthController.register`, `@PreAuthorize("hasRole('ADMIN')")` | Implemented |
| FR-IMP-06 | Create a contest with schedule, duration, freeze, and penalty settings. | Administrator | `ContestController`, `ContestService`, `CreateContestModal` | Implemented |
| FR-IMP-07 | Start, pause, resume, and end contests manually. | Administrator | `ContestController`, `ContestService.updateStatus`, `ContestOverview` | Implemented |
| FR-IMP-08 | Automatically start and end contests based on effective state. | Backend System | `ContestTransitionScheduler`, `ContestStatusSyncScheduler`, `ContestStatusSyncExecutor` | Implemented |
| FR-IMP-09 | Stream contest lifecycle changes to the admin UI. | Administrator, Backend | `ContestStreamController`, `ContestSseAdapter`, `ContestSseRegistry`, `useContestStream` | Implemented |
| FR-IMP-10 | Create, retrieve, update, and delete contest problems. | Administrator, Team | `ProblemController`, `ProblemService`, `ProblemsView`, `teamApi` | Implemented |
| FR-IMP-11 | Add, retrieve, update, and delete test cases with public/private filtering. | Administrator, Team | `TestCaseController`, `TestCaseService`, `TestCasesPanel` | Implemented with visibility notes |
| FR-IMP-12 | Submit code for judging. | Team, Administrator | `SubmissionController`, `SubmissionService`, `CodeEditor` | Implemented with judging limitations |
| FR-IMP-13 | Dispatch submissions asynchronously to Judge0. | Backend, RabbitMQ, Judge0 | `SubmissionProducer`, `SubmissionConsumer`, `Judge0Service` | Implemented |
| FR-IMP-14 | Store per-test-case judging results. | Backend | `SubmissionJudgeResult`, `Judge0CallbackService` | Implemented |
| FR-IMP-15 | Reject unsigned, invalid, stale, or duplicate Judge0 callbacks. | Backend, Judge0 | `Judge0CallbackSignatureService`, `judgeRunId`, `Judge0CallbackService.isStaleCallback`, `SubmissionJudgeResult` unique constraint | Implemented |
| FR-IMP-16 | Review submission history. | Team, Administrator | `SubmissionController`, `SubmissionHistory`, `SubmissionsView` | Implemented |
| FR-IMP-17 | Rejudge selected, problem, or contest submissions through backend endpoints and admin UI. | Administrator | `RejudgeController`, `RejudgeService`, `RejudgeView` | Implemented |
| FR-IMP-18 | Submit and answer clarifications through backend endpoints and admin/team UI. | Team, Administrator | `ClarificationController`, `ClarificationService`, `Clarification`, `ClarificationsView`, team `Clarifications` | Implemented |
| FR-IMP-19 | View public/team and admin scoreboard snapshots and streams with freeze/reveal behavior. | Visitor, Team, Administrator | `ScoreboardController`, `AdminScoreboardController`, scoreboard UI | Implemented |

### Partially Implemented Requirements

| ID | Requirement | Reason for partial status | Evidence |
|---|---|---|---|
| FR-PART-01 | Team contest workspace. | Team can load contest/problems, submit code, and view history, but problem statement presentation is minimal and no live verdict refresh was found. | `team/App.tsx`, `CodeEditor`, `SubmissionHistory` |
| FR-PART-02 | Live verdict delivery. | Submissions persist verdicts, but no dedicated live result queue or verdict push flow was found. | `ResultProducer`, `ResultConsumer`, `SubmissionHistory` |
| FR-PART-03 | Result notification queue. | Queue is configured, but producer and consumer are empty. | `RabbitMQConfig`, `ResultProducer`, `ResultConsumer` |
| FR-PART-04 | Time and memory limits. | Problem fields exist, but Judge0 request does not pass enforcement parameters. | `Problem`, `Judge0SubmissionDTO`, `Judge0Service` |
| FR-PART-05 | Contest report/export workflow. | Scoreboard and submissions exist, but no formal export/report endpoint was found. | `ScoreboardController`, `AdminController.getAllSubmissions` |
| FR-PART-06 | Local/offline deployment. | Docker Compose supports local services, but Judge0 defaults to external Judge0 CE. | `docker-compose.yml`, `application.yml` |

### Planned / Future Work Requirements

| ID | Requirement | Evidence for future status |
|---|---|---|
| FR-PLAN-01 | Announcements. | No announcement entity, controller, or UI workflow was found. |
| FR-PLAN-02 | Contest notification center. | No announcement entity, service, endpoint, or screen was found. |
| FR-PLAN-03 | Security monitoring. | Admin screen is placeholder; no backend monitoring package exists. |
| FR-PLAN-04 | Dashboard statistics. | `StatsPanel` uses dashes and tooltip "No endpoint yet." |
| FR-PLAN-05 | Explicit contest participation/join workflow. | No membership table or join controller exists. |
| FR-PLAN-06 | Advanced reporting/export. | No formal report export workflow was found. |

### Deprecated / Removed Requirements

| ID | Requirement | Evidence |
|---|---|---|
| FR-REM-01 | Email verification workflow. | Verification-related exceptions and `/verify/**` allow-list remain, but there is no verification controller, token entity, email verification service, or active workflow. |

## 4.2 Non-Functional Requirements

| Requirement | Current support | Status |
|---|---|---|
| Security | JWT authentication, BCrypt password encoding, route-level and method-level authorization, admin-only team registration, refresh-token hashing, revocation flag, token ownership checks, production-aware Secure cookie behavior, shared `/auth` refresh-cookie path for refresh/logout, signed Judge0 callbacks, and guarded `no-security` profile usage. | Implemented |
| Reliability | Submissions are persisted before judging; scheduler fallback exists; row locks protect contest auto-sync and callback updates. | Implemented |
| Performance | Judging is asynchronous through RabbitMQ, avoiding direct execution in the submission request. | Implemented |
| Scalability | Queue-based judging can be extended with more consumers, though the backend remains a monolith. | Partially implemented |
| Maintainability | Controllers, services, repositories, DTOs, entities, enums, events, schedulers, and UI components are separated. | Implemented |
| Usability | Role-specific admin/team UI exists, including contest control, problem/test-case management, submissions, rejudge, scoreboard, and clarifications; quick statistics and security monitoring remain placeholders. | Partially implemented |
| Data Integrity | Enums and foreign-key relationships model roles, contest status, verdicts, clarifications, and judging results. Row locks protect selected critical updates, including contest status synchronization and callback updates. | Implemented with noted judging limitations |
| Extensibility | Package structure supports extending scoreboard reporting, notifications, monitoring, and additional UI integrations. | Implemented as design capacity |
| Portability / Deployment Flexibility | Docker Compose supports local PostgreSQL/RabbitMQ/backend/frontend deployment. Judge0 needs explicit local configuration for full offline use. | Partially implemented |
| Observability | Logging exists in lifecycle, scheduler, judging, rejudge, and SSE components, but no metrics dashboard or monitoring subsystem exists. | Partially implemented |

# 5. Analysis Phase

## 5.1 Use Case Diagrams

[Insert Figure 4 here: Contest Administration Use Case Diagram]

Figure 4. Contest Administration Use Case Diagram

Purpose: To show implemented administrator operations.  
Description: The administrator can create and control contests, manage problems and test cases, manage teams, and review submissions.  
Code Alignment: `AdminController`, `ContestController`, `ProblemController`, `TestCaseController`, `ContestOverview`, `TeamsView`, `ProblemsView`, `SubmissionsView`.  
Current Status: Implemented except statistics and security monitor placeholders.

[Insert Figure 5 here: Team Contest Workspace Use Case Diagram]

Figure 5. Team Contest Workspace Use Case Diagram

Purpose: To show team contest operations.  
Description: A team can log in, view the active contest, select problems, write code, submit code, view submission history, view the scoreboard, submit clarifications, and view clarification answers.  
Code Alignment: `team/App.tsx`, `ProblemSidebar`, `CodeEditor`, `SubmissionHistory`, `teamApi`, `Clarifications.tsx`.  
Current Status: Implemented for contest workspace, submissions, scoreboard, and clarifications; partial for live verdict updates.

## 5.2 Use Case Specifications

### UC-01 Log In and Establish Session

| Field | Description |
|---|---|
| Status | Implemented |
| Primary Actor | Unauthenticated user |
| Goal | Obtain an access token and enter the correct role-based interface. |
| Preconditions | User account exists. |
| Main Flow | User enters credentials, frontend calls `/auth/login`, backend validates password, backend creates refresh-token record, backend returns access token and sets refresh cookie, frontend stores access token and routes by role. |
| Alternative Flows | Invalid username/password returns an error. Invalid role decoding returns user to login. |
| Code Evidence | `LoginPage`, `AuthController`, `LoginService`, `TokenIssuerUtil`, `jwt.ts`. |

### UC-02 Refresh Session

| Field | Description |
|---|---|
| Status | Implemented |
| Primary Actor | Authenticated user |
| Goal | Replace an expired or missing access token using the refresh-token cookie. |
| Preconditions | Valid non-revoked refresh cookie exists. |
| Main Flow | Frontend calls `/auth/refresh`, backend validates JWT claims, token hash, owner, and revoked flag, old refresh token is revoked, new access/refresh tokens are issued, frontend stores the new access token. |
| Alternative Flows | Missing, revoked, invalid, or expired refresh token fails refresh and clears frontend token state. |
| Code Evidence | `RefreshTokenService`, `RefreshTokenValidator`, `RefreshTokenRepoService`, `http.ts`, `admin/services/api.ts`. |

### UC-03 Create and Control Contest

| Field | Description |
|---|---|
| Status | Implemented |
| Primary Actor | Administrator |
| Goal | Create and manage contest lifecycle state. |
| Preconditions | Administrator is authenticated. |
| Main Flow | Admin creates contest, backend validates future start time and no conflicting active effective contest, contest is saved as UPCOMING, admin starts/pauses/resumes/ends as allowed, backend publishes `ContestUpdatedEvent`, frontend receives SSE update. |
| Alternative Flows | Invalid transition is rejected. Early end requires jury override. |
| Code Evidence | `ContestController`, `ContestService`, `ContestLifecycleService`, `ContestOverview`. |

### UC-04 Auto Start and Auto End Contest

| Field | Description |
|---|---|
| Status | Implemented |
| Primary Actor | Backend scheduler |
| Goal | Keep persisted contest status aligned with effective contest state. |
| Preconditions | Contest is UPCOMING or RUNNING and is not status-locked. |
| Main Flow | Exact-time scheduler schedules next transition, task calls sync service, executor locks contest row, effective state is calculated, persisted status is updated, event is published, SSE clients are updated. |
| Alternative Flows | Fallback scheduler periodically calls the same sync service if exact scheduling is missed or after server recovery. |
| Code Evidence | `ContestTransitionScheduler`, `ContestStatusSyncScheduler`, `ContestStatusSyncService`, `ContestStatusSyncExecutor`, `ContestRepository.findByIdWithLock`. |

### UC-05 Submit Code and Receive Verdict

| Field | Description |
|---|---|
| Status | Implemented with judging limitations |
| Primary Actor | Team user |
| Goal | Submit a solution and eventually receive a verdict. |
| Preconditions | User is authenticated and an effective RUNNING contest exists. |
| Main Flow | Team writes code, frontend posts `/api/submissions`, backend creates PENDING submission, publishes the submission ID to RabbitMQ after commit, consumer marks RUNNING and sends one Judge0 request per test case with a signed callback URL, callbacks verify the signature, store per-test-case results idempotently, and final verdict is calculated when all results arrive. |
| Alternative Flows | Unsupported language can fail in consumer path. Zero test cases are marked `INTERNAL_ERROR`. Problem and contest validation is enforced before queueing. |
| Code Evidence | `CodeEditor`, `SubmissionController`, `SubmissionService`, `SubmissionProducer`, `SubmissionConsumer`, `Judge0Service`, `Judge0CallbackService`. |

### UC-06 Rejudge Submission

| Field | Description |
|---|---|
| Status | Implemented backend and admin UI controls |
| Primary Actor | Administrator |
| Goal | Requeue previously final submissions for judging again. |
| Preconditions | Administrator is authenticated and target submissions/problem/contest exists. |
| Main Flow | Admin calls rejudge endpoint, service selects submissions, skips active ones, resets final submissions to PENDING_REJUDGE, clears aggregate metrics, publishes submission IDs after commit, consumer increments judgeRunId and reuses Judge0 flow. |
| Alternative Flows | Missing IDs are returned in response. Publish failures are logged after commit but not reflected in the response. |
| Code Evidence | `RejudgeController`, `RejudgeService`, `RejudgeResponse`, `SubmissionConsumer`. |

### UC-07 Submit and Answer Clarification

| Field | Description |
|---|---|
| Status | Implemented |
| Primary Actors | Team user, Administrator |
| Goal | Allow teams to ask contest questions and administrators to answer privately or publicly. |
| Preconditions | Contest is running for team submission. |
| Main Flow | Team submits clarification, backend validates contest and optional problem, clarification is stored PENDING, admin replies using standard or custom reply, backend marks ANSWERED and stores private/public reply type. |
| Alternative Flows | Non-running contest rejects submission. Problem outside contest is rejected. Team and admin screens refresh through clarification SSE. |
| Code Evidence | `ClarificationController`, `ClarificationService`, `Clarification`, `Clarifications.tsx`, `admin/App.tsx`. |

## 5.3 Activity Diagrams for Complicated Behaviors

[Insert Figure 6 here: Authentication and Session Lifecycle Activity Diagram]

Figure 6. Authentication and Session Lifecycle Activity Diagram

Purpose: To show administrator-protected team registration, login, token validation, refresh-token rotation, and logout.  
Description: The activity should include the registration method-security check, the normal login path, automatic refresh after an access token expires, and logout revocation using the shared `/auth` refresh-cookie path.  
Code Alignment: `AuthController`, `LoginService`, `RefreshTokenService`, `JwtAuthFilter`, `CookieUtil`, frontend `http.ts`.  
Current Status: Implemented.

[Insert Figure 7 here: Persisted State vs Effective State Diagram]

Figure 7. Persisted State vs Effective State Diagram

Purpose: To explain a central contest lifecycle design decision.  
Description: Persisted state is the database value. Effective state is what the contest should be now based on time, duration, actual start, pause history, and locks.  
Code Alignment: `Contest`, `ContestLifecycleService`, `ContestResponse`.  
Current Status: Implemented.

[Insert Figure 8 here: Exact-Time Scheduler and Fallback Sync Diagram]

Figure 8. Exact-Time Scheduler and Fallback Sync Diagram

Purpose: To show why two scheduler mechanisms exist.  
Description: Exact-time tasks reduce delay for expected transitions; fallback sync recovers missed transitions and startup gaps. Both delegate to row-locked sync logic.  
Code Alignment: `ContestTransitionScheduler`, `ContestStatusSyncScheduler`, `ContestStatusSyncService`, `ContestStatusSyncExecutor`.  
Current Status: Implemented.

[Insert Figure 9 here: SSE Connection and Contest Update Flow]

Figure 9. SSE Connection and Contest Update Flow

Purpose: To show real-time contest updates.  
Description: The backend sends an initial snapshot, later `contest-update` events, and named `ping` heartbeats. The frontend applies snapshots, places updates into contest buckets, falls back to REST if no snapshot arrives, and polls when SSE closes.
Code Alignment: `ContestStreamController`, `ContestSseAdapter`, `ContestSseRegistry`, `SseHeartbeatScheduler`, `useContestStream`, `ContestOverview`.
Current Status: Implemented.

[Insert Figure 10 here: Judge0 Callback and Per-Test-Case Result Flow]

Figure 10. Judge0 Callback and Per-Test-Case Result Flow

Purpose: To explain verdict correctness and stale callback protection.  
Description: The callback handler verifies the HMAC signature before state changes. The callback service locks the submission, rejects stale run IDs, stores terminal per-test-case results idempotently, waits for all expected tests, then calculates the final verdict.
Code Alignment: `CallbackHandler`, `Judge0CallbackSignatureService`, `Judge0CallbackService`, `SubmissionJudgeResult`, `SubmissionRepository.findByIdForUpdate`.
Current Status: Implemented.

[Insert Figure 11 here: Rejudge Workflow Diagram]

Figure 11. Rejudge Workflow Diagram

Purpose: To show how rejudge reuses the existing judging flow.  
Description: Rejudge resets eligible submissions, republishes them after commit, increments run ID during consumption, and relies on stale callback rejection.  
Code Alignment: `RejudgeController`, `RejudgeService`, `SubmissionConsumer`, `Judge0CallbackService`.  
Current Status: Implemented backend and admin frontend.

[Insert Figure 12 here: Clarification Workflow Diagram]

Figure 12. Clarification Workflow Diagram

Purpose: To explain the implemented clarification workflow.  
Description: The backend supports team questions, admin replies, public/private reply scope, and public answered clarification retrieval. The admin and team frontend screens call the backend and subscribe to clarification SSE updates.  
Code Alignment: `ClarificationController`, `ClarificationService`, `ClarificationRepository`, `Clarification`, `Clarifications.tsx`, `admin/App.tsx`.  
Current Status: Implemented.

# 6. Design Phase

## 6.1 Application Architecture Design / Context Diagram

AuraC2 follows a layered application design. The frontend presents role-specific user interfaces. The backend receives REST requests, validates security rules, performs business logic in services, persists data using repositories, and coordinates asynchronous judging and real-time updates.

The authentication design is stateless for access tokens. Protected requests use a JWT access token in the `Authorization` header. Team account registration is exposed through the administrator workflow and protected at both route level and backend method level. The JWT filter does not skip `/auth/register`, so an administrator bearer token can populate the security context before `@PreAuthorize("hasRole('ADMIN')")` is evaluated.

Refresh tokens are placed in an HTTP-only cookie and also persisted in the database by hash. The refresh cookie is scoped to `/auth`, allowing both `/auth/refresh` and `/auth/logout` to receive the same cookie. This allows the backend to revoke old refresh tokens, rotate refresh tokens, and clear the browser cookie consistently. In local development the cookie can remain non-secure, while production behavior uses the production flag to set the Secure attribute.

Current route authorization map:

| Endpoint group | Authorization behavior |
|---|---|
| `POST /auth/login`, `/auth/refresh`, `/auth/logout` | Public session endpoints; refresh/logout use the HTTP-only `/auth` cookie. |
| `POST /auth/register` | Administrator-only route and method security. |
| `GET /api/contest/active`, `/upcoming`, `/paused`, `/ended` | Public contest status reads. |
| Contest mutation routes and `/api/contest/stream` | Administrator-only. |
| Problem and test-case reads | `TEAM` or `ADMIN`. |
| Problem and test-case create/update/delete | `ADMIN`. |
| `/api/submissions/**` | `TEAM` or `ADMIN`; team detail access is owner-checked. |
| `/api/admin/**` | `ADMIN`, including users, rejudge, and admin scoreboard/reveal controls. |
| `/api/scoreboard/**` | Public scoreboard snapshot and stream. |
| Clarifications | Public answered route, TEAM submit/my routes, ADMIN admin/reply routes. |
| `/api/callback/judge0/**` | Externally reachable but HMAC-signature protected before state mutation. |

The `no-security` profile remains available only as a local/test escape hatch. Startup fails if `no-security` is active by itself or with a production profile.

The contest lifecycle design distinguishes persisted state from effective state. Persisted state is the database status. Effective state is the state the contest should have at the current time. Schedulers exist to reduce the delay between these two concepts. Exact-time scheduling attempts to transition at the precise start/end time, while the fallback scheduler periodically synchronizes eligible contests.

The judging design persists a submission before sending it for execution. This is important because it creates a durable record of the request before any external judge interaction. RabbitMQ publication for new submissions occurs after commit, so workers do not consume uncommitted submission IDs. Judge0 callbacks are signed and update the submission through a per-test-case ledger only after verification.

[Insert Figure 13 here: Contest Lifecycle Architecture Diagram]

Figure 13. Contest Lifecycle Architecture Diagram

Purpose: To show how contest lifecycle control, events, schedulers, and SSE updates interact.  
Description: The administrator calls contest endpoints. The backend saves state and publishes events. Schedulers reschedule or synchronize transitions. SSE broadcasts updates after committed changes.  
Code Alignment: `ContestController`, `ContestService`, `ContestUpdatedEvent`, `ContestTransitionScheduler`, `ContestStatusSyncService`, `ContestSseAdapter`, `SsePublisher`.
Current Status: Implemented.

[Insert Figure 14 here: Submission and Asynchronous Judging Architecture Diagram]

Figure 14. Submission and Asynchronous Judging Architecture Diagram

Purpose: To show asynchronous submission processing.  
Description: The team UI submits code, the backend saves a pending submission, RabbitMQ dispatches judging after commit, Judge0 evaluates each test case using a signed callback URL, and verified callbacks update per-test-case and aggregate results.
Code Alignment: `SubmissionService`, `SubmissionProducer`, `SubmissionConsumer`, `Judge0Service`, `Judge0CallbackSignatureService`, `CallbackHandler`, `Judge0CallbackService`.
Current Status: Implemented with judging limitations.

## 6.2 Data Architecture Design

### ER Diagram

[Insert Figure 15 here: Current ER Diagram]

Figure 15. Current ER Diagram

Purpose: To represent the current persistent entities.  
Description: The ER diagram should include `User`, `RefreshToken`, `Contest`, `Problem`, `TestCase`, `Clarification`, `Submission`, and `SubmissionJudgeResult`.  
Code Alignment: Entity classes under `authServer/entity`, `contestServer/entity`, and `submissionServer/entity`.  
Current Status: Implemented.

### Submission Judging Mini ER Diagram

[Insert Figure 16 here: Submission Judging Mini ER Diagram]

Figure 16. Submission Judging Mini ER Diagram

Purpose: To emphasize the current per-test-case judging design.  
Description: `SubmissionJudgeResult` stores one result per submission, judge run, and test-case number. This supports out-of-order callback handling and rejudge safety.  
Code Alignment: `Submission`, `SubmissionJudgeResult`, `SubmissionJudgeResultRepository`, `Judge0CallbackService`.  
Current Status: Implemented.

### Relational Schema / Logical Database Design

[Insert Figure 17 here: Relational Schema Diagram]

Figure 17. Relational Schema Diagram

Purpose: To show the logical database tables and foreign keys.  
Description: The schema should include table names, primary keys, foreign keys, enum fields, and the unique constraint on `submission_judge_results`.  
Code Alignment: JPA annotations in entity classes.  
Current Status: Implemented.

### Schema Notes

| Table | Notes |
|---|---|
| `users` | Stores administrators and teams. There is no separate team table. |
| `refresh_tokens` | Stores refresh-token metadata and hash, linked to users. |
| `contests` | Stores lifecycle fields including persisted status, actual start, pause time, freeze settings, and penalty minutes. |
| `problems` | Stores contest-bound problem metadata. |
| `test_cases` | Stores problem test cases and public/private visibility. |
| `clarifications` | Stores team questions, admin replies, reply scope, and status. |
| `submissions` | Stores code, language, verdict, timing/memory aggregate values, and judge run. |
| `submission_judge_results` | Stores per-test-case results for each judge run. |

### Gap Analysis: Current System vs Intended Design

| Area | Current system | Gap |
|---|---|---|
| Authentication | Login, admin-protected team registration, refresh rotation, logout revocation, role authorization, production-aware refresh-cookie settings, and admin bootstrap. | Admin bootstrap password rotation remains operationally sensitive and should be reviewed. |
| Contest lifecycle | Strong implementation with effective state, pause-aware time, schedulers, SSE. | No UI for status lock management discovered. |
| Problems/test cases | Create, read, update, and delete exist. | Limits are stored but not enforced by Judge0 request. |
| Judging | Queue, Judge0, signed callbacks, per-case results, live verdict push, and rejudge backend/UI exist. | Unsupported language still needs stronger submission-time validation. |
| Clarifications | Backend and admin/team frontend workflow exist. | Remaining gap is workflow polish and operational policy around public/private replies. |
| Scoreboard | Ranking, freeze, reveal, public/admin snapshots, and streams exist. | Formal export/reporting is not implemented. |
| Monitoring | Device IP stored on refresh token. | No security monitoring subsystem. |
| Participation | Teams represented as users. | No contest membership/join workflow. |

# 7. Implementation Phase

## 7.1 Language Used

| Layer | Languages |
|---|---|
| Backend | Java 21 |
| Frontend | TypeScript, JavaScript, JSX/TSX |
| Styling | CSS and component styles |
| Configuration | YAML, Docker Compose YAML |
| Database | PostgreSQL through JPA-generated schema |

## 7.2 Tools Used

| Tool | Use |
|---|---|
| Spring Boot | Backend framework |
| Spring Security | Authentication and authorization |
| Spring Data JPA | Repository and persistence abstraction |
| PostgreSQL | Database |
| RabbitMQ | Asynchronous submission queue |
| Judge0 | External code execution and judging |
| React | Frontend UI |
| Vite | Frontend build tool |
| Docker Compose | Local deployment support |
| Lombok | Java boilerplate reduction |
| Swagger/OpenAPI | API documentation support |

## 7.3 Templates / UI Structure

The UI is divided into authentication, admin, and team areas. The root `App.tsx` loads an existing access token or attempts refresh. After authentication, it decodes the JWT role and routes administrators to the admin dashboard and teams to the team workspace.

The admin interface contains:

| Screen | Status | Description |
|---|---|---|
| Contest Overview | Implemented | Contest buckets, lifecycle controls, SSE status, creation modal, freeze badge. |
| Teams | Implemented | User list, team registration, username/password update, delete non-admin. |
| Problems | Implemented | Problem list, details, creation/update/delete actions, and test-case panel. |
| Submissions | Implemented | Submission table, search/filter, code viewing. |
| Rejudge | Implemented | Admin rejudge workflows for problem and contest scopes. |
| Clarifications | Implemented | Admin clarification review, reply, filtering, and SSE refresh. |
| Security Monitor | Placeholder | No backend monitoring endpoints. |
| Quick Statistics | Placeholder | Static values; no aggregate endpoint. |

The team interface contains:

| Screen/Component | Status | Description |
|---|---|---|
| Header | Implemented | Active contest title, timer, team name, logout. |
| Problem Sidebar | Implemented | Problem list with solved/wrong/pending/unsolved status from submissions. |
| Code Editor | Implemented | Language selector, starter code, local draft persistence, submit button. |
| Submission History | Implemented | Shows team submissions for selected problem and allows viewing submitted code. |
| Clarifications | Implemented | Submit questions, view own/public answers, and receive SSE refresh. |

## 7.4 Screenshots

[Insert Screenshot here: Login Page]

[Insert Screenshot here: Admin Contest Overview]

[Insert Screenshot here: Admin Teams Page]

[Insert Screenshot here: Admin Problems and Test Cases Page]

[Insert Screenshot here: Admin Submissions Page]

[Insert Screenshot here: Team Contest Workspace]

[Insert Screenshot here: Team Submission History]

[Insert Screenshot here: Clarifications and Security Monitor Pages]

## 7.5 Scenarios

### Scenario 1: Administrator Creates and Starts a Contest

1. Administrator logs in.
2. Administrator opens Contest Overview.
3. Administrator creates a contest with title, description, start time, duration, freeze setting, and penalty.
4. Backend validates that no conflicting effective contest exists.
5. Contest is stored as UPCOMING.
6. `ContestUpdatedEvent` is published.
7. SSE clients receive contest update.
8. Administrator starts the contest manually, or the scheduler auto-starts it at scheduled time.

### Scenario 2: Team Submits Code

1. Team logs in.
2. Team workspace loads active contest.
3. Frontend loads contest problems.
4. Team selects a problem and writes code.
5. Frontend posts submission request.
6. Backend stores PENDING submission and publishes submission ID.
7. RabbitMQ consumer dispatches Judge0 requests.
8. Judge0 callbacks are received and stored per test case.
9. Final verdict is calculated and persisted.
10. Team can view the submission in history.

### Scenario 3: Administrator Rejudges Submissions

1. Administrator calls a rejudge endpoint for selected submissions, a problem, or a contest.
2. Backend validates the target scope.
3. Active submissions are skipped.
4. Final submissions are reset to PENDING_REJUDGE.
5. Submission IDs are republished after transaction commit.
6. Consumer increments `judgeRunId` and reuses the normal Judge0 flow.
7. Stale callbacks from older runs are ignored.

### Scenario 4: Team Clarification Workflow

Current backend behavior:

1. Team submits a clarification during a running contest.
2. Backend validates contest state and optional problem ownership.
3. Clarification is stored as PENDING.
4. Administrator replies with standard or custom text.
5. Reply can be public or private.
6. Public answered clarifications are visible through the public endpoint.

Current frontend behavior:

The team clarification screen submits questions and reads own/public answers from the backend. The admin clarification screen reviews pending questions, sends public or private replies, and refreshes through clarification SSE.

## 7.6 Sample Reports

[Insert Sample Report here: Contest Submission Summary]

Current status: A formal report export feature is not implemented. The admin submissions table can be used as the current manual review view. A future sample report could summarize submissions by contest, problem, team, verdict, execution time, and memory.

# 8. References

[Insert formal references in Phase 2. Suggested topics: PC2, DOMjudge, Codeforces, Judge0 API, Spring Boot, Spring Security, RabbitMQ, PostgreSQL, React, Server-Sent Events.]

# Appendix A: Code

[Insert Appendix Code Reference here: Authentication]

Suggested files:

| Area | Code references |
|---|---|
| Authentication | `AuthController`, `LoginService`, `RefreshTokenService`, `JwtAuthFilter`, `SecurityConfiguration` |
| Contest lifecycle | `Contest`, `ContestService`, `ContestLifecycleService`, `ContestTransitionScheduler`, `ContestStatusSyncExecutor` |
| SSE | `ContestStreamController`, `ContestSseAdapter`, `ContestSseRegistry`, `SseHeartbeatScheduler`, `useContestStream`, `ContestOverview` |
| Judging | `SubmissionService`, `SubmissionProducer`, `SubmissionConsumer`, `Judge0Service`, `CallbackHandler`, `Judge0CallbackService` |
| Rejudge | `RejudgeController`, `RejudgeService` |
| Clarifications | `Clarification`, `ClarificationController`, `ClarificationService` |
| Frontend admin | `admin/App.tsx`, `ContestOverview`, `TeamsView`, `ProblemsView`, `SubmissionsView` |
| Frontend team | `team/App.tsx`, `CodeEditor`, `SubmissionHistory`, `ProblemSidebar` |

# Appendix B: Screenshots of the System Screens

[Insert screenshots after running the current local system.]

Recommended screenshots:

1. Login page.
2. Admin contest overview with contest lifecycle controls.
3. Contest creation modal.
4. Problem management screen.
5. Test-case management panel.
6. Teams management screen.
7. Admin submissions screen.
8. Team contest workspace.
9. Team submission history.
10. Clarification workflow screens and any remaining placeholder screens such as security monitoring, if included to document limitations.

# Appendix C: CD / Deployment Package

The project includes Docker-related deployment files. The current deployment package should include the backend, frontend, PostgreSQL, RabbitMQ, configuration examples, and any required environment variables. The current Compose file provisions PostgreSQL, RabbitMQ, backend, and frontend containers. Judge0 must be configured explicitly if the final deployment is expected to be fully LAN-first or offline.

Deployment-related files:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Defines PostgreSQL, RabbitMQ, backend, and frontend services. |
| `backend/Dockerfile` | Builds backend container. |
| `UI/Dockerfile` | Builds frontend container. |
| `.env.example` | Documents expected environment variables. |
| `backend/src/main/resources/application.yml` | Backend runtime configuration defaults. |

Final submission package should include:

1. Source code.
2. Final report DOCX.
3. Screenshots.
4. Deployment instructions.
5. Environment variable template.
6. Any generated diagrams.
