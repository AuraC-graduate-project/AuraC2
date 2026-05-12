# TheTask1 Full Force Rejudge Documentation

## 1. Purpose Of This Document

This document explains the full task written in `thetask1` and the current
implementation around it. It covers:

- What the task asked for.
- What normal rejudge does.
- What force rejudge does.
- Which backend and frontend files are involved.
- How RabbitMQ, Judge0 callbacks, `judgeRunId`, and per-test-case aggregation work together.
- Whether the feature uses SSE.
- What safety protections exist.
- What tests cover the feature.
- What limitations remain.
- How to test the feature manually.

The most important answer first:

```text
Force rejudge is not an SSE feature.
It is a normal HTTP admin API feature that uses RabbitMQ and Judge0 callbacks.
SSE exists elsewhere in the project for contest lifecycle and clarifications.
```

## 2. Project Context

AuraC2 is a Spring Boot and React contest management system. The project has:

- JWT authentication.
- Admin and team roles.
- Contest lifecycle management.
- Problem management.
- Test case management.
- Code submissions.
- RabbitMQ based async judging.
- Judge0 external execution.
- Judge0 callback handling.
- Clarifications.
- SSE live updates for contest and clarification streams.

The force rejudge task belongs to the submission and judging subsystem.

## 3. Original Task Summary

The file `thetask1` asks for a complete and safe force rejudge feature.

Before this task, the system already had normal rejudge endpoints:

```http
POST /api/admin/rejudge/submissions
POST /api/admin/rejudge/problem/{problemId}
POST /api/admin/rejudge/contests/{contestId}
```

Normal rejudge skips active submissions:

```text
PENDING
PENDING_REJUDGE
RUNNING
```

Normal rejudge only requeues submissions that already reached a final verdict,
for example:

```text
ACCEPTED
WRONG_ANSWER
TLE
COMPILATION_ERROR
RUNTIME_ERROR
INTERNAL_ERROR
```

The requested new behavior was force rejudge:

```text
Force rejudge must include active submissions too.
```

That means force rejudge can requeue:

```text
PENDING
PENDING_REJUDGE
RUNNING
ACCEPTED
WRONG_ANSWER
TLE
COMPILATION_ERROR
RUNTIME_ERROR
INTERNAL_ERROR
```

The critical safety requirement was:

```text
Old Judge0 callbacks must not overwrite the new result.
```

The implementation uses `judgeRunId` to make old callbacks stale.

## 4. High Level Architecture

The rejudge pipeline uses the existing normal judging pipeline:

```text
Admin UI
  -> HTTP POST /api/admin/rejudge/...
  -> RejudgeController
  -> RejudgeService
  -> database transaction updates Submission rows
  -> after commit, SubmissionProducer publishes IDs to RabbitMQ
  -> RabbitMQ submissionQueue
  -> SubmissionConsumer
  -> Judge0Service
  -> Judge0
  -> CallbackHandler
  -> Judge0CallbackService
  -> SubmissionJudgeResult table
  -> final Submission verdict update
```

This is asynchronous. The admin HTTP request does not wait for Judge0 to finish.
It only prepares and queues submissions.

## 5. Is It SSE Or Not?

Force rejudge is not SSE.

### What Force Rejudge Uses

Force rejudge uses:

- HTTP POST endpoints for admin actions.
- Spring Security for admin authorization.
- JPA transactions for database state changes.
- RabbitMQ for async queueing.
- Judge0 for execution.
- HTTP callback routes from Judge0 back into the backend.
- `judgeRunId` to reject stale callbacks.
- `SubmissionJudgeResult` records for per-test-case aggregation.

### What SSE Is Used For In This Project

SSE is used for live frontend streams in other areas:

```http
GET /api/contest/stream
GET /api/team/stream
GET /api/clarifications/my/stream/{contestId}
GET /api/clarifications/admin/stream/{contestId}
```

Those endpoints produce:

```java
MediaType.TEXT_EVENT_STREAM_VALUE
```

Frontend hooks use:

```ts
@microsoft/fetch-event-source
```

SSE files include:

- `backend/src/main/java/com/server/contestControl/contestServer/controller/ContestStreamController.java`
- `backend/src/main/java/com/server/contestControl/contestServer/controller/TeamStreamController.java`
- `backend/src/main/java/com/server/contestControl/contestServer/controller/ClarificationStreamController.java`
- `backend/src/main/java/com/server/contestControl/shared/sse/SseEmitterRegistry.java`
- `backend/src/main/java/com/server/contestControl/shared/sse/SsePublisher.java`
- `backend/src/main/java/com/server/contestControl/shared/sse/SseHeartbeatScheduler.java`
- `UI/src/hooks/useContestStream.ts`
- `UI/src/hooks/useClarificationStream.ts`

