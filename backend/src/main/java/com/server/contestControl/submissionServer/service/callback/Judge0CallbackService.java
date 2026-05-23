package com.server.contestControl.submissionServer.service.callback;

import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.submissionServer.dto.Judge0Response;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.entity.SubmissionJudgeResult;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.event.SubmissionFinalizedEvent;
import com.server.contestControl.submissionServer.repository.SubmissionJudgeResultRepository;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.service.compare.OutputComparator;
import com.server.contestControl.submissionServer.service.validator.CustomValidatorService;
import com.server.contestControl.submissionServer.sse.SubmissionSsePublisher;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEvent;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEventType;
import com.server.contestControl.submissionServer.util.Judge0AuditUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class Judge0CallbackService {

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionJudgeResultRepository judgeResultRepository;
    private final SubmissionSsePublisher submissionSsePublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final OutputComparator outputComparator;
    private final CustomValidatorService customValidatorService;

    @Transactional
    public ResponseEntity<?> handleJudge0Callback(
            Long submissionId,
            Long judgeRunId,
            int testCaseNumber,
            Judge0Response response
    ) {
        Submission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        if (isStaleCallback(submission, judgeRunId)) {
            log.warn(
                    "Ignoring stale Judge0 callback. submissionId={} callbackJudgeRunId={} currentJudgeRunId={} testCaseNumber={}",
                    submissionId,
                    judgeRunId,
                    submission.getJudgeRunId(),
                    testCaseNumber
            );
            return ResponseEntity.ok("Stale callback ignored");
        }

        if (submission.getVerdict() != Verdict.RUNNING) {
            log.info(
                    "Ignoring Judge0 callback for non-running submission. submissionId={} judgeRunId={} currentVerdict={} testCaseNumber={}",
                    submissionId,
                    judgeRunId,
                    submission.getVerdict(),
                    testCaseNumber
            );
            return ResponseEntity.ok("Submission is no longer running");
        }

        int expectedTestCaseCount = testCaseRepository.countByProblemId(submission.getProblem().getId());
        if (expectedTestCaseCount <= 0) {
            submission.setVerdict(Verdict.INTERNAL_ERROR);
            submissionRepository.save(submission);
            SubmissionStreamEvent finalizedEvent =
                    submissionSsePublisher.buildEvent(SubmissionStreamEventType.FINALIZED, submission);
            SubmissionFinalizedEvent scoreboardEvent = buildFinalizedEvent(submission);
            publishAfterCommit(() -> {
                submissionSsePublisher.dispatch(finalizedEvent);
                eventPublisher.publishEvent(scoreboardEvent);
            });
            log.error("Judge0 callback received for problem without test cases. submissionId={}", submissionId);
            return ResponseEntity.ok("No test cases configured; marked INTERNAL_ERROR");
        }

        if (testCaseNumber < 1 || testCaseNumber > expectedTestCaseCount) {
            log.warn(
                    "Ignoring Judge0 callback with invalid test case number. submissionId={} testCaseNumber={} expectedTestCaseCount={}",
                    submissionId,
                    testCaseNumber,
                    expectedTestCaseCount
            );
            return ResponseEntity.badRequest().body("Invalid test case number");
        }

        ComparisonVerdict comparisonVerdict = applyComparePolicy(submission, testCaseNumber, toVerdict(response), response);
        Verdict verdict = comparisonVerdict.verdict();
        if (!isTerminalVerdict(verdict)) {
            log.info(
                    "Ignoring non-terminal Judge0 callback. submissionId={} judgeRunId={} testCaseNumber={} verdict={}",
                    submissionId,
                    effectiveJudgeRunId(submission, judgeRunId),
                    testCaseNumber,
                    verdict
            );
            return ResponseEntity.ok("Non-terminal callback ignored");
        }

        Long effectiveJudgeRunId = effectiveJudgeRunId(submission, judgeRunId);
        boolean recorded = recordJudgeResult(
                submission,
                effectiveJudgeRunId,
                testCaseNumber,
                verdict,
                response,
                comparisonVerdict.diagnostic()
        );
        if (!recorded) {
            log.info(
                    "Ignoring duplicate Judge0 callback. submissionId={} judgeRunId={} testCaseNumber={}",
                    submissionId,
                    effectiveJudgeRunId,
                    testCaseNumber
            );
            return ResponseEntity.ok("Duplicate callback ignored");
        }

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

        List<SubmissionJudgeResult> results = judgeResultRepository.findBySubmission_IdAndJudgeRunId(
                submissionId,
                effectiveJudgeRunId
        );
        Verdict finalVerdict = finalVerdict(results);

        submission.setVerdict(finalVerdict);
        submission.setExecutionTime(maxExecutionTime(results));
        submission.setMemoryUsage(maxMemoryUsage(results));
        submissionRepository.save(submission);

        log.info(
                "Judge0 run completed. submissionId={} judgeRunId={} finalVerdict={} testCaseCount={}",
                submissionId,
                effectiveJudgeRunId,
                finalVerdict,
                expectedTestCaseCount
        );

        // Capture event while still in the transaction so lazy fields are accessible,
        // then dispatch after commit so the frontend reads the committed verdict.
        SubmissionStreamEvent finalizedEvent =
                submissionSsePublisher.buildEvent(SubmissionStreamEventType.FINALIZED, submission);
        SubmissionFinalizedEvent scoreboardEvent = buildFinalizedEvent(submission);
        publishAfterCommit(() -> {
            submissionSsePublisher.dispatch(finalizedEvent);
            eventPublisher.publishEvent(scoreboardEvent);
        });

        return ResponseEntity.ok("Judging completed -> Verdict = " + finalVerdict);
    }

    private SubmissionFinalizedEvent buildFinalizedEvent(Submission submission) {
        return new SubmissionFinalizedEvent(
                submission.getId(),
                submission.getContest().getId(),
                submission.getProblem().getId(),
                submission.getUser().getId(),
                submission.getVerdict()
        );
    }

    private boolean recordJudgeResult(
            Submission submission,
            Long judgeRunId,
            int testCaseNumber,
            Verdict verdict,
            Judge0Response response,
            String diagnostic
    ) {
        if (judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(
                submission.getId(),
                judgeRunId,
                testCaseNumber
        ).isPresent()) {
            return false;
        }

        SubmissionJudgeResult result = SubmissionJudgeResult.builder()
                .submission(submission)
                .judgeRunId(judgeRunId)
                .testCaseNumber(testCaseNumber)
                .build();
        result.setVerdict(verdict);
        result.setExecutionTime(response == null ? 0 : response.getTimeAsInt());
        result.setMemoryUsage(response == null ? 0 : response.getMemoryAsInt());
        result.setJudge0StatusId(response == null || response.getStatus() == null
                ? null
                : response.getStatus().getId());
        result.setJudge0StatusDescription(response == null || response.getStatus() == null
                ? null
                : Judge0AuditUtil.safeStatusDescription(response.getStatus().getDescription()));
        result.setDiagnostic(response == null
                ? Judge0AuditUtil.firstSafeDiagnostic(diagnostic)
                : Judge0AuditUtil.firstSafeDiagnostic(
                        diagnostic,
                response.getDecodedCompileOutput(),
                response.getDecodedMessage(),
                response.getDecodedStderr()
                ));
        try {
            judgeResultRepository.save(result);
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.info(
                    "Duplicate Judge0 callback hit unique constraint. submissionId={} judgeRunId={} testCaseNumber={}",
                    submission.getId(),
                    judgeRunId,
                    testCaseNumber
            );
            return false;
        }
    }

    private Verdict finalVerdict(List<SubmissionJudgeResult> results) {
        return results.stream()
                .filter(result -> result.getVerdict() != Verdict.ACCEPTED)
                .min(Comparator.comparing(SubmissionJudgeResult::getTestCaseNumber))
                .map(SubmissionJudgeResult::getVerdict)
                .orElse(Verdict.ACCEPTED);
    }

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

    private Verdict toVerdict(Judge0Response response) {
        if (response == null || response.getStatus() == null) {
            return Verdict.INTERNAL_ERROR;
        }

        return Verdict.fromJudge0Status(response.getStatus().getId());
    }

    private ComparisonVerdict applyComparePolicy(
            Submission submission,
            int testCaseNumber,
            Verdict executionVerdict,
            Judge0Response response
    ) {
        ComparePolicy comparePolicy = effectiveComparePolicy(submission);
        if (executionVerdict != Verdict.ACCEPTED) {
            return new ComparisonVerdict(executionVerdict, null);
        }

        if (!submission.getProblem().hasActiveCustomValidator() && comparePolicy == ComparePolicy.EXACT) {
            return new ComparisonVerdict(executionVerdict, null);
        }

        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByIdAsc(submission.getProblem().getId());
        if (testCaseNumber < 1 || testCaseNumber > testCases.size()) {
            return new ComparisonVerdict(Verdict.INTERNAL_ERROR, "Missing test case for backend comparison");
        }

        TestCase testCase = testCases.get(testCaseNumber - 1);
        if (submission.getProblem().hasActiveCustomValidator()) {
            CustomValidatorService.ValidatorResult validatorResult = customValidatorService.validate(
                    submission.getProblem(),
                    testCase,
                    response == null ? null : response.getDecodedStdout()
            );
            return new ComparisonVerdict(validatorResult.verdict(), validatorResult.diagnostic());
        }

        OutputComparator.ComparisonResult comparison = outputComparator.compare(
                comparePolicy,
                testCase.getExpectedOutput(),
                response == null ? null : response.getDecodedStdout(),
                submission.getProblem().getFloatAbsoluteEpsilon(),
                submission.getProblem().getFloatRelativeEpsilon()
        );

        if (comparison.matches()) {
            return new ComparisonVerdict(Verdict.ACCEPTED, null);
        }

        return new ComparisonVerdict(Verdict.WRONG_ANSWER, comparison.diagnostic());
    }

    private ComparePolicy effectiveComparePolicy(Submission submission) {
        if (submission.getProblem() == null || submission.getProblem().getComparePolicy() == null) {
            return ComparePolicy.EXACT;
        }

        return submission.getProblem().getComparePolicy();
    }

    private boolean isTerminalVerdict(Verdict verdict) {
        return verdict != Verdict.PENDING
                && verdict != Verdict.PENDING_REJUDGE
                && verdict != Verdict.RUNNING;
    }

    private Long effectiveJudgeRunId(Submission submission, Long callbackJudgeRunId) {
        if (callbackJudgeRunId != null) {
            return callbackJudgeRunId;
        }

        return normalizeJudgeRunId(submission.getJudgeRunId());
    }

    private boolean isStaleCallback(Submission submission, Long callbackJudgeRunId) {
        Long currentJudgeRunId = normalizeJudgeRunId(submission.getJudgeRunId());

        if (callbackJudgeRunId == null) {
            // Legacy callback route (without run id) is accepted only for legacy submissions
            return currentJudgeRunId != 0L;
        }

        return !callbackJudgeRunId.equals(currentJudgeRunId);
    }

    private Long normalizeJudgeRunId(Long judgeRunId) {
        return judgeRunId == null ? 0L : judgeRunId;
    }

    private void publishAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private record ComparisonVerdict(Verdict verdict, String diagnostic) {
    }
}
