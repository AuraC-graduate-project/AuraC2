    # Rejudge Feature Technical Documentation

This document describes the rejudge feature added to the backend, including every rejudge-related file created or edited, the request flow, database changes, concurrency protections, callback aggregation, tests, known limitations, and manual testing steps.

The implementation reuses the existing submission judging pipeline:

`SubmissionProducer -> RabbitMQ submissionQueue -> SubmissionConsumer -> Judge0Service -> Judge0 callback endpoint`

The main additions are:

- Admin rejudge endpoints for selected submissions, one problem, or one contest.
- A new pending rejudge verdict state.
- A `judgeRunId` on submissions to identify each judging attempt.
- Per-test-case result persistence so asynchronous Judge0 callbacks can arrive in any order.
- Final verdict calculation only after all test-case callbacks for the current run have arrived.

## Full Rejudge Flow

1. An admin calls one of the rejudge endpoints.
2. `RejudgeController` delegates to `RejudgeService`.
3. `RejudgeService` selects submissions by scope:
   - selected submission IDs
   - all submissions for a problem
   - all submissions for a contest
4. Active submissions are skipped. Active means:
   - `PENDING`
   - `PENDING_REJUDGE`
   - `RUNNING`
5. Rejudgeable submissions are marked `PENDING_REJUDGE`.
6. Old aggregate result fields are cleared:
   - `executionTime = null`
   - `memoryUsage = null`
7. Updated submissions are saved in a transaction.
8. Submission IDs are republished to RabbitMQ after the database transaction commits.
9. `SubmissionConsumer` receives each submission ID from RabbitMQ.
10. `SubmissionConsumer` ignores any message for submissions that are not `PENDING` or `PENDING_REJUDGE`.
11. If verdict is `PENDING`, `SubmissionConsumer` increments `submission.judgeRunId`; if verdict is `PENDING_REJUDGE`, it keeps the already-reserved run ID.
12. `SubmissionConsumer` marks the submission `RUNNING`.
13. `SubmissionConsumer` fetches all test cases for the problem.
14. `Judge0Service` sends one Judge0 request per test case.
15. The Judge0 callback URL includes:
   - `submissionId`
   - `judgeRunId`
   - `testCaseNumber`
16. `CallbackHandler` receives each Judge0 callback.
17. `CallbackHandler` delegates to `Judge0CallbackService`.
18. `Judge0CallbackService` locks the submission row using `findByIdForUpdate`.
19. `Judge0CallbackService` rejects stale callbacks where callback `judgeRunId` does not match the current submission `judgeRunId`.
20. `SubmissionJudgeResult` stores or updates one result per submission, judge run, and test-case number.
21. The system waits until all expected test cases for that run have a stored result.
22. Once all callbacks arrive, the final verdict is calculated and saved to `Submission`.

## API Endpoints

All rejudge endpoints require the `ADMIN` role and live under:

```text
/api/admin/rejudge
```

### POST /api/admin/rejudge/submissions

Purpose: rejudge a specific list of submissions.

Request body:

```json
{
  "submissionIds": [1, 2, 3]
}
```

