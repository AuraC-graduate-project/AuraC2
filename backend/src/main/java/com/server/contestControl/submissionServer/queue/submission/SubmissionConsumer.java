package com.server.contestControl.submissionServer.queue.submission;

import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.submissionServer.config.RabbitMQConfig;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.entity.SubmissionJudgeResult;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.event.SubmissionFinalizedEvent;
import com.server.contestControl.submissionServer.repository.SubmissionJudgeResultRepository;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.service.judge.Judge0Service;
import com.server.contestControl.submissionServer.sse.SubmissionSsePublisher;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEventType;
import com.server.contestControl.submissionServer.util.Judge0AuditUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.server.contestControl.submissionServer.util.LanguageMapper.convertLanguage;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionConsumer {

    private static final Set<Verdict> QUEUEABLE_VERDICTS = EnumSet.of(
            Verdict.PENDING,
            Verdict.PENDING_REJUDGE
    );

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final Judge0Service judge0Service;
    private final SubmissionSsePublisher submissionSsePublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final SubmissionJudgeResultRepository judgeResultRepository;

    @RabbitListener(queues = RabbitMQConfig.SUBMISSION_QUEUE)
    @Transactional
    public void handleSubmission(Long submissionId) {
        Submission submission = submissionRepository.findByIdWithContestProblemUserForUpdate(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        if (!QUEUEABLE_VERDICTS.contains(submission.getVerdict())) {
            log.info(
                    "Skipping submission queue message because submission is not pending. submissionId={} verdict={}",
                    submissionId,
                    submission.getVerdict()
            );
            return;
        }

        List<TestCase> testCases = testCaseRepository.findByProblemId(submission.getProblem().getId());
        int languageId;
        try {
            languageId = convertLanguage(submission.getLanguage());
        } catch (RuntimeException ex) {
            markInternalError(submission, null, "Unsupported language: " + submission.getLanguage(), ex);
            return;
        }

        if (testCases.isEmpty()) {
            submission.setVerdict(Verdict.INTERNAL_ERROR);
            submissionRepository.save(submission);
            try {
                submissionSsePublisher.publish(SubmissionStreamEventType.FINALIZED, submission);
            } catch (RuntimeException e) {
                log.warn(
                        "Failed to publish zero-test-case submission event. submissionId={} cause={}: {}",
                        submission.getId(),
                        e.getClass().getSimpleName(),
                        e.getMessage()
                );
            }
            eventPublisher.publishEvent(new SubmissionFinalizedEvent(
                    submission.getId(),
                    submission.getContest().getId(),
                    submission.getProblem().getId(),
                    submission.getUser().getId(),
                    submission.getVerdict()
            ));
            log.warn("Submission marked INTERNAL_ERROR because problem has no test cases. submissionId={}", submissionId);
            return;
        }

        if (submission.getVerdict() == Verdict.PENDING) {
            // Normal new submission: increment judgeRunId here
            Long nextJudgeRunId = submission.getJudgeRunId() == null ? 1L : submission.getJudgeRunId() + 1;
            submission.setJudgeRunId(nextJudgeRunId);
        }
        // PENDING_REJUDGE: judgeRunId was already reserved by RejudgeService — do NOT increment again

        submission.setVerdict(Verdict.RUNNING);
        submissionRepository.save(submission);

        // Live UI updates should never prevent the actual judge dispatch.
        try {
            submissionSsePublisher.publish(SubmissionStreamEventType.RUNNING, submission);
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to publish RUNNING submission event. Continuing judge dispatch. submissionId={} cause={}: {}",
                    submission.getId(),
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
        }

        log.info(
                "Dispatching submission to Judge0. submissionId={} judgeRunId={} testCaseCount={}",
                submission.getId(),
                submission.getJudgeRunId(),
                testCases.size()
        );

        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            int testCaseNumber = i + 1;
            try {
                judge0Service.sendSingleTest(submission, tc, testCaseNumber, languageId);
            } catch (RuntimeException ex) {
                markInternalError(submission, testCaseNumber, "Judge0 dispatch failed", ex);
                return;
            }
        }
    }

    private void markInternalError(
            Submission submission,
            Integer testCaseNumber,
            String statusDescription,
            RuntimeException cause
    ) {
        submission.setVerdict(Verdict.INTERNAL_ERROR);
        submission.setExecutionTime(0);
        submission.setMemoryUsage(0);

        if (testCaseNumber != null) {
            SubmissionJudgeResult result = SubmissionJudgeResult.builder()
                    .submission(submission)
                    .judgeRunId(submission.getJudgeRunId())
                    .testCaseNumber(testCaseNumber)
                    .verdict(Verdict.INTERNAL_ERROR)
                    .executionTime(0)
                    .memoryUsage(0)
                    .judge0StatusDescription(Judge0AuditUtil.safeStatusDescription(statusDescription))
                    .diagnostic(Judge0AuditUtil.firstSafeDiagnostic(
                            cause.getClass().getSimpleName() + ": " + cause.getMessage()
                    ))
                    .build();
            judgeResultRepository.save(result);
        }

        submissionRepository.save(submission);
        publishFinalized(submission, statusDescription, cause);
    }

    private void publishFinalized(Submission submission, String reason, RuntimeException cause) {
        try {
            submissionSsePublisher.publish(SubmissionStreamEventType.FINALIZED, submission);
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to publish INTERNAL_ERROR submission event. submissionId={} cause={}: {}",
                    submission.getId(),
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
        }
        eventPublisher.publishEvent(new SubmissionFinalizedEvent(
                submission.getId(),
                submission.getContest().getId(),
                submission.getProblem().getId(),
                submission.getUser().getId(),
                submission.getVerdict()
        ));
        log.error(
                "Submission marked INTERNAL_ERROR during judge dispatch. submissionId={} judgeRunId={} reason={}",
                submission.getId(),
                submission.getJudgeRunId(),
                reason,
                cause
        );
    }
}
