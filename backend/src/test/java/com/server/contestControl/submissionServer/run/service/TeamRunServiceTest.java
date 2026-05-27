package com.server.contestControl.submissionServer.run.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.moderation.service.ContestTeamModerationService;
import com.server.contestControl.contestServer.oracle.entity.InputValidator;
import com.server.contestControl.contestServer.oracle.repository.InputValidatorRepository;
import com.server.contestControl.contestServer.oracle.service.OracleJudge0ExecutionService;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.contestServer.service.ContestService;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.run.dto.RunRequest;
import com.server.contestControl.submissionServer.run.dto.RunResponse;
import com.server.contestControl.submissionServer.run.dto.UpdateCustomTestCaseRequest;
import com.server.contestControl.submissionServer.run.entity.UserCustomTestCase;
import com.server.contestControl.submissionServer.run.exception.CustomTestCaseNotFoundException;
import com.server.contestControl.submissionServer.run.exception.RunRequestException;
import com.server.contestControl.submissionServer.run.repository.UserCustomTestCaseRepository;
import com.server.contestControl.submissionServer.service.compare.OutputComparator;
import com.server.contestControl.submissionServer.service.judge.Judge0ExpectedOutputPolicy;
import com.server.contestControl.submissionServer.service.validator.CustomValidatorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamRunServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ContestService contestService;
    @Mock private ProblemRepository problemRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private UserCustomTestCaseRepository customTestCaseRepository;
    @Mock private InputValidatorRepository inputValidatorRepository;
    @Mock private OracleJudge0ExecutionService judge0ExecutionService;
    @Spy private OutputComparator outputComparator = new OutputComparator();
    @Spy private Judge0ExpectedOutputPolicy expectedOutputPolicy = new Judge0ExpectedOutputPolicy();
    @Mock private CustomValidatorService customValidatorService;
    @Mock private ContestTeamModerationService moderationService;

    @InjectMocks
    private TeamRunService teamRunService;

    @Test
    void runUsesPublicSamplesOnlyAndDoesNotFetchHiddenTests() {
        Problem problem = problem();
        TestCase publicSample = sample(100L, problem, "1 2\n", "3\n", true);
        stubContext(problem);
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(5L)).thenReturn(List.of(publicSample));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(5L))
                .thenReturn(Optional.empty());
        when(judge0ExecutionService.run(eq("source"), eq(54), eq("1 2\n"), eq("3\n"), eq(1.0), eq(131072)))
                .thenReturn(execution(Verdict.ACCEPTED, "3\n"));

        RunResponse response = teamRunService.run(5L, runRequest(true, List.of(), "source"), "team1");

        assertThat(response.scoring()).isFalse();
        assertThat(response.publicSampleCount()).isEqualTo(1);
        assertThat(response.results()).singleElement()
                .satisfies(result -> {
                    assertThat(result.caseType()).isEqualTo("PUBLIC_SAMPLE");
                    assertThat(result.status()).isEqualTo("PASSED");
                    assertThat(result.expectedOutput()).isEqualTo("3\n");
                });
        verify(testCaseRepository, never()).findByProblemIdOrderByIdAsc(any());
        verify(outputComparator, never()).compare(any(), any(), any(), any(), any());
    }

    @Test
    void runCustomTestWithExpectedOutputComparesAgainstUserProvidedExpected() {
        Problem problem = problem();
        UserCustomTestCase customTest = customTest(problem, "4 5\n", "9\n");
        stubContext(problem);
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(5L))
                .thenReturn(Optional.empty());
        when(customTestCaseRepository.findByIdInAndContest_IdAndProblem_IdAndOwner_IdOrderByCreatedAtAscIdAsc(
                argThat(ids -> ids.size() == 1 && ids.contains(20L)), eq(7L), eq(5L), eq(1L)
        )).thenReturn(List.of(customTest));
        when(judge0ExecutionService.run(eq("source"), eq(54), eq("4 5\n"), eq("9\n"), eq(1.0), eq(131072)))
                .thenReturn(execution(Verdict.ACCEPTED, "9\n"));

        RunResponse response = teamRunService.run(5L, runRequest(false, List.of(20L), "source"), "team1");

        assertThat(response.customTestCount()).isEqualTo(1);
        assertThat(response.results()).singleElement()
                .satisfies(result -> {
                    assertThat(result.caseType()).isEqualTo("CUSTOM");
                    assertThat(result.status()).isEqualTo("PASSED");
                    assertThat(result.expectedOutput()).isEqualTo("9\n");
                });
    }

    @Test
    void exactPublicSampleExpectedFourAndActualFourReturnsPassed() {
        Problem problem = problem();
        TestCase publicSample = sample(100L, problem, "3 1", "4", true);
        stubContext(problem);
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(5L)).thenReturn(List.of(publicSample));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(5L))
                .thenReturn(Optional.empty());
        when(judge0ExecutionService.run(eq("source"), eq(54), eq("3 1"), eq("4"), eq(1.0), eq(131072)))
                .thenReturn(execution(Verdict.ACCEPTED, "4"));

        RunResponse response = teamRunService.run(5L, runRequest(true, List.of(), "source"), "team1");

        assertThat(response.results()).singleElement()
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo("PASSED");
                    assertThat(result.expectedOutput()).isEqualTo("4");
                    assertThat(result.actualOutput()).isEqualTo("4");
                });
        verify(outputComparator, never()).compare(any(), any(), any(), any(), any());
    }

    @Test
    void exactPublicSampleExpectedAndActualWithNewlineReturnsPassed() {
        Problem problem = problem();
        TestCase publicSample = sample(100L, problem, "3 1\n", "4\n", true);
        stubContext(problem);
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(5L)).thenReturn(List.of(publicSample));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(5L))
                .thenReturn(Optional.empty());
        when(judge0ExecutionService.run(eq("source"), eq(54), eq("3 1\n"), eq("4\n"), eq(1.0), eq(131072)))
                .thenReturn(execution(Verdict.ACCEPTED, "4\n"));

        RunResponse response = teamRunService.run(5L, runRequest(true, List.of(), "source"), "team1");

        assertThat(response.results()).singleElement()
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo("PASSED");
                    assertThat(result.expectedOutput()).isEqualTo("4\n");
                    assertThat(result.actualOutput()).isEqualTo("4\n");
                });
        verify(outputComparator, never()).compare(any(), any(), any(), any(), any());
    }

    @Test
    void exactPublicSampleFinalNewlineBehaviorFollowsJudge0ExpectedOutputPath() {
        Problem problem = problem();
        TestCase publicSample = sample(100L, problem, "3 1", "4", true);
        stubContext(problem);
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(5L)).thenReturn(List.of(publicSample));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(5L))
                .thenReturn(Optional.empty());
        when(judge0ExecutionService.run(eq("source"), eq(54), eq("3 1"), eq("4"), eq(1.0), eq(131072)))
                .thenReturn(execution(Verdict.ACCEPTED, "4\n"));

        RunResponse response = teamRunService.run(5L, runRequest(true, List.of(), "source"), "team1");

        assertThat(response.results()).singleElement()
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo("PASSED");
                    assertThat(result.expectedOutput()).isEqualTo("4");
                    assertThat(result.actualOutput()).isEqualTo("4\n");
                });
        verify(outputComparator, never()).compare(any(), any(), any(), any(), any());
    }

    @Test
    void exactPublicSampleWrongAnswerFollowsJudge0Verdict() {
        Problem problem = problem();
        TestCase publicSample = sample(100L, problem, "3 1", "4", true);
        stubContext(problem);
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(5L)).thenReturn(List.of(publicSample));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(5L))
                .thenReturn(Optional.empty());
        when(judge0ExecutionService.run(eq("source"), eq(54), eq("3 1"), eq("4"), eq(1.0), eq(131072)))
                .thenReturn(execution(Verdict.WRONG_ANSWER, "5"));

        RunResponse response = teamRunService.run(5L, runRequest(true, List.of(), "source"), "team1");

        assertThat(response.results()).singleElement()
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo("WRONG_ANSWER");
                    assertThat(result.expectedOutput()).isEqualTo("4");
                    assertThat(result.actualOutput()).isEqualTo("5");
                    assertThat(result.diagnostic()).isEqualTo("Output mismatch under EXACT compare policy");
                });
        verify(outputComparator, never()).compare(any(), any(), any(), any(), any());
    }

    @Test
    void nonExactPublicSampleUsesSharedOutputComparatorAfterExecution() {
        Problem problem = problem(ComparePolicy.TOKEN_NORMALIZED);
        TestCase publicSample = sample(100L, problem, "1\n", "1 2 3", true);
        stubContext(problem);
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(5L)).thenReturn(List.of(publicSample));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(5L))
                .thenReturn(Optional.empty());
        when(judge0ExecutionService.run(eq("source"), eq(54), eq("1\n"), isNull(), eq(1.0), eq(131072)))
                .thenReturn(execution(Verdict.ACCEPTED, "1\n2\t3"));

        RunResponse response = teamRunService.run(5L, runRequest(true, List.of(), "source"), "team1");

        assertThat(response.results()).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("PASSED"));
        verify(outputComparator).compare(
                eq(ComparePolicy.TOKEN_NORMALIZED),
                eq("1 2 3"),
                eq("1\n2\t3"),
                any(),
                any()
        );
    }

    @Test
    void runCustomTestWithoutExpectedOutputShowsProgramOutputWithoutPassFail() {
        Problem problem = problem();
        UserCustomTestCase customTest = customTest(problem, "hello\n", null);
        stubContext(problem);
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(5L))
                .thenReturn(Optional.empty());
        when(customTestCaseRepository.findByIdInAndContest_IdAndProblem_IdAndOwner_IdOrderByCreatedAtAscIdAsc(
                argThat(ids -> ids.size() == 1 && ids.contains(20L)), eq(7L), eq(5L), eq(1L)
        )).thenReturn(List.of(customTest));
        when(judge0ExecutionService.run(eq("source"), eq(54), eq("hello\n"), isNull(), eq(1.0), eq(131072)))
                .thenReturn(execution(Verdict.ACCEPTED, "world\n"));

        RunResponse response = teamRunService.run(5L, runRequest(false, List.of(20L), "source"), "team1");

        assertThat(response.results()).singleElement()
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo("RUN_COMPLETED");
                    assertThat(result.expectedOutput()).isNull();
                    assertThat(result.actualOutput()).isEqualTo("world\n");
                });
    }

    @Test
    void deleteCustomTestIsScopedToCurrentOwner() {
        Problem problem = problem();
        stubContext(problem);
        when(customTestCaseRepository.findByIdAndContest_IdAndProblem_IdAndOwner_Id(20L, 7L, 5L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamRunService.deleteCustomTest(5L, 20L, "team1"))
                .isInstanceOf(CustomTestCaseNotFoundException.class);

        verify(customTestCaseRepository, never()).delete(any());
    }

    @Test
    void updateCustomTestIsScopedToCurrentOwner() {
        Problem problem = problem();
        UserCustomTestCase customTest = customTest(problem, "old\n", null);
        stubContext(problem);
        when(customTestCaseRepository.findByIdAndContest_IdAndProblem_IdAndOwner_Id(20L, 7L, 5L, 1L))
                .thenReturn(Optional.of(customTest));

        var response = teamRunService.updateCustomTest(
                5L,
                20L,
                new UpdateCustomTestCaseRequest("new input\n", "new expected\n"),
                "team1"
        );

        assertThat(response.input()).isEqualTo("new input\n");
        assertThat(response.expectedOutput()).isEqualTo("new expected\n");
        assertThat(customTest.getInputData()).isEqualTo("new input\n");
        assertThat(customTest.getExpectedOutput()).isEqualTo("new expected\n");
    }

    @Test
    void invalidCustomInputIsRejectedBeforeUserCodeRuns() {
        Problem problem = problem();
        InputValidator validator = InputValidator.builder()
                .id(30L)
                .problem(problem)
                .languageId(54)
                .source("validator")
                .active(true)
                .build();
        UserCustomTestCase customTest = customTest(problem, "bad\n", "ok\n");
        stubContext(problem);
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(5L))
                .thenReturn(Optional.of(validator));
        when(customTestCaseRepository.findByIdInAndContest_IdAndProblem_IdAndOwner_IdOrderByCreatedAtAscIdAsc(
                argThat(ids -> ids.size() == 1 && ids.contains(20L)), eq(7L), eq(5L), eq(1L)
        )).thenReturn(List.of(customTest));
        when(judge0ExecutionService.run("validator", 54, "bad\n"))
                .thenReturn(execution(Verdict.ACCEPTED, "REJECT\n"));

        RunResponse response = teamRunService.run(5L, runRequest(false, List.of(20L), "source"), "team1");

        assertThat(response.results()).singleElement()
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo("VALIDATION_ERROR");
                    assertThat(result.actualOutput()).isNull();
                });
        verify(judge0ExecutionService, never()).run(eq("source"), eq(54), eq("bad\n"), any(), any(), any());
    }

    @Test
    void compilationErrorReturnsGlobalCompileOutputAndSkipsCases() {
        Problem problem = problem();
        TestCase publicSample = sample(100L, problem, "1 2\n", "3\n", true);
        UserCustomTestCase customTest = customTest(problem, "4 5\n", "9\n");
        stubContext(problem);
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(5L)).thenReturn(List.of(publicSample));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(5L))
                .thenReturn(Optional.empty());
        when(customTestCaseRepository.findByIdInAndContest_IdAndProblem_IdAndOwner_IdOrderByCreatedAtAscIdAsc(
                argThat(ids -> ids.size() == 1 && ids.contains(20L)), eq(7L), eq(5L), eq(1L)
        )).thenReturn(List.of(customTest));
        when(judge0ExecutionService.run(eq("source"), eq(54), eq("1 2\n"), eq("3\n"), eq(1.0), eq(131072)))
                .thenReturn(execution(Verdict.COMPILATION_ERROR, "", "Main.cpp: expected ';'"));

        RunResponse response = teamRunService.run(5L, runRequest(true, List.of(20L), "source"), "team1");

        assertThat(response.compileStatus()).isEqualTo("COMPILATION_ERROR");
        assertThat(response.compileOutput()).contains("expected");
        assertThat(response.results()).hasSize(2);
        assertThat(response.results()).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo("SKIPPED");
            assertThat(result.actualOutput()).isNull();
        });
        verify(judge0ExecutionService, never()).run(eq("source"), eq(54), eq("4 5\n"), any(), any(), any());
    }

    @Test
    void runDisabledTeamCannotExecuteRunWorkflow() {
        Problem problem = problem();
        stubContext(problem);
        doThrow(new RunRequestException("Run is disabled for your team."))
                .when(moderationService).assertRunAllowed(eq(problem.getContest()), any(User.class));

        assertThatThrownBy(() -> teamRunService.run(5L, runRequest(true, List.of(), "source"), "team1"))
                .isInstanceOf(RunRequestException.class)
                .hasMessageContaining("Run is disabled");

        verify(testCaseRepository, never()).findByProblemIdAndIsPublicTrueOrderByIdAsc(anyLong());
        verify(judge0ExecutionService, never()).run(any(), anyInt(), any(), any(), anyDouble(), anyInt());
    }

    private void stubContext(Problem problem) {
        User user = User.builder().id(1L).username("team1").role(Role.TEAM).build();
        when(userRepository.findByUsername("team1")).thenReturn(Optional.of(user));
        when(contestService.getContestEntity()).thenReturn(problem.getContest());
        when(problemRepository.findByIdWithContest(5L)).thenReturn(Optional.of(problem));
    }

    private RunRequest runRequest(boolean includePublicSamples, List<Long> customIds, String sourceCode) {
        return new RunRequest(
                54,
                null,
                sourceCode,
                null,
                includePublicSamples,
                customIds,
                List.of()
        );
    }

    private Problem problem() {
        return problem(ComparePolicy.EXACT);
    }

    private Problem problem(ComparePolicy comparePolicy) {
        Contest contest = Contest.builder().id(7L).title("Practice").build();
        return Problem.builder()
                .id(5L)
                .contest(contest)
                .title("A+B")
                .timeLimit(1000)
                .memoryLimit(128)
                .comparePolicy(comparePolicy)
                .build();
    }

    private TestCase sample(Long id, Problem problem, String input, String expected, boolean isPublic) {
        return TestCase.builder()
                .id(id)
                .problem(problem)
                .inputData(input)
                .expectedOutput(expected)
                .isPublic(isPublic)
                .build();
    }

    private UserCustomTestCase customTest(Problem problem, String input, String expected) {
        return UserCustomTestCase.builder()
                .id(20L)
                .contest(problem.getContest())
                .problem(problem)
                .owner(User.builder().id(1L).username("team1").role(Role.TEAM).build())
                .inputData(input)
                .expectedOutput(expected)
                .build();
    }

    private OracleJudge0ExecutionService.SandboxExecutionResult execution(Verdict verdict, String stdout) {
        return execution(verdict, stdout, null);
    }

    private OracleJudge0ExecutionService.SandboxExecutionResult execution(
            Verdict verdict,
            String stdout,
            String diagnostic
    ) {
        return new OracleJudge0ExecutionService.SandboxExecutionResult(
                verdict,
                verdict == Verdict.ACCEPTED ? 3 : 4,
                verdict == Verdict.ACCEPTED ? "Accepted" : "Wrong Answer",
                stdout,
                null,
                10,
                1024,
                diagnostic
        );
    }
}