There is currently no dedicated SSE event for rejudge progress or submission
verdict updates.

## 6. Normal Rejudge Versus Force Rejudge

### Normal Rejudge

Normal rejudge is conservative. It avoids touching submissions that are already
being judged.

It skips:

```text
PENDING
PENDING_REJUDGE
RUNNING
```

It queues only final verdict submissions.

For each queued submission it:

1. Calculates the next `judgeRunId`.
2. Sets `verdict = PENDING_REJUDGE`.
3. Clears `executionTime`.
4. Clears `memoryUsage`.
5. Saves the submission.
6. Publishes the submission ID to RabbitMQ after commit.

### Force Rejudge

Force rejudge is stronger. It includes active submissions.

It does not skip:

```text
PENDING
PENDING_REJUDGE
RUNNING
```

For each found submission it:

1. Calculates the next `judgeRunId`.
2. Immediately stores that new `judgeRunId` in the database.
3. Sets `verdict = PENDING_REJUDGE`.
4. Clears `executionTime`.
5. Clears `memoryUsage`.
6. Saves the submission.
7. Publishes the submission ID to RabbitMQ after commit.

The key difference is not just that force rejudge queues active submissions.
The key difference is that it immediately invalidates the old run.

## 7. Endpoints

All rejudge endpoints live under:

```http
/api/admin/rejudge
```

All require:

```java
@PreAuthorize("hasRole('ADMIN')")
```

The route-level security also protects:

```text
/api/admin/**
```

### 7.1 Normal Selected Submissions

```http
POST /api/admin/rejudge/submissions
Content-Type: application/json
Authorization: Bearer <admin-access-token>
```

Body:

```json
{
  "submissionIds": [1, 2, 3]
}
```

Behavior:

- Normalizes IDs.
- Ignores null, zero, and negative IDs.
- De-duplicates IDs while preserving order.
- Rejects the request if no positive IDs remain.
- Fetches found submissions.
- Queues only final verdict submissions.
- Returns missing IDs.

### 7.2 Normal Problem Rejudge

```http
POST /api/admin/rejudge/problem/{problemId}
Authorization: Bearer <admin-access-token>
```

Behavior:

- Validates that the problem exists.
- Loads all submissions for the problem.
- Queues only final verdict submissions.
- Skips active submissions.

### 7.3 Normal Contest Rejudge

```http
POST /api/admin/rejudge/contests/{contestId}
Authorization: Bearer <admin-access-token>
```

Behavior:

- Validates that the contest exists.
- Loads all submissions in the contest.
- Queues only final verdict submissions.
- Skips active submissions.

There is no global all-system rejudge endpoint. Contest scope is required for
large/bulk rejudge.

### 7.4 Force Selected Submissions

```http
POST /api/admin/rejudge/force/submissions
Content-Type: application/json
Authorization: Bearer <admin-access-token>
```

Body:

```json
{
  "submissionIds": [1, 2, 3]
}
```

Behavior:

- Normalizes IDs.
- Ignores null, zero, and negative IDs.
- De-duplicates IDs while preserving order.
- Rejects the request if no positive IDs remain.
- Fetches found submissions.
- Queues every found submission, including active submissions.
- Returns missing IDs.

### 7.5 Force Problem Rejudge

```http
POST /api/admin/rejudge/force/problem/{problemId}
Authorization: Bearer <admin-access-token>
```

Behavior:

- Validates that the problem exists.
- Loads all submissions for the problem.
- Queues every found submission, including active submissions.

### 7.6 Force Contest Rejudge

```http
POST /api/admin/rejudge/force/contests/{contestId}
Authorization: Bearer <admin-access-token>
```

Behavior:

- Validates that the contest exists.
- Loads all submissions in the contest.
- Queues every found submission, including active submissions.

## 8. Response Format

The backend uses:

```java
RejudgeResponse
```

Shape:

```json
{
  "scope": "FORCE_SUBMISSIONS",
  "scopeId": null,
  "requestedCount": 3,
  "foundCount": 2,
  "queuedCount": 2,
  "skippedCount": 0,
  "queuedSubmissionIds": [1, 2],
  "skippedSubmissionIds": [],
  "missingSubmissionIds": [3]
}
```

Fields:

