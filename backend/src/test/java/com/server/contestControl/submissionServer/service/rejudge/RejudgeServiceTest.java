package com.server.contestControl.submissionServer.service.rejudge;

import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.submissionServer.dto.RejudgeResponse;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.exceptions.InvalidRejudgeRequestException;
import com.server.contestControl.submissionServer.queue.submission.SubmissionProducer;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private RejudgeService rejudgeService;

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

    private Submission submission(Long id, Verdict verdict) {
        return Submission.builder()
                .id(id)
                .verdict(verdict)
                .executionTime(1)
                .memoryUsage(1)
                .judgeRunId(0L)
                .build();
    }
}
