package com.server.contestControl.submissionServer.service.callback;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.ValidationMode;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.submissionServer.dto.Judge0Response;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.entity.SubmissionJudgeResult;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.repository.SubmissionJudgeResultRepository;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.service.compare.OutputComparator;
import com.server.contestControl.submissionServer.service.validator.CustomValidatorService;
import com.server.contestControl.submissionServer.sse.SubmissionSsePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private OutputComparator outputComparator = new OutputComparator();

    @Mock
    private CustomValidatorService customValidatorService;

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
                .contest(contest())
                .problem(problem)
                .user(team())
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
                .contest(contest())
                .problem(problem)
                .user(team())
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
                .contest(contest())
                .problem(problem)
                .user(team())
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

    @Test
    void nonTerminalJudge0StatusDoesNotStoreOrFinalize() {
        Submission submission = runningSubmission();

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);

        ResponseEntity<?> response = callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(1));

        assertThat(response.getBody()).isEqualTo("Non-terminal callback ignored");
        assertThat(submission.getVerdict()).isEqualTo(Verdict.RUNNING);
        verify(judgeResultRepository, never()).save(any());
        verify(submissionRepository, never()).save(submission);
    }

    @Test
    void unknownJudge0StatusMapsToInternalErrorAndStoresAuditDetails() {
        Submission submission = runningSubmission();
        SubmissionJudgeResult internalResult = result(submission, 1, Verdict.INTERNAL_ERROR);
        internalResult.setJudge0StatusId(999);
        internalResult.setJudge0StatusDescription("Mystery Status");
        internalResult.setDiagnostic("compiler exploded");

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(1L);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunId(1L, 7L))
                .thenReturn(List.of(internalResult));

        Judge0Response judge0Response = judge0Response(999, "Mystery Status");
        judge0Response.setCompileOutput("compiler exploded");
        callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response);

        ArgumentCaptor<SubmissionJudgeResult> resultCaptor =
                ArgumentCaptor.forClass(SubmissionJudgeResult.class);
        verify(judgeResultRepository).save(resultCaptor.capture());
        SubmissionJudgeResult saved = resultCaptor.getValue();
        assertThat(saved.getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(saved.getJudge0StatusId()).isEqualTo(999);
        assertThat(saved.getJudge0StatusDescription()).isEqualTo("Mystery Status");
        assertThat(saved.getDiagnostic()).isEqualTo("compiler exploded");
        assertThat(submission.getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
    }

    @Test
    void tokenNormalizedPolicyComparesStdoutOnBackendAndAccepts() {
        Submission submission = runningSubmission(ComparePolicy.TOKEN_NORMALIZED);
        TestCase testCase = TestCase.builder()
                .id(100L)
                .expectedOutput("1 2 3")
                .build();
        SubmissionJudgeResult acceptedResult = result(submission, 1, Verdict.ACCEPTED);

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(testCaseRepository.findByProblemIdOrderByIdAsc(10L)).thenReturn(List.of(testCase));
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(1L);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(List.of(acceptedResult));

        callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(3, "Accepted", "1\n2\t3"));

        ArgumentCaptor<SubmissionJudgeResult> resultCaptor =
                ArgumentCaptor.forClass(SubmissionJudgeResult.class);
        verify(judgeResultRepository).save(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getVerdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(resultCaptor.getValue().getDiagnostic()).isNull();
        assertThat(submission.getVerdict()).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void normalizedPolicyMismatchMapsSuccessfulExecutionToWrongAnswer() {
        Submission submission = runningSubmission(ComparePolicy.NORMALIZED_TEXT);
        TestCase testCase = TestCase.builder()
                .id(100L)
                .expectedOutput("expected")
                .build();
        SubmissionJudgeResult wrongAnswerResult = result(submission, 1, Verdict.WRONG_ANSWER);
        wrongAnswerResult.setDiagnostic("Output mismatch under NORMALIZED_TEXT compare policy");

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(testCaseRepository.findByProblemIdOrderByIdAsc(10L)).thenReturn(List.of(testCase));
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(1L);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(List.of(wrongAnswerResult));

        callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(3, "Accepted", "actual"));

        ArgumentCaptor<SubmissionJudgeResult> resultCaptor =
                ArgumentCaptor.forClass(SubmissionJudgeResult.class);
        verify(judgeResultRepository).save(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getVerdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(resultCaptor.getValue().getDiagnostic()).isEqualTo("Output mismatch under NORMALIZED_TEXT compare policy");
        assertThat(submission.getVerdict()).isEqualTo(Verdict.WRONG_ANSWER);
    }

    @Test
    void executionErrorsBypassBackendOutputComparison() {
        Submission submission = runningSubmission(ComparePolicy.TOKEN_NORMALIZED);
        SubmissionJudgeResult compileResult = result(submission, 1, Verdict.COMPILATION_ERROR);

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(1L);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(List.of(compileResult));

        callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(6, "Compilation Error", "matching output"));

        verify(outputComparator, never()).compare(any(), any(), any(), any(), any());
        verify(customValidatorService, never()).validate(any(), any(), any());
        assertThat(submission.getVerdict()).isEqualTo(Verdict.COMPILATION_ERROR);
    }

    @Test
    void customValidatorAcceptsAlternativeOutputAfterSuccessfulExecution() {
        Submission submission = runningSubmissionWithCustomValidator();
        TestCase testCase = TestCase.builder()
                .id(100L)
                .inputData("4")
                .expectedOutput("YES")
                .build();
        SubmissionJudgeResult acceptedResult = result(submission, 1, Verdict.ACCEPTED);

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(testCaseRepository.findByProblemIdOrderByIdAsc(10L)).thenReturn(List.of(testCase));
        when(customValidatorService.validate(submission.getProblem(), testCase, "Y\n"))
                .thenReturn(CustomValidatorService.ValidatorResult.accepted());
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(1L);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(List.of(acceptedResult));

        callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(3, "Accepted", "Y\n"));

        ArgumentCaptor<SubmissionJudgeResult> resultCaptor =
                ArgumentCaptor.forClass(SubmissionJudgeResult.class);
        verify(judgeResultRepository).save(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getVerdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(submission.getVerdict()).isEqualTo(Verdict.ACCEPTED);
        verify(outputComparator, never()).compare(any(), any(), any(), any(), any());
    }

    @Test
    void customValidatorRejectsInvalidOutputAsWrongAnswer() {
        Submission submission = runningSubmissionWithCustomValidator();
        TestCase testCase = TestCase.builder()
                .id(100L)
                .inputData("4")
                .expectedOutput("YES")
                .build();
        SubmissionJudgeResult wrongAnswerResult = result(submission, 1, Verdict.WRONG_ANSWER);
        wrongAnswerResult.setDiagnostic("Custom validator rejected output");

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(testCaseRepository.findByProblemIdOrderByIdAsc(10L)).thenReturn(List.of(testCase));
        when(customValidatorService.validate(submission.getProblem(), testCase, "NO\n"))
                .thenReturn(CustomValidatorService.ValidatorResult.wrongAnswer("Custom validator rejected output"));
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(1L);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(List.of(wrongAnswerResult));

        callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(3, "Accepted", "NO\n"));

        ArgumentCaptor<SubmissionJudgeResult> resultCaptor =
                ArgumentCaptor.forClass(SubmissionJudgeResult.class);
        verify(judgeResultRepository).save(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getVerdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(resultCaptor.getValue().getDiagnostic()).isEqualTo("Custom validator rejected output");
        assertThat(submission.getVerdict()).isEqualTo(Verdict.WRONG_ANSWER);
    }

    @Test
    void customValidatorFailureMapsSuccessfulExecutionToInternalError() {
        Submission submission = runningSubmissionWithCustomValidator();
        TestCase testCase = TestCase.builder()
                .id(100L)
                .inputData("4")
                .expectedOutput("YES")
                .build();
        SubmissionJudgeResult internalResult = result(submission, 1, Verdict.INTERNAL_ERROR);
        internalResult.setDiagnostic("Custom validator produced invalid decision");

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(testCaseRepository.findByProblemIdOrderByIdAsc(10L)).thenReturn(List.of(testCase));
        when(customValidatorService.validate(submission.getProblem(), testCase, "Y\n"))
                .thenReturn(CustomValidatorService.ValidatorResult.internalError("Custom validator produced invalid decision"));
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.countBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(1L);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunId(1L, 7L)).thenReturn(List.of(internalResult));

        callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(3, "Accepted", "Y\n"));

        ArgumentCaptor<SubmissionJudgeResult> resultCaptor =
                ArgumentCaptor.forClass(SubmissionJudgeResult.class);
        verify(judgeResultRepository).save(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(resultCaptor.getValue().getDiagnostic()).isEqualTo("Custom validator produced invalid decision");
        assertThat(submission.getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
    }

    @Test
    void callbackForAlreadyFinalizedSubmissionIsIgnored() {
        Submission submission = runningSubmission();
        submission.setVerdict(Verdict.INTERNAL_ERROR);

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));

        ResponseEntity<?> response = callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(3));

        assertThat(response.getBody()).isEqualTo("Submission is no longer running");
        verify(judgeResultRepository, never()).save(any());
        verify(submissionRepository, never()).save(submission);
    }

    @Test
    void duplicateCallbackForExistingResultIsIdempotent() {
        Submission submission = runningSubmission();
        SubmissionJudgeResult existingResult = result(submission, 1, Verdict.ACCEPTED);

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 1))
                .thenReturn(Optional.of(existingResult));

        ResponseEntity<?> response = callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(4));

        assertThat(response.getBody()).isEqualTo("Duplicate callback ignored");
        assertThat(submission.getVerdict()).isEqualTo(Verdict.RUNNING);
        verify(judgeResultRepository, never()).save(any(SubmissionJudgeResult.class));
        verify(submissionRepository, never()).save(submission);
    }

    @Test
    void duplicateCallbackUniqueConstraintRaceIsIdempotent() {
        Submission submission = runningSubmission();

        when(submissionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.countByProblemId(10L)).thenReturn(1);
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(1L, 7L, 1))
                .thenReturn(Optional.empty());
        when(judgeResultRepository.save(any(SubmissionJudgeResult.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        ResponseEntity<?> response = callbackService.handleJudge0Callback(1L, 7L, 1, judge0Response(3));

        assertThat(response.getBody()).isEqualTo("Duplicate callback ignored");
        assertThat(submission.getVerdict()).isEqualTo(Verdict.RUNNING);
        verify(submissionRepository, never()).save(submission);
    }

    private Submission runningSubmission() {
        return runningSubmission(null);
    }

    private Submission runningSubmission(ComparePolicy comparePolicy) {
        Problem problem = Problem.builder()
                .id(10L)
                .comparePolicy(comparePolicy)
                .build();

        return Submission.builder()
                .id(1L)
                .contest(contest())
                .problem(problem)
                .user(team())
                .verdict(Verdict.RUNNING)
                .judgeRunId(7L)
                .build();
    }

    private Submission runningSubmissionWithCustomValidator() {
        Problem problem = Problem.builder()
                .id(10L)
                .comparePolicy(ComparePolicy.EXACT)
                .validationMode(ValidationMode.CUSTOM_VALIDATOR)
                .validatorEnabled(true)
                .validatorLanguageId(71)
                .validatorSource("checker")
                .validatorSourceHash("a".repeat(64))
                .build();

        return Submission.builder()
                .id(1L)
                .contest(contest())
                .problem(problem)
                .user(team())
                .verdict(Verdict.RUNNING)
                .judgeRunId(7L)
                .build();
    }

    private Contest contest() {
        return Contest.builder().id(99L).build();
    }

    private User team() {
        return User.builder().id(42L).username("team42").role(Role.TEAM).build();
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
        return judge0Response(statusId, null);
    }

    private Judge0Response judge0Response(int statusId, String description) {
        return judge0Response(statusId, description, null);
    }

    private Judge0Response judge0Response(int statusId, String description, String stdout) {
        Judge0Response response = new Judge0Response();
        Judge0Response.Status status = new Judge0Response.Status();
        status.setId(statusId);
        status.setDescription(description);
        response.setStatus(status);
        response.setStdout(stdout);
        response.setTime("0.010");
        response.setMemory(1024);
        return response;
    }
}