- `scope`: what kind of rejudge was requested.
- `scopeId`: problem ID or contest ID when applicable.
- `requestedCount`: number of normalized requested IDs or scope submissions.
- `foundCount`: number of submissions found in the database.
- `queuedCount`: number of submissions prepared for queueing.
- `skippedCount`: number skipped by normal rejudge because they were active.
- `queuedSubmissionIds`: IDs prepared and published after commit.
- `skippedSubmissionIds`: active IDs skipped by normal rejudge.
- `missingSubmissionIds`: requested selected IDs not found in the database.

Possible scopes:

```text
SUBMISSIONS
PROBLEM
CONTEST
FORCE_SUBMISSIONS
FORCE_PROBLEM
FORCE_CONTEST
```

## 9. Backend Files

### 9.1 RejudgeController

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/controller/RejudgeController.java
```

Responsibility:

- Defines normal and force rejudge REST endpoints.
- Delegates all logic to `RejudgeService`.
- Requires `ADMIN` role.

Important routes:

```java
@PostMapping("/submissions")
@PostMapping("/problem/{problemId}")
@PostMapping("/contests/{contestId}")
@PostMapping("/force/submissions")
@PostMapping("/force/problem/{problemId}")
@PostMapping("/force/contests/{contestId}")
```

### 9.2 RejudgeService

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/service/rejudge/RejudgeService.java
```

Responsibility:

- Normalizes selected submission IDs.
- Validates problem and contest existence.
- Loads submissions by selected IDs, problem ID, or contest ID.
- Applies normal versus force filtering.
- Reserves `judgeRunId` for rejudge runs.
- Marks submissions `PENDING_REJUDGE`.
- Clears previous execution metrics.
- Saves DB changes in a transaction.
- Publishes queued IDs after transaction commit.

Active verdict set:

```java
PENDING
PENDING_REJUDGE
RUNNING
```

Normal rejudge skips that set.

Force rejudge does not skip that set.

Core state update:

```java
Long currentRunId = submission.getJudgeRunId();
Long nextJudgeRunId = (currentRunId == null) ? 1L : currentRunId + 1;
submission.setJudgeRunId(nextJudgeRunId);
submission.setVerdict(Verdict.PENDING_REJUDGE);
submission.setExecutionTime(null);
submission.setMemoryUsage(null);
```

Why publishing happens after commit:

- The consumer must not receive the message before the new DB state is visible.
- If RabbitMQ dispatch happened before commit, the consumer might read old state.
- For force rejudge, the old run must become stale as soon as the DB commit succeeds.

### 9.3 RejudgeResponse

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/dto/RejudgeResponse.java
```

Responsibility:

- Defines the API response returned by all rejudge endpoints.

### 9.4 RejudgeSubmissionsRequest

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/dto/RejudgeSubmissionsRequest.java
```

Responsibility:

- Defines the request body for selected-submission rejudge.

Shape:

```java
public record RejudgeSubmissionsRequest(List<Long> submissionIds) {}
```

### 9.5 InvalidRejudgeRequestException

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/exceptions/InvalidRejudgeRequestException.java
```

Responsibility:

- Returns a typed `400 BAD_REQUEST` for invalid selected rejudge requests.

Used when:

- `submissionIds` is null.
- `submissionIds` is empty after normalization.

### 9.6 Submission

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/entity/Submission.java
```

Important fields:

```java
private Verdict verdict;
private Integer executionTime;
private Integer memoryUsage;
private Long judgeRunId;
```

`judgeRunId` identifies the current judging attempt.

`@PrePersist` initializes:

```java
verdict = PENDING
judgeRunId = 0L
```

### 9.7 Verdict

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/enums/Verdict.java
```

Verdicts:

```text
ACCEPTED
WRONG_ANSWER
TLE
COMPILATION_ERROR
RUNTIME_ERROR
INTERNAL_ERROR
PENDING
PENDING_REJUDGE
RUNNING
```

`PENDING_REJUDGE` means the submission is intentionally waiting for a rejudge run.

### 9.8 SubmissionRepository

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/repository/SubmissionRepository.java
```

Important methods:

```java
findAllByProblem_Id(Long problemId)
findAllByContest_Id(Long contestId)
findByIdForUpdate(Long id)
```

`findByIdForUpdate` uses pessimistic locking for callback safety.

