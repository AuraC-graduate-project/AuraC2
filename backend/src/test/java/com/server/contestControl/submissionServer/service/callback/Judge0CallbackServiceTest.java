package com.server.contestControl.submissionServer.service.callback;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.submissionServer.dto.Judge0Response;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.entity.SubmissionJudgeResult;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.repository.SubmissionJudgeResultRepository;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.sse.SubmissionSsePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Judge0CallbackServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private SubmissionJudgeResultRepository judgeResultRepository;

    @Mock
    private SubmissionSsePublisher submissionSsePublisher;

    @InjectMocks
    private Judge0CallbackService callbackService;

    @Test
    void callbackFinalizesOnlyAfterAllTestCasesArrive() {
        Submission submission = runningSubmission();

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(2);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 2))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(1L);

        callbackService.handleJudge0Callback(1L, 7L, 2, judge0Response(3));

        assertThat(submission.getVerdict()).isEqualTo(Verdict.RUNNING);
        verify(judgeResultRepository).save(any(SubmissionJudgeResult.class));
        verify(submissionRepository, never()).save(submission);
    }

    @Test
    void finalVerdictUsesFirstFailingTestCaseByTestCaseNumber() {
        Submission submission = runningSubmission();

        SubmissionJudgeResult acceptedSecondCase = result(submission, 2, Verdict.ACCEPTED);
        SubmissionJudgeResult wrongFirstCase = result(submission, 1, Verdict.WRONG_ANSWER);

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(2);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 2))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 7L))
                .thenReturn(1L)
                .thenReturn(2L);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunId(1L, 7L))
                .thenReturn(List.of(acceptedSecondCase, wrongFirstCase));

        callbackService.handleJudge0Callback(1L, 7L, 2, judge0Response(3));
        callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(4));

        assertThat(submission.getVerdict()).isEqualTo(Verdict.WRONG_ANSWER);
        verify(judgeResultRepository, times(2)).save(any(SubmissionJudgeResult.class));
        verify(submissionRepository).save(submission);
    }

    @Test
    void staleCallbackFromOlderRunIsIgnored() {
        Submission submission = runningSubmission();

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));

        callbackService.handleJudge0Callback(1L, 6L, 1, judge0Response(4));

        assertThat(submission.getVerdict()).isEqualTo(Verdict.RUNNING);
        verify(judgeResultRepository, never()).save(any());
        verify(submissionRepository, never()).save(submission);
    }

    // ─── Test 6: Stale callback after force rejudge is ignored ─────────────────

    @Test
    void staleCallbackAfterForceRejudgeIsIgnored() {
        // Simulate: submission was force-rejudged, judgeRunId advanced to 6
        // Old callback arrives with judgeRunId = 5
        Problem problem = Problem.builder().id(10L).build();
        Submission submission = Submission.builder()
                .id(1L)
                .problem(problem)
                .verdict(Verdict.RUNNING)
                .judgeRunId(6L)
                .build();

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));

        callbackService.handleJudge0Callback(1L, 5L, 1, judge0Response(3));

        // No result stored, submission untouched
        assertThat(submission.getVerdict()).isEqualTo(Verdict.RUNNING);
        verify(judgeResultRepository, never()).save(any());
        verify(submissionRepository, never()).save(submission);
    }

    // ─── Test 7: Current callback after force rejudge is accepted ────────────

    @Test
    void currentCallbackAfterForceRejudgeIsAccepted() {
        // Simulate: submission was force-rejudged with judgeRunId = 6
        // New callback arrives with matching judgeRunId = 6
        Problem problem = Problem.builder().id(10L).build();
        Submission submission = Submission.builder()
                .id(1L)
                .problem(problem)
                .verdict(Verdict.RUNNING)
                .judgeRunId(6L)
                .build();

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 6L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 6L)).thenReturn(1L);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunId(1L, 6L))
                .thenReturn(List.of(
                        SubmissionJudgeResult.builder()
                                .submission(submission)
                                .judgeRunId(6L)
                                .testCaseNumber(1)
                                .verdict(Verdict.ACCEPTED)
                                .executionTime(10)
                                .memoryUsage(100)
                                .build()
                ));

        callbackService.handleJudge0Callback(1L, 6L, 1, judge0Response(3));

        // Result should be stored and verdict finalized
        verify(judgeResultRepository).save(any(SubmissionJudgeResult.class));
        verify(submissionRepository).save(submission);
        assertThat(submission.getVerdict()).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void legacyCallbackWithoutJudgeRunIdIsAcceptedOnlyForLegacyRunZero() {
        Problem problem = Problem.builder().id(10L).build();
        Submission submission = Submission.builder()
                .id(1L)
                .problem(problem)
                .verdict(Verdict.RUNNING)
                .judgeRunId(0L)
                .build();

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 0L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 0L)).thenReturn(1L);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunId(1L, 0L))
                .thenReturn(List.of(
                        SubmissionJudgeResult.builder()
                                .submission(submission)
                                .judgeRunId(0L)
                                .testCaseNumber(1)
                                .verdict(Verdict.ACCEPTED)
                                .executionTime(10)
                                .memoryUsage(100)
                                .build()
                ));

        callbackService.handleJudge0Callback(1L, null, 1, judge0Response(3));

        verify(judgeResultRepository).save(any(SubmissionJudgeResult.class));
        verify(submissionRepository).save(submission);
        assertThat(submission.getVerdict()).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void legacyCallbackWithoutJudgeRunIdIsIgnoredForNonLegacyRuns() {
        Submission submission = runningSubmission();

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));

        callbackService.handleJudge0Callback(1L, null, 1, judge0Response(3));

        assertThat(submission.getVerdict()).isEqualTo(Verdict.RUNNING);
        verify(judgeResultRepository, never()).save(any());
        verify(submissionRepository, never()).save(submission);
    }

    private Submission runningSubmission() {
        Problem problem = Problem.builder()
                .id(10L)
                .build();

        return Submission.builder()
                .id(1L)
                .problem(problem)
                .verdict(Verdict.RUNNING)
                .judgeRunId(7L)
                .build();
    }

    private SubmissionJudgeResult result(Submission submission, int testCaseNumber, Verdict verdict) {
        return SubmissionJudgeResult.builder()
                .submission(submission)
                .judgeRunId(7L)
                .testCaseNumber(testCaseNumber)
                .verdict(verdict)
                .executionTime(testCaseNumber * 10)
                .memoryUsage(testCaseNumber * 100)
                .build();
    }

    private Judge0Response judge0Response(int statusId) {
        Judge0Response response = new Judge0Response();
        Judge0Response.Status status = new Judge0Response.Status();
        status.setId(statusId);
        response.setStatus(status);
        response.setTime("0.010");
        response.setMemory(1024);
        return response;
    }
}