Example cURL:

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/submissions" \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d "{\"submissionIds\":[1,2,3]}"
```

Example response:

```json
{
  "scope": "SUBMISSIONS",
  "scopeId": null,
  "requestedCount": 3,
  "foundCount": 2,
  "queuedCount": 1,
  "skippedCount": 1,
  "queuedSubmissionIds": [1],
  "skippedSubmissionIds": [2],
  "missingSubmissionIds": [3]
}
```

Validation rules:

- `submissionIds` must not be null.
- The normalized list must contain at least one positive ID.
- Null, zero, and negative IDs are ignored during normalization.
- Duplicate IDs are de-duplicated while preserving first-seen order.
- Missing IDs are returned in `missingSubmissionIds`.

Skipped submissions:

- `PENDING`
- `PENDING_REJUDGE`
- `RUNNING`

### POST /api/admin/rejudge/problem/{problemId}

Purpose: rejudge all submissions for one problem.

Path variables:

- `problemId`: the problem whose submissions should be rejudged.

Example cURL:

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/problem/10" \
  -H "Authorization: Bearer <admin-access-token>"
```

Example response:

```json
{
  "scope": "PROBLEM",
  "scopeId": 10,
  "requestedCount": 42,
  "foundCount": 42,
  "queuedCount": 39,
  "skippedCount": 3,
  "queuedSubmissionIds": [101, 102, 103],
  "skippedSubmissionIds": [104, 105, 106],
  "missingSubmissionIds": []
}
```

Validation rules:

- The problem must exist.
- If the problem does not exist, `ProblemNotFoundException` is thrown.

Skipped submissions:

- `PENDING`
- `PENDING_REJUDGE`
- `RUNNING`

### POST /api/admin/rejudge/contests/{contestId}

Purpose: rejudge all submissions for one contest.

This endpoint intentionally replaces a global `/api/admin/rejudge/all` endpoint. Contest scope is required to avoid accidentally rejudging the entire system.

Path variables:

- `contestId`: the contest whose submissions should be rejudged.

Example cURL:

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/contests/5" \
  -H "Authorization: Bearer <admin-access-token>"
```

Example response:

```json
{
  "scope": "CONTEST",
  "scopeId": 5,
  "requestedCount": 250,
  "foundCount": 250,
  "queuedCount": 240,
  "skippedCount": 10,
  "queuedSubmissionIds": [201, 202, 203],
  "skippedSubmissionIds": [204, 205],
  "missingSubmissionIds": []
}
```

Validation rules:

- The contest must exist.
- If the contest does not exist, `ContestNotFoundException` is thrown.

Skipped submissions:

- `PENDING`
- `PENDING_REJUDGE`
- `RUNNING`

## Database Changes

### Submission.judgeRunId

File: `backend/src/main/java/com/server/contestControl/submissionServer/entity/Submission.java`

Change:

```java
private Long judgeRunId;
```

Pre-persist initialization:

```java
@PrePersist
public void prePersist() {
    this.createdAt = LocalDateTime.now();
    this.verdict = Verdict.PENDING;
    this.judgeRunId = 0L;
}
```

Why it was needed:

- A submission can be judged more than once.
- Judge0 callbacks from older runs can arrive after a rejudge has started.
- `judgeRunId` identifies the current judging attempt.

Risk:

- Existing rows created before this field existed may have `null` until processed. The code treats null as `0L` where needed.

### Verdict.PENDING_REJUDGE

File: `backend/src/main/java/com/server/contestControl/submissionServer/enums/Verdict.java`

Change:

```java
PENDING,
PENDING_REJUDGE,
RUNNING;
```

Why it was needed:

- `PENDING` already means a new submission is waiting for judging.
- `PENDING_REJUDGE` makes rejudge state visible and distinguishable without changing the existing lifecycle semantics.

### submission_judge_results table

Entity file: `backend/src/main/java/com/server/contestControl/submissionServer/entity/SubmissionJudgeResult.java`

Repository file: `backend/src/main/java/com/server/contestControl/submissionServer/repository/SubmissionJudgeResultRepository.java`

Entity definition:

```java
@Entity
@Table(
        name = "submission_judge_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_submission_judge_result_run_case",
                columnNames = {"submission_id", "judge_run_id", "test_case_number"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionJudgeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(name = "judge_run_id", nullable = false)
    private Long judgeRunId;

    @Column(name = "test_case_number", nullable = false)
    private Integer testCaseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verdict verdict;

    private Integer executionTime;
    private Integer memoryUsage;
    private LocalDateTime receivedAt;

    @PrePersist
    @PreUpdate
    public void touchReceivedAt() {
        this.receivedAt = LocalDateTime.now();
    }
}
```

Unique constraint:

```text
submission_id + judge_run_id + test_case_number
```

Why this table is needed:

- Judge0 callbacks are asynchronous.
- Callbacks may arrive out of order.
- The old implementation treated the callback for the last-numbered test case as proof that all earlier cases had passed.
- That assumption was unsafe.
- This table records each callback independently, then finalizes the submission only when all expected test cases for the current run have arrived.

Risk:

- No cleanup policy exists yet for old rows.
- Large contests and repeated rejudges will grow this table.

## Concurrency And Race-Condition Protections

### Publishing after DB commit

File: `backend/src/main/java/com/server/contestControl/submissionServer/service/rejudge/RejudgeService.java`

Exact code:

```java
private void publishAfterCommit(List<Long> submissionIds) {
    Runnable publisher = () -> publishSubmissions(submissionIds);

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        publisher.run();
        return;
    }

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            publisher.run();
        }
    });
}
```

Why:

- The consumer should not receive a RabbitMQ message before the database update to `PENDING_REJUDGE` is committed.
- Without this, the consumer could load stale submission state and skip or mishandle the message.

Risk:

- `queuedCount` means the submission was prepared for publishing and marked for rejudge. If RabbitMQ publishing fails after commit, the failure is logged, but the API response is not adjusted.

### judgeRunId

Files:

- `Submission.java`
- `SubmissionConsumer.java`
- `Judge0Service.java`
- `Judge0CallbackService.java`

`SubmissionConsumer` increments the run:

```java
Long nextJudgeRunId = submission.getJudgeRunId() == null ? 1L : submission.getJudgeRunId() + 1;
submission.setJudgeRunId(nextJudgeRunId);
submission.setVerdict(Verdict.RUNNING);
submissionRepository.save(submission);
```

`Judge0Service` includes the run in callback URLs:

```java
callbackUrl + "/" + submission.getId() + "/" + submission.getJudgeRunId() + "/" + testCaseNumber
```

Why:

- Old Judge0 callbacks can arrive after a new rejudge starts.
- `judgeRunId` lets the backend distinguish the current run from stale older runs.

### Stale callback handling

File: `Judge0CallbackService.java`

Exact code:

```java
private boolean isStaleCallback(Submission submission, Long callbackJudgeRunId) {
    Long currentJudgeRunId = submission.getJudgeRunId();

    if (callbackJudgeRunId == null) {
        return currentJudgeRunId != null && currentJudgeRunId > 0;
    }

    return currentJudgeRunId == null || !callbackJudgeRunId.equals(currentJudgeRunId);
}
```

Why:

- Legacy callbacks without a run ID are accepted only when current `judgeRunId` is null or zero.
- New callbacks must match the current `Submission.judgeRunId`.

### Pessimistic locking

File: `SubmissionRepository.java`

Exact code:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select submission from Submission submission where submission.id = :id")
Optional<Submission> findByIdForUpdate(@Param("id") Long id);
```

Why:

- Multiple callbacks for the same submission/run may arrive at nearly the same time.
- The service counts stored test-case results and finalizes the submission when all are present.
- Pessimistic locking serializes callback processing for the same submission and prevents concurrent finalization races.

### Per-test-case aggregation

File: `Judge0CallbackService.java`

Exact code:

```java
long receivedCount = judgeResultRepository.countBySubmission_IdAndJudgeRunId(
        submissionId,
        effectiveJudgeRunId
);

if (receivedCount < expectedTestCaseCount) {
    log.info(
            "Judge0 callback recorded. submissionId={} judgeRunId={} receivedCount={} expectedCount={}",
            submissionId,
            effectiveJudgeRunId,
            receivedCount,
            expectedTestCaseCount
    );
    return ResponseEntity.ok(
            "Test Case " + testCaseNumber + " received; waiting for "
                    + (expectedTestCaseCount - receivedCount) + " more"
    );
}
```

Why:

- Judge0 callback order is not guaranteed.
- The backend must wait for all expected callbacks before deciding `ACCEPTED`.

## Final Verdict Calculation

File: `Judge0CallbackService.java`

### How ACCEPTED is decided

The final verdict is `ACCEPTED` only if all stored test-case results for the run are `ACCEPTED`.

Exact code:

```java
private Verdict finalVerdict(List<SubmissionJudgeResult> results) {
    return results.stream()
            .filter(result -> result.getVerdict() != Verdict.ACCEPTED)
            .min(Comparator.comparing(SubmissionJudgeResult::getTestCaseNumber))
            .map(SubmissionJudgeResult::getVerdict)
            .orElse(Verdict.ACCEPTED);
}
```

### How failing verdicts are selected

If one or more test cases fail, the first failing test case by `testCaseNumber` determines the final verdict.

Example:

- case 1: `ACCEPTED`
- case 2: `WRONG_ANSWER`
- case 3: `TLE`

Final verdict: `WRONG_ANSWER`

### Execution time and memory usage

The aggregate `Submission.executionTime` and `Submission.memoryUsage` are stored as the maximum values observed among all test-case results in the run.

Exact code:

```java
private int maxExecutionTime(List<SubmissionJudgeResult> results) {
    return results.stream()
            .map(SubmissionJudgeResult::getExecutionTime)
            .filter(value -> value != null)
            .max(Integer::compareTo)
            .orElse(0);
}

private int maxMemoryUsage(List<SubmissionJudgeResult> results) {
    return results.stream()
            .map(SubmissionJudgeResult::getMemoryUsage)
            .filter(value -> value != null)
            .max(Integer::compareTo)
            .orElse(0);
}
```

Design decision:

- The previous model only had one aggregate time and memory value.
- Max is a conservative aggregate and avoids order-dependent behavior.

### Invalid or non-terminal callbacks

Null response or null status:

```java
private Verdict toVerdict(Judge0Response response) {
    if (response == null || response.getStatus() == null) {
        return Verdict.INTERNAL_ERROR;
    }

    return Verdict.fromJudge0Status(response.getStatus().getId());
}
```

Non-terminal statuses:

```java
private boolean isTerminalVerdict(Verdict verdict) {
    return verdict != Verdict.PENDING
            && verdict != Verdict.PENDING_REJUDGE
            && verdict != Verdict.RUNNING;
}
```

Invalid test-case numbers:

```java
if (testCaseNumber < 1 || testCaseNumber > expectedTestCaseCount) {
    log.warn(
            "Ignoring Judge0 callback with invalid test case number. submissionId={} testCaseNumber={} expectedTestCaseCount={}",
            submissionId,
            testCaseNumber,
            expectedTestCaseCount
    );
    return ResponseEntity.badRequest().body("Invalid test case number");
}
```

Problems with no test cases:

```java
if (expectedTestCaseCount <= 0) {
    submission.setVerdict(Verdict.INTERNAL_ERROR);
    submissionRepository.save(submission);
    log.error("Judge0 callback received for problem without test cases. submissionId={}", submissionId);
    return ResponseEntity.ok("No test cases configured; marked INTERNAL_ERROR");
}
```

## File-By-File Documentation

### README.md

Status: edited.

Exact code modified:

```diff
-| Per-test-case final aggregated tracking | Partial |
+| Per-test-case final aggregated tracking | Implemented for judge runs |
```

```diff
-/api/callback/judge0/{submissionId}/{testCaseNumber}
+/api/callback/judge0/{submissionId}/{judgeRunId}/{testCaseNumber}
```

```diff
- marks the submission as failed immediately on the first failing test case
- marks it as accepted when the final test case passes
+ stores one result per submission, judge run, and test case
+ waits until all test case callbacks for the current judge run are received
+ calculates the final verdict from the completed run, so out-of-order callbacks cannot mark a submission accepted early
```

Added API documentation:

~~~md
### Admin Rejudge Endpoints

All rejudge endpoints require the `ADMIN` role. Rejudge reuses the normal RabbitMQ submission queue and Judge0 callback flow.

#### Rejudge selected submissions
```http
POST /api/admin/rejudge/submissions
```

Example body:
```json
{
  "submissionIds": [1, 2, 3]
}
```

#### Rejudge all submissions for a problem
```http
POST /api/admin/rejudge/problem/{problemId}
```

#### Rejudge all submissions in a contest
```http
POST /api/admin/rejudge/contests/{contestId}
```
~~~

Why:

- Keeps public project docs aligned with the new API and callback behavior.

Risk:

- README examples are concise and do not replace OpenAPI or Postman docs.

### backend/pom.xml

Status: edited.

Exact code added:

```xml
<proc>full</proc>
```

Context:

```xml
<configuration>
    <release>${java.version}</release>
    <proc>full</proc>
    <annotationProcessorPaths>
```

Why:

- The local build uses JDK 23.
- Explicit annotation processing avoids Lombok getters/builders being skipped under newer JDK behavior.

How it fits:

- Not directly part of rejudge logic, but needed for reliable compilation and tests.

Risk:

- Low. It makes annotation processing explicit for the Maven compiler plugin.