### 9.9 SubmissionJudgeResult

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/entity/SubmissionJudgeResult.java
```

Responsibility:

- Stores one test-case callback result for one submission run.

Important fields:

```java
submission
judgeRunId
testCaseNumber
verdict
executionTime
memoryUsage
receivedAt
```

Unique logical key:

```text
submission_id + judge_run_id + test_case_number
```

This prevents duplicate callbacks from creating duplicate logical results.
Duplicates update the same logical result.

### 9.10 SubmissionJudgeResultRepository

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/repository/SubmissionJudgeResultRepository.java
```

Important methods:

```java
findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(...)
countBySubmission_IdAndJudgeRunId(...)
findBySubmission_IdAndJudgeRunId(...)
deleteAllBySubmissionIds(...)
```

The callback service uses these methods to:

- Upsert a test-case result.
- Count how many expected test cases have arrived.
- Load all results for final verdict calculation.

### 9.11 SubmissionConsumer

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/queue/submission/SubmissionConsumer.java
```

Responsibility:

- Consumes submission IDs from RabbitMQ.
- Loads submission from DB.
- Skips queue messages if verdict is not queueable.
- Sends each test case to Judge0.

Queueable verdicts:

```text
PENDING
PENDING_REJUDGE
```

Behavior:

- If `PENDING`, it is a normal new submission, so the consumer increments `judgeRunId`.
- If `PENDING_REJUDGE`, it is a rejudge submission, so the consumer does not increment `judgeRunId`.
- In both cases, it sets `RUNNING` before dispatching to Judge0.

This distinction is critical.

If the consumer incremented `PENDING_REJUDGE`, the callback URL would use a
different run ID than the one reserved by `RejudgeService`. That would break
force rejudge safety.

### 9.12 SubmissionProducer

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/queue/submission/SubmissionProducer.java
```

Responsibility:

- Sends a submission ID to RabbitMQ.

### 9.13 RabbitMQConfig

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/config/RabbitMQConfig.java
```

Important queue:

```text
submissionQueue
```

Important exchange and routing key:

```text
submissionExchange
submissionRoutingKey
```

### 9.14 Judge0Service

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/service/judge/Judge0Service.java
```

Responsibility:

- Sends one Judge0 request per test case.
- Includes source code, language ID, input, expected output, and callback URL.

Callback URL shape:

```text
{callbackUrl}/{submissionId}/{judgeRunId}/{testCaseNumber}
```

Example:

```text
/api/callback/judge0/25/6/1
```

