package com.server.contestControl.submissionServer.service.rejudge;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.submissionServer.dto.RejudgeResponse;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.exceptions.InvalidRejudgeRequestException;
import com.server.contestControl.submissionServer.queue.submission.SubmissionProducer;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.sse.SubmissionSsePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RejudgeServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private SubmissionProducer submissionProducer;

    @Mock
    private SubmissionSsePublisher submissionSsePublisher;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RejudgeService rejudgeService;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejudgeSelectedSubmissionsQueuesOnlyFinalVerdicts() {
        Submission acceptedSubmission = submission(1L, Verdict.ACCEPTED);
        acceptedSubmission.setExecutionTime(123);
        acceptedSubmission.setMemoryUsage(456);

        Submission runningSubmission = submission(2L, Verdict.RUNNING);

        when(submissionRepository.findAllById(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(acceptedSubmission, runningSubmission));

        RejudgeResponse response = rejudgeService.rejudgeSelectedSubmissions(List.of(1L, 2L, 3L));

        assertThat(acceptedSubmission.getVerdict()).isEqualTo(Verdict.PENDING_REJUDGE);
        assertThat(acceptedSubmission.getExecutionTime()).isNull();
        assertThat(acceptedSubmission.getMemoryUsage()).isNull();
        assertThat(response.queuedSubmissionIds()).containsExactly(1L);
        assertThat(response.skippedSubmissionIds()).containsExactly(2L);
        assertThat(response.missingSubmissionIds()).containsExactly(3L);

        verify(submissionRepository).saveAll(List.of(acceptedSubmission));
        verify(submissionProducer).sendSubmission(1L);
        verify(submissionProducer, never()).sendSubmission(2L);
    }

    @Test
    void rejudgeSelectedSubmissionsRejectsEmptyRequest() {
        assertThatThrownBy(() -> rejudgeService.rejudgeSelectedSubmissions(List.of()))
                .isInstanceOf(InvalidRejudgeRequestException.class);

        verify(submissionRepository, never()).findAllById(List.of());
    }

    @Test
    void rejudgeProblemUsesProblemScope() {
        Submission wrongAnswerSubmission = submission(11L, Verdict.WRONG_ANSWER);

        when(problemRepository.existsById(7L)).thenReturn(true);
        when(submissionRepository.findAllByProblem_Id(7L)).thenReturn(List.of(wrongAnswerSubmission));

        RejudgeResponse response = rejudgeService.rejudgeProblem(7L);

        assertThat(response.scope()).isEqualTo("PROBLEM");
        assertThat(response.scopeId()).isEqualTo(7L);
        assertThat(response.queuedSubmissionIds()).containsExactly(11L);
        assertThat(wrongAnswerSubmission.getVerdict()).isEqualTo(Verdict.PENDING_REJUDGE);

        verify(submissionProducer).sendSubmission(11L);
    }

    @Test
    void rejudgeContestRequiresContestScope() {
        Submission runtimeErrorSubmission = submission(21L, Verdict.RUNTIME_ERROR);

        when(contestRepository.existsById(5L)).thenReturn(true);
        when(submissionRepository.findAllByContest_Id(5L)).thenReturn(List.of(runtimeErrorSubmission));

        RejudgeResponse response = rejudgeService.rejudgeContest(5L);

        assertThat(response.scope()).isEqualTo("CONTEST");
        assertThat(response.scopeId()).isEqualTo(5L);
        assertThat(response.queuedSubmissionIds()).containsExactly(21L);

        verify(submissionProducer).sendSubmission(21L);
    }

    // ─── Test 1: Normal rejudge still skips active submissions ─────────────────

    @Test
    void normalRejudgeStillSkipsActiveSubmissions() {
        Submission pending = submission(1L, Verdict.PENDING);
        Submission pendingRejudge = submission(2L, Verdict.PENDING_REJUDGE);
        Submission running = submission(3L, Verdict.RUNNING);
        Submission accepted = submission(4L, Verdict.ACCEPTED);
        Submission wrongAnswer = submission(5L, Verdict.WRONG_ANSWER);

        when(submissionRepository.findAllById(List.of(1L, 2L, 3L, 4L, 5L)))
                .thenReturn(List.of(pending, pendingRejudge, running, accepted, wrongAnswer));

        RejudgeResponse response = rejudgeService.rejudgeSelectedSubmissions(
                List.of(1L, 2L, 3L, 4L, 5L)
        );

        assertThat(response.queuedSubmissionIds()).containsExactly(4L, 5L);
        assertThat(response.skippedSubmissionIds()).containsExactly(1L, 2L, 3L);

        verify(submissionProducer).sendSubmission(4L);
        verify(submissionProducer).sendSubmission(5L);
        verify(submissionProducer, never()).sendSubmission(1L);
        verify(submissionProducer, never()).sendSubmission(2L);
        verify(submissionProducer, never()).sendSubmission(3L);
    }

    // ─── Test 2: Force rejudge queues active AND final submissions ───────────

    @Test
    void forceRejudgeQueuesActiveAndFinalSubmissions() {
        Submission pending = submission(1L, Verdict.PENDING);
        Submission pendingRejudge = submission(2L, Verdict.PENDING_REJUDGE);
        Submission running = submission(3L, Verdict.RUNNING);
        Submission accepted = submission(4L, Verdict.ACCEPTED);

        when(submissionRepository.findAllById(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(pending, pendingRejudge, running, accepted));

        RejudgeResponse response = rejudgeService.forceRejudgeSelectedSubmissions(
                List.of(1L, 2L, 3L, 4L)
        );

        assertThat(response.queuedSubmissionIds()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(response.skippedSubmissionIds()).isEmpty();
        assertThat(response.scope()).isEqualTo("FORCE_SUBMISSIONS");

        // All should be marked PENDING_REJUDGE
        assertThat(pending.getVerdict()).isEqualTo(Verdict.PENDING_REJUDGE);
        assertThat(pendingRejudge.getVerdict()).isEqualTo(Verdict.PENDING_REJUDGE);
        assertThat(running.getVerdict()).isEqualTo(Verdict.PENDING_REJUDGE);
        assertThat(accepted.getVerdict()).isEqualTo(Verdict.PENDING_REJUDGE);

        verify(submissionProducer).sendSubmission(1L);
        verify(submissionProducer).sendSubmission(2L);
        verify(submissionProducer).sendSubmission(3L);
        verify(submissionProducer).sendSubmission(4L);
    }

    // ─── Test 3: Force rejudge advances judgeRunId immediately ───────────────

    @Test
    void forceRejudgeAdvancesJudgeRunIdImmediately() {
        Submission running = Submission.builder()
                .id(10L)
                .contest(Contest.builder().id(5L).build())
                .problem(Problem.builder().id(7L).build())
                .user(User.builder().id(9L).username("team9").role(Role.TEAM).build())
                .verdict(Verdict.RUNNING)
                .executionTime(200)
                .memoryUsage(512)
                .judgeRunId(5L)
                .build();

        when(submissionRepository.findAllById(List.of(10L)))
                .thenReturn(List.of(running));

        rejudgeService.forceRejudgeSelectedSubmissions(List.of(10L));

        assertThat(running.getVerdict()).isEqualTo(Verdict.PENDING_REJUDGE);
        assertThat(running.getJudgeRunId()).isEqualTo(6L);
        assertThat(running.getExecutionTime()).isNull();
        assertThat(running.getMemoryUsage()).isNull();
    }

    @Test
    void rejudgePublishAfterCommitBehaviorIsPreserved() {
        TransactionSynchronizationManager.initSynchronization();
        Submission accepted = submission(1L, Verdict.ACCEPTED);

        when(submissionRepository.findAllById(List.of(1L))).thenReturn(List.of(accepted));

        rejudgeService.rejudgeSelectedSubmissions(List.of(1L));

        verify(submissionProducer, never()).sendSubmission(1L);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(submissionProducer).sendSubmission(1L);
    }

    private Submission submission(Long id, Verdict verdict) {
        return Submission.builder()
                .id(id)
                .contest(Contest.builder().id(5L).build())
                .problem(Problem.builder().id(7L).build())
                .user(User.builder().id(9L).username("team9").role(Role.TEAM).build())
                .verdict(verdict)
                .executionTime(1)
                .memoryUsage(1)
                .judgeRunId(0L)
                .build();
    }
}