### backend/src/main/java/com/server/contestControl/authServer/config/SecurityConfiguration.java

Status: edited.

Exact code added:

```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
```

Context:

```java
.requestMatchers("/api/contest/**").hasRole("ADMIN")
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/submissions/**").hasAnyRole("TEAM", "ADMIN")
```

Why:

- Rejudge endpoints live under `/api/admin/rejudge/**`.
- This route-level rule makes `/api/admin/**` consistently admin-only.

How it fits:

- `RejudgeController` also uses `@PreAuthorize("hasRole('ADMIN')")`.
- This gives both request-matcher and method-level protection.

Risk:

- Existing `/api/admin/users/**` endpoints were already admin-only via class-level `@PreAuthorize`.
- This change is consistent with that existing security design.

### backend/src/main/java/com/server/contestControl/submissionServer/controller/RejudgeController.java

Status: created.

Exact code:

```java
@RestController
@RequestMapping("/api/admin/rejudge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RejudgeController {

    private final RejudgeService rejudgeService;

    @PostMapping("/submissions")
    public ResponseEntity<RejudgeResponse> rejudgeSubmissions(
            @RequestBody RejudgeSubmissionsRequest request
    ) {
        return ResponseEntity.ok(
                rejudgeService.rejudgeSelectedSubmissions(
                        request == null ? null : request.submissionIds()
                )
        );
    }

    @PostMapping("/problem/{problemId}")
    public ResponseEntity<RejudgeResponse> rejudgeProblem(@PathVariable Long problemId) {
        return ResponseEntity.ok(rejudgeService.rejudgeProblem(problemId));
    }

    @PostMapping("/contests/{contestId}")
    public ResponseEntity<RejudgeResponse> rejudgeContest(@PathVariable Long contestId) {
        return ResponseEntity.ok(rejudgeService.rejudgeContest(contestId));
    }
}
```

Why:

- Adds admin-facing API entry points for all supported rejudge scopes.

How it fits:

- Thin controller.
- All business logic stays in `RejudgeService`.

Design decisions:

- No global `/all` endpoint. Contest-wide rejudge requires `contestId`.
- Null body handling delegates validation to `RejudgeService`.

### backend/src/main/java/com/server/contestControl/submissionServer/service/rejudge/RejudgeService.java

Status: created.

Key exact code:

```java
private static final Set<Verdict> ACTIVE_VERDICTS = EnumSet.of(
        Verdict.PENDING,
        Verdict.PENDING_REJUDGE,
        Verdict.RUNNING
);
```

```java
@Transactional
public RejudgeResponse rejudgeSelectedSubmissions(List<Long> submissionIds) {
    List<Long> requestedIds = normalizeSubmissionIds(submissionIds);
    log.info("Starting selected submission rejudge. requestedCount={}", requestedIds.size());

    List<Submission> submissions = submissionRepository.findAllById(requestedIds);
    RejudgeResponse response = rejudgeSubmissions("SUBMISSIONS", null, requestedIds, submissions);
    ...
    return response;
}
```

```java
@Transactional
public RejudgeResponse rejudgeProblem(Long problemId) {
    if (!problemRepository.existsById(problemId)) {
        throw new ProblemNotFoundException(problemId);
    }

    log.info("Starting problem rejudge. problemId={}", problemId);
    List<Submission> submissions = submissionRepository.findAllByProblem_Id(problemId);
    ...
    return response;
}
```

```java
@Transactional
public RejudgeResponse rejudgeContest(Long contestId) {
    if (!contestRepository.existsById(contestId)) {
        throw new ContestNotFoundException(contestId);
    }

    log.info("Starting contest-wide rejudge. contestId={}", contestId);
    List<Submission> submissions = submissionRepository.findAllByContest_Id(contestId);
    ...
    return response;
}
```

```java
for (Submission submission : submissions) {
    if (ACTIVE_VERDICTS.contains(submission.getVerdict())) {
        skippedIds.add(submission.getId());
        continue;
    }

    submission.setVerdict(Verdict.PENDING_REJUDGE);
    submission.setExecutionTime(null);
    submission.setMemoryUsage(null);
    submissionsToQueue.add(submission);
    queuedIds.add(submission.getId());
}
```

```java
if (!submissionsToQueue.isEmpty()) {
    submissionRepository.saveAll(submissionsToQueue);
    publishAfterCommit(queuedIds);
}
```

Why:

- Centralizes all rejudge behavior.
- Avoids duplicating the existing judging pipeline.

How it fits:

- Produces the same RabbitMQ submission IDs that normal submissions use.
- The existing `SubmissionConsumer` handles actual Judge0 dispatch.

Risks:

- RabbitMQ publish failures after commit are logged but not reflected in the response.
- There is no rejudge job table for progress or retries.

### backend/src/main/java/com/server/contestControl/submissionServer/dto/RejudgeSubmissionsRequest.java

Status: created.

Exact code:

```java
public record RejudgeSubmissionsRequest(
        List<Long> submissionIds
) {
}
```

Why:

- Defines the selected-submissions request body.

How it fits:

- Used by `POST /api/admin/rejudge/submissions`.

Risk:

- Validation is performed in service code, not bean validation annotations.

### backend/src/main/java/com/server/contestControl/submissionServer/dto/RejudgeResponse.java

Status: created.

Exact code:

```java
public record RejudgeResponse(
        String scope,
        Long scopeId,
        int requestedCount,
        int foundCount,
        int queuedCount,
        int skippedCount,
        List<Long> queuedSubmissionIds,
        List<Long> skippedSubmissionIds,
        List<Long> missingSubmissionIds
) {
}
```

