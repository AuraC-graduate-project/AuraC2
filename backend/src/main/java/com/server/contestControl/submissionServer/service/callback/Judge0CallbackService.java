package com.server.contestControl.submissionServer.service.callback;

import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.submissionServer.dto.Judge0Response;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.entity.SubmissionJudgeResult;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.repository.SubmissionJudgeResultRepository;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class Judge0CallbackService {

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionJudgeResultRepository judgeResultRepository;

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

        int expectedTestCaseCount = testCaseRepository.countByProblemId(submission.getProblem().getId());
        if (expectedTestCaseCount <= 0) {
            submission.setVerdict(Verdict.INTERNAL_ERROR);
            submissionRepository.save(submission);
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

        Verdict verdict = toVerdict(response);
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
        recordJudgeResult(submission, effectiveJudgeRunId, testCaseNumber, verdict, response);

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

        return ResponseEntity.ok("Judging completed -> Verdict = " + finalVerdict);
    }

    private void recordJudgeResult(
            Submission submission,
            Long judgeRunId,
            int testCaseNumber,
            Verdict verdict,
            Judge0Response response
    ) {
        SubmissionJudgeResult result = judgeResultRepository
                .findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(
                        submission.getId(),
                        judgeRunId,
                        testCaseNumber
                )
                .orElseGet(() -> SubmissionJudgeResult.builder()
                        .submission(submission)
                        .judgeRunId(judgeRunId)
                        .testCaseNumber(testCaseNumber)
                        .build());

        result.setVerdict(verdict);
        result.setExecutionTime(response == null ? 0 : response.getTimeAsInt());
        result.setMemoryUsage(response == null ? 0 : response.getMemoryAsInt());
        judgeResultRepository.save(result);
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

    private boolean isTerminalVerdict(Verdict verdict) {
        return verdict != Verdict.PENDING
                && verdict != Verdict.PENDING_REJUDGE
                && verdict != Verdict.RUNNING;
    }

    private Long effectiveJudgeRunId(Submission submission, Long callbackJudgeRunId) {
        if (callbackJudgeRunId != null) {
            return callbackJudgeRunId;
        }

        return submission.getJudgeRunId() == null ? 0L : submission.getJudgeRunId();
    }

    private boolean isStaleCallback(Submission submission, Long callbackJudgeRunId) {
        Long currentJudgeRunId = submission.getJudgeRunId();

        if (callbackJudgeRunId == null) {
            return currentJudgeRunId != null && currentJudgeRunId > 0;
        }

        return currentJudgeRunId == null || !callbackJudgeRunId.equals(currentJudgeRunId);
    }
}
