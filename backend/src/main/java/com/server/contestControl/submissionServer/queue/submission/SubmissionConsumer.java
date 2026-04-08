package com.server.contestControl.submissionServer.queue.submission;

import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.submissionServer.config.RabbitMQConfig;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.service.judge.Judge0Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static com.server.contestControl.submissionServer.util.LanguageMapper.convertLanguage;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionConsumer {

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final Judge0Service judge0Service;

    @RabbitListener(queues = RabbitMQConfig.SUBMISSION_QUEUE)
    public void handleSubmission(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        List<TestCase> testCases = testCaseRepository.findByProblemId(submission.getProblem().getId());
        int languageId = convertLanguage(submission.getLanguage());

        submission.setVerdict(Verdict.RUNNING);
        submissionRepository.save(submission);

        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            judge0Service.sendSingleTest(submission, tc, i + 1, languageId);
        }
    }
}