Why:

- Gives admins a clear summary of what happened.

How it fits:

- Returned by all rejudge endpoints.

Risk:

- `queuedCount` reflects submissions prepared for publishing, not a durable job status.

### backend/src/main/java/com/server/contestControl/submissionServer/exceptions/InvalidRejudgeRequestException.java

Status: created.

Exact code:

```java
public class InvalidRejudgeRequestException extends ApiException {
    public InvalidRejudgeRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
```

Why:

- Provides a typed `400 BAD_REQUEST` for invalid selected-submission rejudge requests.

How it fits:

- Thrown by `RejudgeService.normalizeSubmissionIds`.

Risk:

- None beyond existing global exception handling behavior.

### backend/src/main/java/com/server/contestControl/submissionServer/entity/Submission.java

Status: edited.

Exact code added:

```java
private Long judgeRunId;
```

```java
this.judgeRunId = 0L;
```

Why:

- Tracks the current judge attempt for a submission.

How it fits:

- Incremented by `SubmissionConsumer`.
- Included in Judge0 callback URLs.
- Checked by `Judge0CallbackService` to ignore stale callbacks.

Risk:

- Existing rows may have null `judgeRunId`.
- The code handles null by treating it as zero.

### backend/src/main/java/com/server/contestControl/submissionServer/enums/Verdict.java

Status: edited.

Exact code added:

```java
PENDING_REJUDGE,
```

Why:

- Represents submissions intentionally queued for rejudge.

How it fits:

- Set by `RejudgeService`.
- Accepted by `SubmissionConsumer` as queueable.
- Treated as non-terminal by callback logic.

### backend/src/main/java/com/server/contestControl/submissionServer/repository/SubmissionRepository.java

Status: edited.

Exact code added:

