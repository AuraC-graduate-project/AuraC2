package com.server.contestControl.submissionServer.queue.submission;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.event.SubmissionFinalizedEvent;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.service.judge.Judge0Service;
import com.server.contestControl.submissionServer.sse.SubmissionSsePublisher;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionConsumerTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private Judge0Service judge0Service;

    @Mock
    private SubmissionSsePublisher submissionSsePublisher;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SubmissionConsumer submissionConsumer;

    // ─── Test 4: Consumer does NOT double-increment judgeRunId for PENDING_REJUDGE

    @Test
    void consumerDoesNotDoubleIncrementPendingRejudge() {
        Problem problem = Problem.builder().id(10L).build();
        Submission submission = Submission.builder()
                .id(1L)
                .problem(problem)
                .verdict(Verdict.PENDING_REJUDGE)
                .judgeRunId(6L)
                .language("java")
                .code("class Main {}")
                .build();

        TestCase tc = TestCase.builder()
                .id(100L)
                .inputData("1")
                .expectedOutput("1")
                .build();

        when(submissionRepository.findByIdWithContestProblemUser(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.findByProblemId(10L)).thenReturn(List.of(tc));

        submissionConsumer.handleSubmission(1L);

        // judgeRunId must remain 6, NOT become 7
        assertThat(submission.getJudgeRunId()).isEqualTo(6L);
        assertThat(submission.getVerdict()).isEqualTo(Verdict.RUNNING);
        verify(submissionRepository).save(submission);
        verify(submissionSsePublisher).publish(eq(SubmissionStreamEventType.RUNNING), eq(submission));
    }

    // ─── Test 5: Consumer still increments judgeRunId for normal PENDING submission

    @Test
    void consumerStillIncrementsNormalPendingSubmission() {
        Problem problem = Problem.builder().id(10L).build();
        Submission submission = Submission.builder()
                .id(2L)
                .problem(problem)
                .verdict(Verdict.PENDING)
                .judgeRunId(0L)
                .language("java")
                .code("class Main {}")
                .build();

        TestCase tc = TestCase.builder()
                .id(101L)
                .inputData("2")
                .expectedOutput("2")
                .build();

        when(submissionRepository.findByIdWithContestProblemUser(2L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.findByProblemId(10L)).thenReturn(List.of(tc));

        submissionConsumer.handleSubmission(2L);

        // judgeRunId must be incremented from 0 to 1
        assertThat(submission.getJudgeRunId()).isEqualTo(1L);
        assertThat(submission.getVerdict()).isEqualTo(Verdict.RUNNING);
        verify(submissionRepository).save(submission);
        verify(submissionSsePublisher).publish(eq(SubmissionStreamEventType.RUNNING), eq(submission));
    }

    @Test
    void consumerSkipsStaleOrDuplicateMessages() {
        Problem problem = Problem.builder().id(10L).build();
        Submission submission = Submission.builder()
                .id(3L)
                .problem(problem)
                .verdict(Verdict.ACCEPTED)
                .judgeRunId(5L)
                .language("java")
                .code("class Main {}")
                .build();

        when(submissionRepository.findByIdWithContestProblemUser(3L)).thenReturn(Optional.of(submission));

        submissionConsumer.handleSubmission(3L);

        // Should not dispatch since verdict is ACCEPTED (not PENDING or PENDING_REJUDGE)
        verify(submissionRepository, never()).save(any());
        verify(judge0Service, never()).sendSingleTest(any(), any(), anyInt(), anyInt());
    }

    @Test
    void consumerStillDispatchesToJudgeWhenRunningEventPublishFails() {
        Problem problem = Problem.builder().id(10L).build();
        Submission submission = Submission.builder()
                .id(4L)
                .problem(problem)
                .verdict(Verdict.PENDING)
                .judgeRunId(0L)
                .language("java")
                .code("class Main {}")
                .build();

        TestCase tc = TestCase.builder()
                .id(102L)
                .inputData("3")
                .expectedOutput("3")
                .build();

        when(submissionRepository.findByIdWithContestProblemUser(4L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.findByProblemId(10L)).thenReturn(List.of(tc));
        doThrow(new RuntimeException("sse failed"))
                .when(submissionSsePublisher)
                .publish(eq(SubmissionStreamEventType.RUNNING), eq(submission));

        submissionConsumer.handleSubmission(4L);

        assertThat(submission.getVerdict()).isEqualTo(Verdict.RUNNING);
        verify(submissionRepository).save(submission);
        verify(judge0Service).sendSingleTest(eq(submission), eq(tc), eq(1), anyInt());
    }

    @Test
    void consumerMarksZeroTestCaseProblemInternalErrorAndPublishesFinalEvent() {
        Contest contest = Contest.builder().id(20L).build();
        Problem problem = Problem.builder().id(10L).contest(contest).build();
        User team = User.builder().id(30L).username("team30").role(Role.TEAM).build();
        Submission submission = Submission.builder()
                .id(5L)
                .contest(contest)
                .problem(problem)
                .user(team)
                .verdict(Verdict.PENDING)
                .judgeRunId(0L)
                .language("java")
                .code("class Main {}")
                .build();

        when(submissionRepository.findByIdWithContestProblemUser(5L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.findByProblemId(10L)).thenReturn(List.of());

        submissionConsumer.handleSubmission(5L);

        assertThat(submission.getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        verify(submissionRepository).save(submission);
        verify(submissionSsePublisher).publish(eq(SubmissionStreamEventType.FINALIZED), eq(submission));
        verify(eventPublisher).publishEvent(any(SubmissionFinalizedEvent.class));
        verify(judge0Service, never()).sendSingleTest(any(), any(), anyInt(), anyInt());
    }
}