### 9.15 CallbackHandler

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/service/callback/CallbackHandler.java
```

Responsibility:

- Receives Judge0 callback requests.
- Delegates to `Judge0CallbackService`.

Current callback route:

```http
PUT /api/callback/judge0/{submissionId}/{judgeRunId}/{testCaseNumber}
```

Legacy route:

```http
PUT /api/callback/judge0/{submissionId}/{testCaseNumber}
```

The legacy route has no `judgeRunId`.

### 9.16 Judge0CallbackService

Path:

```text
backend/src/main/java/com/server/contestControl/submissionServer/service/callback/Judge0CallbackService.java
```

Responsibility:

- Locks the submission row.
- Rejects stale callbacks.
- Validates test case numbers.
- Maps Judge0 status to internal verdict.
- Ignores non-terminal callbacks.
- Stores or updates per-test-case result.
- Waits until all expected test case results are present.
- Calculates final aggregate verdict.
- Saves final verdict, execution time, and memory usage.

Stale callback rule:

```text
If callback judgeRunId does not equal current submission judgeRunId,
the callback is stale and ignored.
```

Legacy callback rule:

```text
A callback without judgeRunId is accepted only when the current submission
judgeRunId is null or 0.
```

This protects new rejudge-aware submissions while keeping old callback routes
compatible for old submissions.

## 10. Force Rejudge Safety In Detail

### 10.1 The Problem

Judge0 execution is external and asynchronous. If a submission is already
running, Judge0 may still call the backend after an admin starts a rejudge.

Without protection, an old callback could overwrite the result of the new run.

### 10.2 The Solution

Each judging attempt has a run identifier:

```text
Submission.judgeRunId
```

Every Judge0 callback URL includes the run identifier:

```text
/api/callback/judge0/{submissionId}/{judgeRunId}/{testCaseNumber}
```

Force rejudge immediately increments the submission's `judgeRunId` in the
database.

### 10.3 Example

Initial state:

```text
submissionId = 10
verdict = RUNNING
judgeRunId = 5
```

Admin calls:

```http
POST /api/admin/rejudge/force/submissions
```

Service saves:

```text
verdict = PENDING_REJUDGE
judgeRunId = 6
executionTime = null
memoryUsage = null
```

Old Judge0 callback arrives:

```text
submissionId = 10
callback judgeRunId = 5
testCaseNumber = 1
```

Backend loads current submission:

```text
current judgeRunId = 6
```

Comparison:

```text
5 != 6
```

Result:

```text
Old callback ignored.
No judge result stored.
Submission verdict not overwritten.
```

New RabbitMQ run starts:

```text
SubmissionConsumer sees PENDING_REJUDGE
judgeRunId stays 6
verdict becomes RUNNING
Judge0 callback URL uses run 6
```

New callback arrives:

```text
callback judgeRunId = 6
current judgeRunId = 6
```

Result:

```text
Callback accepted.
Per-test-case result stored.
Final verdict calculated after all expected callbacks arrive.
```

## 11. Per-Test-Case Aggregation

Judge0 sends one callback per test case.

Callbacks can arrive:

- Out of order.
- Close together.
- More than once.
- After an old run has already been invalidated.

The backend does not finalize based on callback order. Instead:

1. It stores each terminal test-case callback in `submission_judge_results`.
2. It counts stored results for the current `submissionId` and `judgeRunId`.
3. If count is less than expected test case count, the submission stays `RUNNING`.
4. When all expected results exist, it loads them and calculates final verdict.

Final verdict rule:

```text
First non-ACCEPTED result by testCaseNumber wins.
If all results are ACCEPTED, final verdict is ACCEPTED.
```

Execution time:

```text
Maximum executionTime from the test-case results.
```

Memory usage:

```text
Maximum memoryUsage from the test-case results.
```

## 12. Frontend Files

### 12.1 API Client

Path:

```text
UI/src/admin/services/api.ts
```

Normal methods:

```ts
rejudgeSubmissions(submissionIds)
rejudgeProblem(problemId)
rejudgeContest(contestId)
```

Force methods:

```ts
forceRejudgeSubmissions(submissionIds)
forceRejudgeProblem(problemId)
forceRejudgeContest(contestId)
```

All use normal HTTP fetch through the shared API client.

### 12.2 API Types

Path:

```text
UI/src/admin/types/api.ts
```

Includes:

```ts
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
```

### 12.3 SubmissionsView

Path:

```text
UI/src/admin/components/SubmissionsView.tsx
```

Responsibility:

- Shows all submissions.
- Lets admin select submissions with checkboxes.
- Shows normal `Rejudge` button when selections exist.
- Shows destructive `Force Rejudge` button when selections exist.
- Requires confirmation before force rejudge.
- Shows success toast with queued/skipped/missing counts.
- Reloads submission table after success.

Force confirmation explains:

- It includes pending, running, and pending rejudge submissions.
- It logically cancels in-progress judging by advancing run ID.
- Old Judge0 callbacks are discarded.
- External Judge0 jobs are not physically stopped.

### 12.4 RejudgeView

Path:

```text
UI/src/admin/components/RejudgeView.tsx
```

Responsibility:

- Dedicated admin screen for problem and contest rejudge.
- Lets admin enter a problem ID.
- Lets admin enter a contest ID.
- Supports normal and force rejudge for both scopes.
- Requires confirmation for force problem/contest rejudge.
- Uses toasts for success and errors.

## 13. Validation Rules

Selected submission endpoints:

- Reject `null` request list.
- Ignore null IDs.
- Ignore zero IDs.
- Ignore negative IDs.
- De-duplicate IDs.
- Preserve the original positive ID order.
- Reject if no positive IDs remain.
- Return missing positive IDs.

Problem endpoints:

- Validate the problem exists.
- Throw `ProblemNotFoundException` if missing.

Contest endpoints:

- Validate the contest exists.
- Throw `ContestNotFoundException` if missing.

## 14. Security

Rejudge endpoints require admin authorization at two levels:

1. Controller annotation:

```java
@PreAuthorize("hasRole('ADMIN')")
```

2. Security route matcher:

```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
```

Judge0 callback endpoints are public:

```text
/api/callback/judge0/**
```

This is necessary because Judge0 must call the backend from outside the logged-in
browser session.

The callback route is protected logically by `submissionId`, `judgeRunId`,
test-case validation, and stale callback rejection.

## 15. Transaction And Queue Timing

Rejudge state changes happen in a transaction.

RabbitMQ publishing is registered after commit:

```java
TransactionSynchronizationManager.registerSynchronization(...)
afterCommit()
```

Why this matters:

- The DB must commit `PENDING_REJUDGE` before the queue consumer runs.
- The DB must commit the new `judgeRunId` before old callbacks can be judged stale.
- Queue consumers should always see committed state.

If no transaction synchronization is active, the publisher runs immediately.

## 16. Duplicate Queue Messages

`SubmissionConsumer` skips messages when the submission verdict is not:

```text
PENDING
PENDING_REJUDGE
```

This makes stale or duplicate queue messages safer.

Example:

```text
Queue message arrives for submission 20.
Submission is already ACCEPTED.
Consumer skips it.
No Judge0 request is sent.
```

## 17. Callback Rules

### Accepted Callback

A callback is accepted when:

- Submission exists.
- Callback run ID matches current `Submission.judgeRunId`.
- Test case number is in range.
- Judge0 status maps to a terminal verdict.

### Stale Callback

A callback is stale when:

- It has a `judgeRunId`, and that ID does not match current `Submission.judgeRunId`.
- Or it is a legacy callback without `judgeRunId`, but current submission run is not legacy run 0.

### Non-Terminal Callback

Judge0 statuses that map to non-terminal internal states are ignored until a
terminal result arrives.

Terminal internal verdicts include:

```text
ACCEPTED
WRONG_ANSWER
TLE
COMPILATION_ERROR
RUNTIME_ERROR
INTERNAL_ERROR
```

Non-terminal internal verdicts:

```text
PENDING
PENDING_REJUDGE
RUNNING
```

## 18. Test Coverage

### 18.1 RejudgeService Tests

Path:

```text
backend/src/test/java/com/server/contestControl/submissionServer/service/rejudge/RejudgeServiceTest.java
```

Important tests:

```text
rejudgeSelectedSubmissionsQueuesOnlyFinalVerdicts
rejudgeSelectedSubmissionsRejectsEmptyRequest
rejudgeProblemUsesProblemScope
rejudgeContestRequiresContestScope
normalRejudgeStillSkipsActiveSubmissions
forceRejudgeQueuesActiveAndFinalSubmissions
forceRejudgeAdvancesJudgeRunIdImmediately
```

What they prove:

- Normal rejudge skips active submissions.
- Force rejudge includes active and final submissions.
- Force rejudge advances `judgeRunId`.
- Execution metrics are cleared.
- Missing selected IDs are returned.
- Empty selected requests fail.

### 18.2 SubmissionConsumer Tests

Path:

```text
backend/src/test/java/com/server/contestControl/submissionServer/queue/submission/SubmissionConsumerTest.java
```

Important tests:

```text
consumerDoesNotDoubleIncrementPendingRejudge
consumerStillIncrementsNormalPendingSubmission
consumerSkipsStaleOrDuplicateMessages
```

What they prove:

- Rejudge runs keep the run ID reserved by `RejudgeService`.
- Normal new submissions still increment the run ID.
- Non-queueable verdicts are skipped.

### 18.3 Judge0CallbackService Tests

Path:

```text
backend/src/test/java/com/server/contestControl/submissionServer/service/callback/Judge0CallbackServiceTest.java
```

Important tests:

```text
callbackFinalizesOnlyAfterAllTestCasesArrive
finalVerdictUsesFirstFailingTestCaseByTestCaseNumber
staleCallbackFromOlderRunIsIgnored
staleCallbackAfterForceRejudgeIsIgnored
currentCallbackAfterForceRejudgeIsAccepted
legacyCallbackWithoutJudgeRunIdIsAcceptedOnlyForLegacyRunZero
legacyCallbackWithoutJudgeRunIdIsIgnoredForNonLegacyRuns
```

What they prove:

- The backend waits for all callbacks.
- Final verdict uses test-case order, not callback arrival order.
- Old callbacks are ignored.
- Current force rejudge callbacks are accepted.
- Legacy callback support is limited to legacy run zero.

## 19. Documentation Files Already Related

Existing related docs:

```text
README.md
docs/rejudge-feature-documentation.md
docs/sse-architecture.md
docs/team_landing_sse_logic.md
```

`docs/rejudge-feature-documentation.md` already contains a large rejudge
technical document. This file is a focused full explanation of the `thetask1`
task and how the current implementation satisfies it.

## 20. Known Limitations

### 20.1 Judge0 Jobs Are Not Physically Cancelled

Force rejudge does not stop already-running Judge0 jobs.

It only invalidates their callbacks by advancing `judgeRunId`.

Old external execution may still consume Judge0 resources until it ends.

### 20.2 RabbitMQ Publish Failure After Commit

If DB commit succeeds but RabbitMQ publish fails, the submission may remain:

```text
PENDING_REJUDGE
```

The error is logged, but there is no durable rejudge job table to retry later.

A future improvement would be an outbox table or rejudge job table.

### 20.3 Old Judge Result Rows Are Not Cleaned Up

Old rows in:

```text
submission_judge_results
```

remain in the database.

They do not break correctness because current runs are separated by
`judgeRunId`, but they can grow over time.

### 20.4 No Rejudge Progress Tracking

The API response tells how many submissions were prepared for queueing.

It does not provide:

- Background job ID.
- Progress percentage.
- Completed count.
- Failed count after Judge0 execution.
- Live stream of rejudge progress.

### 20.5 No Rejudge SSE Events

The project has SSE infrastructure, but force rejudge does not currently emit
SSE events for submission verdict changes.

Admin must refresh or rely on whatever submission loading behavior the UI has.

### 20.6 No Batch Pagination For Huge Scopes

Problem and contest force rejudge load all matching submissions into memory.

For very large contests, a paged batch implementation would be safer.

### 20.7 Scoreboard Recalculation May Be Future Work

If scoreboard/ranking is implemented later, rejudge should trigger or integrate
with scoreboard recalculation.

## 21. Manual Testing

Replace:

```text
<TOKEN>
```

with an admin access token.

### 21.1 Normal Selected Rejudge

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/submissions" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d "{\"submissionIds\":[1,2,3]}"
```

Expected:

- Final verdict submissions are queued.
- Active submissions are skipped.
- Response includes queued, skipped, and missing IDs.

### 21.2 Force Selected Rejudge

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/force/submissions" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d "{\"submissionIds\":[1,2,3]}"
```

Expected:

- All found submissions are queued.
- `skippedCount` should be 0 unless future logic adds another skip reason.
- Active submissions become `PENDING_REJUDGE`.
- `judgeRunId` advances immediately.

### 21.3 Normal Problem Rejudge

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/problem/10" \
  -H "Authorization: Bearer <TOKEN>"
```

Expected:

- Only final submissions for problem 10 are queued.
- Active submissions for problem 10 are skipped.

### 21.4 Force Problem Rejudge

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/force/problem/10" \
  -H "Authorization: Bearer <TOKEN>"
```

Expected:

- All submissions for problem 10 are queued, including active ones.

### 21.5 Normal Contest Rejudge

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/contests/5" \
  -H "Authorization: Bearer <TOKEN>"
```

Expected:

- Only final submissions in contest 5 are queued.
- Active submissions in contest 5 are skipped.

### 21.6 Force Contest Rejudge

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/force/contests/5" \
  -H "Authorization: Bearer <TOKEN>"
```

Expected:

- All submissions in contest 5 are queued, including active ones.

### 21.7 Verify Database State After Force Rejudge

Example SQL:

```sql
select id, verdict, judge_run_id, execution_time, memory_usage
from submissions
where id in (1, 2, 3);
```

Expected immediately after force rejudge:

```text
verdict = PENDING_REJUDGE or RUNNING
judge_run_id has increased
execution_time = null
memory_usage = null
```

Depending on RabbitMQ timing, the consumer may quickly move
`PENDING_REJUDGE` to `RUNNING`.

### 21.8 Verify RabbitMQ Activity

Open RabbitMQ management UI:

```text
http://localhost:15672
```

Check:

```text
Queues -> submissionQueue
```

Expected:

- Message activity increases when rejudge endpoints are called.

### 21.9 Verify Judge0 Callback URLs

Backend logs should show dispatch lines like:

```text
Dispatching submission to Judge0. submissionId=<id> judgeRunId=<run> testCaseCount=<count>
Sent test case to Judge0. submissionId=<id> judgeRunId=<run> testCaseNumber=<n>
```

Expected:

- The `judgeRunId` in logs matches the current submission run.

### 21.10 Verify Stale Callback Behavior

Manual idea:

1. Create or find a running submission with `judgeRunId = 5`.
2. Force rejudge it.
3. Confirm DB now has `judgeRunId = 6`.
4. Send an old callback manually with `judgeRunId = 5`.

Example:

```bash
curl -X PUT "http://localhost:8080/api/callback/judge0/10/5/1" \
  -H "Content-Type: application/json" \
  -d "{\"status\":{\"id\":3},\"time\":\"0.010\",\"memory\":1024}"
```

Expected:

```text
Stale callback ignored
```

No result should be stored for the old run as the current run result.

Then send current callback:

```bash
curl -X PUT "http://localhost:8080/api/callback/judge0/10/6/1" \
  -H "Content-Type: application/json" \
  -d "{\"status\":{\"id\":3},\"time\":\"0.010\",\"memory\":1024}"
```

Expected:

```text
Callback accepted if testCaseNumber is valid and run ID matches.
```

## 22. Build And Test Commands

Backend compile:

```bash
cd backend
mvn -q -DskipTests compile
```

Backend tests:

```bash
cd backend
mvn -q test
```

Frontend build:

```bash
cd UI
npm run build
```

For this documentation-only change, running the full build is optional unless
you also changed code.

## 23. Expected End-To-End Flow Examples

### 23.1 Normal Rejudge Flow

```text
Admin selects submissions 1, 2, 3
  -> POST /api/admin/rejudge/submissions
  -> RejudgeService normalizes IDs
  -> DB returns submissions 1, 2, 3
  -> submission 1 is ACCEPTED, queued
  -> submission 2 is RUNNING, skipped
  -> submission 3 is WRONG_ANSWER, queued
  -> queued submissions get new judgeRunId and PENDING_REJUDGE
  -> after commit, IDs 1 and 3 are sent to RabbitMQ
  -> API response says queued 2, skipped 1
```

### 23.2 Force Rejudge Flow

```text
Admin selects submissions 1, 2, 3
  -> POST /api/admin/rejudge/force/submissions
  -> RejudgeService normalizes IDs
  -> DB returns submissions 1, 2, 3
  -> all found submissions are queued
  -> all get new judgeRunId and PENDING_REJUDGE
  -> old callbacks are stale after DB commit
  -> after commit, IDs 1, 2, 3 are sent to RabbitMQ
  -> API response says queued 3, skipped 0
```

### 23.3 Rejudge Consumer Flow

```text
RabbitMQ message contains submissionId
  -> SubmissionConsumer loads submission
  -> if verdict is ACCEPTED, skip stale message
  -> if verdict is PENDING, increment judgeRunId and set RUNNING
  -> if verdict is PENDING_REJUDGE, keep judgeRunId and set RUNNING
  -> fetch problem test cases
  -> send one Judge0 request per test case
```

### 23.4 Callback Flow

```text
Judge0 calls backend
  -> CallbackHandler receives callback
  -> Judge0CallbackService locks submission row
  -> stale run check
  -> test-case number check
  -> terminal verdict check
  -> upsert SubmissionJudgeResult
  -> count received results for this submission and run
  -> if not all arrived, keep RUNNING
  -> if all arrived, calculate final verdict and save submission
```

## 24. Why The Design Is Correct

The design is correct for this task because:

- Normal endpoints keep their old behavior and skip active submissions.
- Force endpoints are separate routes, not a query flag.
- No global force-rejudge-all endpoint exists.
- Force rejudge includes active submissions.
- The old run is invalidated immediately by advancing `judgeRunId`.
- Publishing happens after DB commit.
- The consumer does not double-increment rejudge runs.
- Judge0 callbacks include the run ID.
- Stale callbacks are ignored.
- Results are stored per submission, run, and test case.
- Final verdict waits for all expected test cases.
- Duplicate callbacks update the same logical result.
- The admin UI requires confirmation for force actions.

## 25. Quick Glossary

`PENDING`

```text
A newly submitted solution waiting to be judged.
```

`PENDING_REJUDGE`

```text
A submission intentionally queued for rejudge.
```

`RUNNING`

```text
The submission has been dispatched to Judge0 and callbacks are expected.
```

`judgeRunId`

```text
The number identifying the current judging attempt for a submission.
```

`SubmissionJudgeResult`

```text
One stored test-case result for one submission and one judge run.
```

`Stale callback`

```text
A Judge0 callback from an older run whose judgeRunId no longer matches the
current submission judgeRunId.
```

`SSE`

```text
Server-Sent Events. Used for live contest and clarification streams in this
project, but not used by rejudge itself.
```

## 26. Final Summary

The task implemented a safe force rejudge feature.

Normal rejudge remains conservative:

```text
Skip active submissions.
Only requeue final verdict submissions.
```

Force rejudge is aggressive but safe:

```text
Include active submissions.
Immediately advance judgeRunId.
Mark PENDING_REJUDGE.
Clear old metrics.
Queue after DB commit.
Ignore old callbacks.
Accept only callbacks for the current run.
```

The feature is an HTTP plus RabbitMQ plus Judge0 callback workflow, not an SSE
workflow. SSE is part of the broader AuraC2 live-update architecture, but no
rejudge progress stream exists yet.