```java
List<Submission> findAllByProblem_Id(Long problemId);
List<Submission> findAllByContest_Id(Long contestId);
```

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select submission from Submission submission where submission.id = :id")
Optional<Submission> findByIdForUpdate(@Param("id") Long id);
```

Why:

- Problem and contest rejudge scopes need repository queries.
- Callback aggregation needs row-level locking.

How it fits:

- `RejudgeService` uses `findAllByProblem_Id` and `findAllByContest_Id`.
- `Judge0CallbackService` uses `findByIdForUpdate`.

Risk:

- Pessimistic locking depends on database transaction support.

### backend/src/main/java/com/server/contestControl/submissionServer/entity/SubmissionJudgeResult.java

Status: created.

Exact code:

```java
@Entity
@Table(
        name = "submission_judge_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_submission_judge_result_run_case",
                columnNames = {"submission_id", "judge_run_id", "test_case_number"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionJudgeResult {
    ...
}
```

Full fields:

```java
private Long id;
private Submission submission;
private Long judgeRunId;
private Integer testCaseNumber;
private Verdict verdict;
private Integer executionTime;
private Integer memoryUsage;
private LocalDateTime receivedAt;
```

Why:

- Stores one Judge0 result per test case per judge run.

How it fits:

- `Judge0CallbackService` records results here before finalizing a submission.

Risk:

- No cleanup policy yet.

### backend/src/main/java/com/server/contestControl/submissionServer/repository/SubmissionJudgeResultRepository.java

Status: created.

Exact code:

```java
public interface SubmissionJudgeResultRepository extends JpaRepository<SubmissionJudgeResult, Long> {

    Optional<SubmissionJudgeResult> findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(
            Long submissionId,
            Long judgeRunId,
            Integer testCaseNumber
    );

    long countBySubmission_IdAndJudgeRunId(Long submissionId, Long judgeRunId);

    List<SubmissionJudgeResult> findBySubmission_IdAndJudgeRunId(Long submissionId, Long judgeRunId);
}
```

Why:

- Supports idempotent per-test-case callback upsert behavior.
- Counts received test-case results.
- Loads all results for final verdict calculation.

Risk:

- Duplicate callbacks overwrite the same row because lookup uses submission, run, and test-case number.

### backend/src/main/java/com/server/contestControl/submissionServer/queue/submission/SubmissionConsumer.java

Status: edited.

Exact code added:

```java
private static final Set<Verdict> QUEUEABLE_VERDICTS = EnumSet.of(
        Verdict.PENDING,
        Verdict.PENDING_REJUDGE
);
```

```java
if (!QUEUEABLE_VERDICTS.contains(submission.getVerdict())) {
    log.info(
            "Skipping submission queue message because submission is not pending. submissionId={} verdict={}",
            submissionId,
            submission.getVerdict()
    );
    return;
}
```

```java
Long nextJudgeRunId = submission.getJudgeRunId() == null ? 1L : submission.getJudgeRunId() + 1;
submission.setJudgeRunId(nextJudgeRunId);
submission.setVerdict(Verdict.RUNNING);
submissionRepository.save(submission);
```

Why:

- Prevents duplicate or stale queue messages from re-dispatching submissions that are already running or final.
- Starts a new judge run for normal pending submissions and rejudged submissions.

How it fits:

- Bridges the rejudge service and Judge0 dispatch without duplicating queue logic.

Risk:

- If a submission is stuck in `PENDING`, rejudge will skip it as active. A separate retry/stuck-job tool would be needed.

### backend/src/main/java/com/server/contestControl/submissionServer/service/judge/Judge0Service.java

Status: edited.

Exact code modified:

```java
callbackUrl + "/" + submission.getId() + "/" + submission.getJudgeRunId() + "/" + testCaseNumber
```

Exact logging added:

```java
log.info(
        "Sent test case to Judge0. submissionId={} judgeRunId={} testCaseNumber={}",
        submission.getId(),
        submission.getJudgeRunId(),
        testCaseNumber
);
```

Why:

- Judge0 callback URLs must include `judgeRunId`.

How it fits:

- Lets `Judge0CallbackService` ignore stale callbacks.

Risk:

- Older in-flight Judge0 requests may still call the legacy URL without `judgeRunId`. `CallbackHandler` keeps that route for compatibility.

### backend/src/main/java/com/server/contestControl/submissionServer/service/callback/CallbackHandler.java

Status: edited.

Exact code added:

```java
private final Judge0CallbackService callbackService;
```

Legacy route:

```java
@PutMapping("/{submissionId}/{testCaseNumber}")
public ResponseEntity<?> handleLegacyJudge0Callback(
        @PathVariable Long submissionId,
        @PathVariable int testCaseNumber,
        @RequestBody Judge0Response response
) {
    return callbackService.handleJudge0Callback(submissionId, null, testCaseNumber, response);
}
```

Current route:

```java
@PutMapping("/{submissionId}/{judgeRunId}/{testCaseNumber}")
public ResponseEntity<?> handleJudge0Callback(
        @PathVariable Long submissionId,
        @PathVariable Long judgeRunId,
        @PathVariable int testCaseNumber,
        @RequestBody Judge0Response response
) {
    return callbackService.handleJudge0Callback(submissionId, judgeRunId, testCaseNumber, response);
}
```

Why:

- Keeps controller thin.
- Moves transactional aggregation into `Judge0CallbackService`.
- Preserves backward compatibility for old callbacks.

Risk:

- Legacy callbacks are considered stale once `judgeRunId` is greater than zero.

### backend/src/main/java/com/server/contestControl/submissionServer/service/callback/Judge0CallbackService.java

Status: created.

Key exact code:

```java
@Transactional
public ResponseEntity<?> handleJudge0Callback(
        Long submissionId,
        Long judgeRunId,
        int testCaseNumber,
        Judge0Response response
) {
    Submission submission = submissionRepository.findByIdForUpdate(submissionId)
            .orElseThrow(() -> new RuntimeException("Submission not found"));
    ...
}
```

```java
recordJudgeResult(submission, effectiveJudgeRunId, testCaseNumber, verdict, response);
```

```java
List<SubmissionJudgeResult> results = judgeResultRepository.findBySubmission_IdAndJudgeRunId(
        submissionId,
        effectiveJudgeRunId
);
Verdict finalVerdict = finalVerdict(results);

submission.setVerdict(finalVerdict);
submission.setExecutionTime(maxExecutionTime(results));
submission.setMemoryUsage(maxMemoryUsage(results));
submissionRepository.save(submission);
```

Why:

- Correctly handles asynchronous Judge0 callbacks.
- Serializes updates for a submission.
- Computes final verdict only after all test cases for the run are received.

How it fits:

- `CallbackHandler` delegates all callback processing here.

Risk:

- Uses `RuntimeException("Submission not found")`, matching existing style but not a typed API exception.
- No cleanup of old run results.

### backend/src/test/java/com/server/contestControl/submissionServer/service/rejudge/RejudgeServiceTest.java

Status: created.

Tests added:

```java
void rejudgeSelectedSubmissionsQueuesOnlyFinalVerdicts()
```

Checks:

- Final submissions are marked `PENDING_REJUDGE`.
- Old execution time and memory are cleared.
- Active `RUNNING` submissions are skipped.
- Missing selected IDs are reported.
- Only queued submissions are republished.

```java
void rejudgeSelectedSubmissionsRejectsEmptyRequest()
```

Checks:

- Empty selected rejudge requests throw `InvalidRejudgeRequestException`.
- Repository lookup is not called for invalid input.

```java
void rejudgeProblemUsesProblemScope()
```

Checks:

- Problem scope returns `scope = "PROBLEM"`.
- The matching problem submissions are queued.

```java
void rejudgeContestRequiresContestScope()
```

Checks:

- Contest scope returns `scope = "CONTEST"`.
- Contest rejudge requires an explicit contest ID.

Why these tests matter:

- They protect idempotency.
- They protect active-submission skipping.
- They protect the API decision to avoid global rejudge.

### backend/src/test/java/com/server/contestControl/submissionServer/service/callback/Judge0CallbackServiceTest.java

Status: created.

Tests added:

```java
void acceptedLastNumberedCallbackDoesNotFinalizeUntilAllResultsArrive()
```

Checks:

- If test case 2 of 2 arrives first as `ACCEPTED`, the submission remains `RUNNING`.
- The submission is not finalized before test case 1 arrives.

Why:

- Protects against the original out-of-order callback bug.

```java
void finalVerdictUsesAllCompletedResultsInTestCaseOrder()
```

Checks:

- If all results have arrived, the final verdict is calculated from the first failing test case.
- A later accepted result cannot hide an earlier failing result.

Why:

- Protects final verdict aggregation.

```java
void staleCallbackFromOlderRunIsIgnored()
```

Checks:

- A callback with older `judgeRunId` is ignored.
- No result row or submission update is saved.

Why:

- Protects rejudge from stale Judge0 callbacks.

### admin-account.txt

Status: modified indirectly by tests.

Why it appears in `git status`:

- Existing `AdminBootstrapRunner` rewrites `admin-account.txt` during Spring context startup.
- Running `mvn test` triggers the existing context-load test and rewrites this file.

How it fits:

- It is not part of the rejudge feature.

Risk:

- The file may contain generated credentials. Its content is intentionally not copied into this documentation.

## Before vs After

### Callback correctness

Before:

- Callback handling depended on callback order.
- If the last-numbered test case callback arrived first and was accepted, the submission could be marked `ACCEPTED` before an earlier failing callback arrived.

After:

- Each test-case callback is stored in `submission_judge_results`.
- Final verdict is calculated only after all expected test cases for the current `judgeRunId` are received.
- Out-of-order callbacks are safe.

### Rejudge capability

Before:

- There was no admin rejudge API.
- Existing submissions could not be requeued through the backend.

After:

- Admins can rejudge selected submissions.
- Admins can rejudge all submissions for a problem.
- Admins can rejudge all submissions for a contest.
- Rejudge reuses the existing RabbitMQ and Judge0 pipeline.

## Known Limitations And Future Improvements

- No separate rejudge job table exists yet.
- No progress tracking exists for large rejudge batches.
- No retry mechanism exists for failed RabbitMQ republish after DB commit.
- `RejudgeResponse.queuedCount` does not guarantee RabbitMQ publish success if RabbitMQ fails after commit.
- No cleanup policy exists for old `submission_judge_results` rows.
- No batch pagination exists for very large problem or contest rejudge operations.
- There is no UI integration documented here.
- There is no scoreboard recalculation logic yet, because scoreboard/ranking is not implemented in the current backend.
- No database migration tool is present; with current Hibernate `ddl-auto:update`, schema changes are applied automatically in development.

## How To Test Manually

### 1. Start dependencies

Start PostgreSQL, RabbitMQ, and the backend as usual.

RabbitMQ management UI:

```text
http://localhost:15672
```

### 2. Login as admin

```bash
curl -X POST "http://localhost:8080/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"<admin-password>\"}"
```

Copy the returned access token.

### 3. Rejudge selected submissions

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/submissions" \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d "{\"submissionIds\":[1,2,3]}"
```

Verify:

- Response contains `queuedSubmissionIds`.
- Response contains skipped active submissions under `skippedSubmissionIds`.
- Database rows in `submissions` are set to `PENDING_REJUDGE`.
- `executionTime` and `memoryUsage` are null for queued rows.

### 4. Rejudge a problem

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/problem/10" \
  -H "Authorization: Bearer <admin-access-token>"
```

Verify:

- All non-active submissions for problem 10 are queued.
- Active submissions are skipped.

### 5. Rejudge a contest

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/contests/5" \
  -H "Authorization: Bearer <admin-access-token>"
```

Verify:

- All non-active submissions for contest 5 are queued.
- There is no global all-submissions endpoint.

### 6. Verify RabbitMQ receives messages

In RabbitMQ management UI:

- Open `Queues`.
- Inspect `submissionQueue`.
- Confirm message activity when rejudge is requested.

Expected payload:

```text
submissionId
```

The system still publishes the same `Long submissionId` payload used by normal submission judging.

### 7. Verify Judge0 dispatch

Check backend logs for:

```text
Dispatching submission to Judge0. submissionId=<id> judgeRunId=<run> testCaseCount=<count>
Sent test case to Judge0. submissionId=<id> judgeRunId=<run> testCaseNumber=<n>
```

### 8. Verify per-test-case callback storage

Query the database:

```sql
select *
from submission_judge_results
where submission_id = <submission_id>
order by judge_run_id, test_case_number;
```

Verify:

- One row per test case.
- `judge_run_id` matches the current submission `judgeRunId`.
- Duplicate callbacks update the same `(submission_id, judge_run_id, test_case_number)` logical result.

### 9. Verify final verdict updates only after all callbacks arrive

Use logs:

Before all callbacks:

```text
Judge0 callback recorded. submissionId=<id> judgeRunId=<run> receivedCount=<n> expectedCount=<total>
```

After all callbacks:

```text
Judge0 run completed. submissionId=<id> judgeRunId=<run> finalVerdict=<verdict> testCaseCount=<total>
```

Database check:

```sql
select id, verdict, execution_time, memory_usage, judge_run_id
from submissions
where id = <submission_id>;
```

Expected:

- Submission remains `RUNNING` until all results are recorded.
- Final verdict is saved only after `receivedCount == expectedTestCaseCount`.

## Force Rejudge

### Overview

Force rejudge is an extension of the normal rejudge system that allows administrators to rejudge submissions even when they are in an active state (PENDING, PENDING_REJUDGE, or RUNNING).

### Difference Between Normal Rejudge and Force Rejudge

Normal rejudge skips submissions that are currently PENDING, PENDING_REJUDGE, or RUNNING. It only requeues submissions that have already reached a final verdict (ACCEPTED, WRONG_ANSWER, TLE, etc.).

Force rejudge includes all submissions regardless of their current state. When a submission is RUNNING, force rejudge logically cancels the old judging attempt immediately by advancing the `judgeRunId` in the database before re-queuing.

### How Force Rejudge Is Safe

Force rejudge does not physically stop Judge0 execution. The external Judge0 process will continue running and eventually call back, but the callback will be discarded because:

1. When force rejudge is called, `RejudgeService` immediately sets `judgeRunId = currentJudgeRunId + 1` on the submission entity.
2. The old Judge0 process will call back with the old `judgeRunId`.
3. `Judge0CallbackService.isStaleCallback()` compares the callback's `judgeRunId` with the submission's current `judgeRunId`.
4. Since they no longer match, the old callback is logged and ignored.
5. The newly queued run will use the advanced `judgeRunId`, and its callbacks will match.

Example scenario:

- Submission is RUNNING with `judgeRunId = 5`.
- Admin calls force rejudge.
- `RejudgeService` sets `judgeRunId = 6`, verdict = PENDING_REJUDGE, clears execution metrics.
- Old Judge0 callback arrives with `judgeRunId = 5` — rejected as stale.
- `SubmissionConsumer` picks up the re-queued submission, sees PENDING_REJUDGE, does NOT increment `judgeRunId` again (it was already reserved), sets verdict = RUNNING.
- New Judge0 callbacks arrive with `judgeRunId = 6` — accepted and processed normally.

### Force Rejudge and SubmissionConsumer Behavior

The `SubmissionConsumer` distinguishes between normal and rejudge submissions:

- If verdict is **PENDING**: this is a normal new submission. The consumer increments `judgeRunId` before dispatching to Judge0.
- If verdict is **PENDING_REJUDGE**: this is a rejudge submission. The consumer does NOT increment `judgeRunId` (it was already reserved by `RejudgeService`). It only sets verdict to RUNNING and dispatches.

This prevents a double-increment that would cause the consumer's Judge0 callbacks to use a different `judgeRunId` than the one reserved by `RejudgeService`.

### Per-Test-Case Aggregation

Force rejudge still uses the same per-test-case callback aggregation. The submission stays RUNNING until all expected test-case callbacks for the current `judgeRunId` arrive. Out-of-order callbacks are safe.

### API Endpoints

All force rejudge endpoints require the `ADMIN` role and live under:

```text
/api/admin/rejudge/force
```

#### POST /api/admin/rejudge/force/submissions

Purpose: force rejudge a specific list of submissions, including active ones.

Request body:

```json
{
  "submissionIds": [1, 2, 3]
}
```

Example cURL:

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/force/submissions" \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d "{\"submissionIds\":[1,2,3]}"
```

Example response:

```json
{
  "scope": "FORCE_SUBMISSIONS",
  "scopeId": null,
  "requestedCount": 3,
  "foundCount": 3,
  "queuedCount": 3,
  "skippedCount": 0,
  "queuedSubmissionIds": [1, 2, 3],
  "skippedSubmissionIds": [],
  "missingSubmissionIds": []
}
```

Validation rules (same as normal selected rejudge):

- `submissionIds` must not be null.
- The normalized list must contain at least one positive ID.
- Null, zero, and negative IDs are ignored during normalization.
- Duplicate IDs are de-duplicated while preserving first-seen order.
- Missing IDs are returned in `missingSubmissionIds`.

#### POST /api/admin/rejudge/force/problem/{problemId}

Purpose: force rejudge all submissions for one problem, including active ones.

Example cURL:

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/force/problem/10" \
  -H "Authorization: Bearer <admin-access-token>"
```

Example response:

```json
{
  "scope": "FORCE_PROBLEM",
  "scopeId": 10,
  "requestedCount": 42,
  "foundCount": 42,
  "queuedCount": 42,
  "skippedCount": 0,
  "queuedSubmissionIds": [101, 102, 103],
  "skippedSubmissionIds": [],
  "missingSubmissionIds": []
}
```

#### POST /api/admin/rejudge/force/contests/{contestId}

Purpose: force rejudge all submissions in a contest, including active ones.

Example cURL:

```bash
curl -X POST "http://localhost:8080/api/admin/rejudge/force/contests/5" \
  -H "Authorization: Bearer <admin-access-token>"
```

Example response:

```json
{
  "scope": "FORCE_CONTEST",
  "scopeId": 5,
  "requestedCount": 250,
  "foundCount": 250,
  "queuedCount": 250,
  "skippedCount": 0,
  "queuedSubmissionIds": [201, 202, 203],
  "skippedSubmissionIds": [],
  "missingSubmissionIds": []
}
```

### Frontend Integration

The admin UI has been updated:

- **Submissions view**: Checkboxes on each row allow selecting submissions. Two action buttons appear when selections exist: "Rejudge" (normal) and "Force Rejudge" (with destructive styling). Force rejudge requires a confirmation dialog.
- **Rejudge view** (sidebar): A dedicated admin view allows rejudging by problem ID or contest ID, with both normal and force variants. Force rejudge uses confirmation dialogs with clear explanations.

### Known Limitations

- Force rejudge does not cancel external Judge0 jobs physically. It only invalidates their callbacks logically via `judgeRunId` advancement.
- RabbitMQ publish failure after DB commit is still a limitation unless a durable rejudge job table is added.
- Old `submission_judge_results` rows from previous runs are not cleaned up. This is future work.
- Scoreboard recalculation may still be future work if scoreboard is not implemented.
- No batch pagination for very large force rejudge operations.

### Tests Added

- `normalRejudgeStillSkipsActiveSubmissions` — Verifies normal rejudge skips PENDING/RUNNING/PENDING_REJUDGE.
- `forceRejudgeQueuesActiveAndFinalSubmissions` — Verifies force rejudge includes all submissions.
- `forceRejudgeAdvancesJudgeRunIdImmediately` — Verifies `judgeRunId` is incremented during force rejudge.
- `consumerDoesNotDoubleIncrementPendingRejudge` — Verifies consumer preserves reserved `judgeRunId`.
- `consumerStillIncrementsNormalPendingSubmission` — Verifies consumer still increments for new submissions.
- `staleCallbackAfterForceRejudgeIsIgnored` — Verifies old callbacks are discarded.
- `currentCallbackAfterForceRejudgeIsAccepted` — Verifies new callbacks are processed normally.

## Verification Commands

The implementation was verified with:

```bash
mvn -q -DskipTests compile
mvn -q test
```

Both commands passed after this feature was implemented.
