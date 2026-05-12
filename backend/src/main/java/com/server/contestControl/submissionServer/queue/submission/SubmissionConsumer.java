package com.server.contestControl.submissionServer.queue.submission;

import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.submissionServer.config.RabbitMQConfig;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.service.judge.Judge0Service;
import com.server.contestControl.submissionServer.sse.SubmissionSsePublisher;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

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

    @RabbitListener(queues = RabbitMQConfig.SUBMISSION_QUEUE)
    public void handleSubmission(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
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
        int languageId = convertLanguage(submission.getLanguage());

        if (submission.getVerdict() == Verdict.PENDING) {
            // Normal new submission: increment judgeRunId here
            Long nextJudgeRunId = submission.getJudgeRunId() == null ? 1L : submission.getJudgeRunId() + 1;
            submission.setJudgeRunId(nextJudgeRunId);
        }
        // PENDING_REJUDGE: judgeRunId was already reserved by RejudgeService — do NOT increment again

        submission.setVerdict(Verdict.RUNNING);
        submissionRepository.save(submission);

        // Publish RUNNING event directly — SubmissionConsumer is not transactional,
        // so the save above is immediately visible and we publish without delay.
        submissionSsePublisher.publish(SubmissionStreamEventType.RUNNING, submission);

        log.info(
                "Dispatching submission to Judge0. submissionId={} judgeRunId={} testCaseCount={}",
                submission.getId(),
                submission.getJudgeRunId(),
                testCases.size()
        );

        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            judge0Service.sendSingleTest(submission, tc, i + 1, languageId);
        }
    }
}
