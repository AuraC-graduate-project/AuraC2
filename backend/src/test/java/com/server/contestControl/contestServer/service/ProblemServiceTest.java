package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.problem.ProblemRequest;
import com.server.contestControl.contestServer.dto.problem.ProblemResponse;
import com.server.contestControl.contestServer.dto.problem.ProblemUpdateRequest;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.enums.ValidationMode;
import com.server.contestControl.contestServer.exceptions.InvalidComparePolicyException;
import com.server.contestControl.contestServer.exceptions.InvalidValidatorConfigurationException;
import com.server.contestControl.contestServer.exceptions.ProblemDeletionConflictException;
import com.server.contestControl.contestServer.oracle.repository.CounterexampleRepository;
import com.server.contestControl.contestServer.oracle.repository.GeneratedTestBatchRepository;
import com.server.contestControl.contestServer.oracle.repository.InputGeneratorRepository;
import com.server.contestControl.contestServer.oracle.repository.InputValidatorRepository;
import com.server.contestControl.contestServer.oracle.repository.ReferenceSolutionRepository;
import com.server.contestControl.contestServer.repository.ClarificationRepository;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealCellRepository;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private ContestRepository contestRepository;
    @Mock private ClarificationRepository clarificationRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private ScoreboardRevealCellRepository scoreboardRevealCellRepository;
    @Mock private GeneratedTestBatchRepository generatedTestBatchRepository;
    @Mock private CounterexampleRepository counterexampleRepository;
    @Mock private ReferenceSolutionRepository referenceSolutionRepository;
    @Mock private InputGeneratorRepository inputGeneratorRepository;
    @Mock private InputValidatorRepository inputValidatorRepository;

    @InjectMocks
    private ProblemService problemService;

    @Test
    void createProblemDefaultsComparePolicyToExact() {
        Contest contest = Contest.builder().id(1L).build();
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(problemRepository.countByContest_Id(1L)).thenReturn(0L);

        ProblemResponse response = problemService.createProblem(baseRequest(null));

        ArgumentCaptor<Problem> problemCaptor = ArgumentCaptor.forClass(Problem.class);
        verify(problemRepository).save(problemCaptor.capture());
        assertThat(problemCaptor.getValue().getComparePolicy()).isEqualTo(ComparePolicy.EXACT);
        assertThat(problemCaptor.getValue().getFloatAbsoluteEpsilon()).isNull();
        assertThat(problemCaptor.getValue().getFloatRelativeEpsilon()).isNull();
        assertThat(response.getComparePolicy()).isEqualTo("EXACT");
    }

    @Test
    void createProblemAcceptsFloatToleranceWithPositiveEpsilon() {
        Contest contest = Contest.builder().id(1L).build();
        ProblemRequest request = baseRequest("FLOAT_TOLERANCE");
        request.setFloatAbsoluteEpsilon(0.001);
        request.setFloatRelativeEpsilon(0.0001);

        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(problemRepository.countByContest_Id(1L)).thenReturn(0L);

        problemService.createProblem(request);

        ArgumentCaptor<Problem> problemCaptor = ArgumentCaptor.forClass(Problem.class);
        verify(problemRepository).save(problemCaptor.capture());
        assertThat(problemCaptor.getValue().getComparePolicy()).isEqualTo(ComparePolicy.FLOAT_TOLERANCE);
        assertThat(problemCaptor.getValue().getFloatAbsoluteEpsilon()).isEqualTo(0.001);
        assertThat(problemCaptor.getValue().getFloatRelativeEpsilon()).isEqualTo(0.0001);
    }

    @Test
    void createProblemRejectsEpsilonForNonFloatPolicy() {
        ProblemRequest request = baseRequest("TOKEN_NORMALIZED");
        request.setFloatAbsoluteEpsilon(0.001);

        when(contestRepository.findById(1L)).thenReturn(Optional.of(Contest.builder().id(1L).build()));
        when(problemRepository.countByContest_Id(1L)).thenReturn(0L);

        assertThatThrownBy(() -> problemService.createProblem(request))
                .isInstanceOf(InvalidComparePolicyException.class)
                .hasMessageContaining("only valid for FLOAT_TOLERANCE");
    }

    @Test
    void updateProblemPreservesExistingPolicyWhenRequestOmitsIt() {
        Contest contest = Contest.builder().id(1L).build();
        Problem problem = Problem.builder()
                .id(10L)
                .contest(contest)
                .title("Old")
                .description("Old")
                .timeLimit(1000)
                .memoryLimit(128)
                .difficulty(Difficulty.EASY)
                .comparePolicy(ComparePolicy.TOKEN_NORMALIZED)
                .build();
        ProblemUpdateRequest request = new ProblemUpdateRequest();
        request.setTitle("New");
        request.setDescription("New statement");
        request.setTimeLimit(2000);
        request.setMemoryLimit(256);
        request.setDifficulty("MEDIUM");

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(problemRepository.findByContest_IdOrderByIdAsc(1L)).thenReturn(List.of(problem));

        ProblemResponse response = problemService.updateProblem(10L, request);

        assertThat(problem.getComparePolicy()).isEqualTo(ComparePolicy.TOKEN_NORMALIZED);
        assertThat(response.getComparePolicy()).isEqualTo("TOKEN_NORMALIZED");
    }

    @Test
    void updateProblemPreservesExistingFloatEpsilonWhenRequestOmitsIt() {
        Contest contest = Contest.builder().id(1L).build();
        Problem problem = Problem.builder()
                .id(10L)
                .contest(contest)
                .title("Old")
                .description("Old")
                .timeLimit(1000)
                .memoryLimit(128)
                .difficulty(Difficulty.EASY)
                .comparePolicy(ComparePolicy.FLOAT_TOLERANCE)
                .floatAbsoluteEpsilon(0.001)
                .floatRelativeEpsilon(0.0001)
                .build();
        ProblemUpdateRequest request = new ProblemUpdateRequest();
        request.setTitle("New");
        request.setDescription("New statement");
        request.setTimeLimit(2000);
        request.setMemoryLimit(256);
        request.setDifficulty("MEDIUM");

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(problemRepository.findByContest_IdOrderByIdAsc(1L)).thenReturn(List.of(problem));

        ProblemResponse response = problemService.updateProblem(10L, request);

        assertThat(problem.getComparePolicy()).isEqualTo(ComparePolicy.FLOAT_TOLERANCE);
        assertThat(problem.getFloatAbsoluteEpsilon()).isEqualTo(0.001);
        assertThat(problem.getFloatRelativeEpsilon()).isEqualTo(0.0001);
        assertThat(response.getComparePolicy()).isEqualTo("FLOAT_TOLERANCE");
    }

    @Test
    void createProblemAcceptsCustomValidatorAndStoresHashWithoutExposingSource() {
        Contest contest = Contest.builder().id(1L).build();
        ProblemRequest request = baseRequest("TOKEN_NORMALIZED");
        request.setValidationMode("CUSTOM_VALIDATOR");
        request.setValidatorLanguageId(71);
        request.setValidatorSource("print('ACCEPT')");

        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(problemRepository.countByContest_Id(1L)).thenReturn(0L);

        ProblemResponse response = problemService.createProblem(request);

        ArgumentCaptor<Problem> problemCaptor = ArgumentCaptor.forClass(Problem.class);
        verify(problemRepository).save(problemCaptor.capture());
        Problem saved = problemCaptor.getValue();
        assertThat(saved.getValidationMode()).isEqualTo(ValidationMode.CUSTOM_VALIDATOR);
        assertThat(saved.getValidatorEnabled()).isTrue();
        assertThat(saved.getValidatorLanguageId()).isEqualTo(71);
        assertThat(saved.getValidatorSource()).isEqualTo("print('ACCEPT')");
        assertThat(saved.getValidatorSourceHash()).hasSize(64);
        assertThat(response.getValidationMode()).isEqualTo("CUSTOM_VALIDATOR");
        assertThat(response.getValidatorEnabled()).isTrue();
        assertThat(response.getValidatorLanguageId()).isEqualTo(71);
        assertThat(response.getValidatorSourceHash()).isEqualTo(saved.getValidatorSourceHash());
    }

    @Test
    void createProblemRejectsValidatorFieldsWithoutCustomValidatorMode() {
        ProblemRequest request = baseRequest("EXACT");
        request.setValidatorLanguageId(71);
        request.setValidatorSource("print('ACCEPT')");

        when(contestRepository.findById(1L)).thenReturn(Optional.of(Contest.builder().id(1L).build()));
        when(problemRepository.countByContest_Id(1L)).thenReturn(0L);

        assertThatThrownBy(() -> problemService.createProblem(request))
                .isInstanceOf(InvalidValidatorConfigurationException.class)
                .hasMessageContaining("CUSTOM_VALIDATOR");
    }

    @Test
    void createProblemRejectsEnabledValidatorWithoutSource() {
        ProblemRequest request = baseRequest("EXACT");
        request.setValidationMode("CUSTOM_VALIDATOR");
        request.setValidatorLanguageId(71);

        when(contestRepository.findById(1L)).thenReturn(Optional.of(Contest.builder().id(1L).build()));
        when(problemRepository.countByContest_Id(1L)).thenReturn(0L);

        assertThatThrownBy(() -> problemService.createProblem(request))
                .isInstanceOf(InvalidValidatorConfigurationException.class)
                .hasMessageContaining("validatorSource");
    }

    @Test
    void updateProblemCanDisableCustomValidatorAndReturnToBuiltinMode() {
        Contest contest = Contest.builder().id(1L).build();
        Problem problem = Problem.builder()
                .id(10L)
                .contest(contest)
                .title("Old")
                .description("Old")
                .timeLimit(1000)
                .memoryLimit(128)
                .difficulty(Difficulty.EASY)
                .comparePolicy(ComparePolicy.TOKEN_NORMALIZED)
                .validationMode(ValidationMode.CUSTOM_VALIDATOR)
                .validatorEnabled(true)
                .validatorLanguageId(71)
                .validatorSource("print('ACCEPT')")
                .validatorSourceHash("a".repeat(64))
                .build();
        ProblemUpdateRequest request = new ProblemUpdateRequest();
        request.setTitle("New");
        request.setDescription("New statement");
        request.setTimeLimit(2000);
        request.setMemoryLimit(256);
        request.setDifficulty("MEDIUM");
        request.setValidationMode("BUILTIN_COMPARE_POLICY");

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(problemRepository.findByContest_IdOrderByIdAsc(1L)).thenReturn(List.of(problem));

        ProblemResponse response = problemService.updateProblem(10L, request);

        assertThat(problem.getValidationMode()).isEqualTo(ValidationMode.BUILTIN_COMPARE_POLICY);
        assertThat(problem.getValidatorEnabled()).isFalse();
        assertThat(problem.getValidatorSource()).isNull();
        assertThat(response.getValidationMode()).isEqualTo("BUILTIN_COMPARE_POLICY");
        assertThat(response.getValidatorSourceHash()).isNull();
    }

    @Test
    void deleteProblemDeletesOnlyTestCasesBeforeManagedProblemWhenNoHistoryExists() {
        Problem problem = Problem.builder().id(10L).contest(Contest.builder().id(1L).build()).build();
        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));

        problemService.deleteProblem(10L);

        verify(referenceSolutionRepository).deleteByProblem_Id(10L);
        verify(inputGeneratorRepository).deleteByProblem_Id(10L);
        verify(inputValidatorRepository).deleteByProblem_Id(10L);
        verify(testCaseRepository).deleteByProblem_Id(10L);
        verify(problemRepository).delete(problem);
    }

    @Test
    void deleteProblemRejectsProblemWithSubmissions() {
        Problem problem = Problem.builder().id(10L).contest(Contest.builder().id(1L).build()).build();
        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(submissionRepository.existsByProblem_Id(10L)).thenReturn(true);

        assertThatThrownBy(() -> problemService.deleteProblem(10L))
                .isInstanceOf(ProblemDeletionConflictException.class)
                .hasMessageContaining("submissions or judging history");
    }

    @Test
    void deleteProblemRejectsProblemWithScoreboardRevealCells() {
        Problem problem = Problem.builder().id(10L).contest(Contest.builder().id(1L).build()).build();
        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(scoreboardRevealCellRepository.existsByProblem_Id(10L)).thenReturn(true);

        assertThatThrownBy(() -> problemService.deleteProblem(10L))
                .isInstanceOf(ProblemDeletionConflictException.class)
                .hasMessageContaining("scoreboard reveal history");
    }

    @Test
    void deleteProblemRejectsProblemWithClarifications() {
        Problem problem = Problem.builder().id(10L).contest(Contest.builder().id(1L).build()).build();
        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(clarificationRepository.existsByProblem_Id(10L)).thenReturn(true);

        assertThatThrownBy(() -> problemService.deleteProblem(10L))
                .isInstanceOf(ProblemDeletionConflictException.class)
                .hasMessageContaining("clarifications");
    }

    @Test
    void deleteProblemRejectsProblemWithGeneratedOracleTests() {
        Problem problem = Problem.builder().id(10L).contest(Contest.builder().id(1L).build()).build();
        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(generatedTestBatchRepository.existsByProblem_Id(10L)).thenReturn(true);

        assertThatThrownBy(() -> problemService.deleteProblem(10L))
                .isInstanceOf(ProblemDeletionConflictException.class)
                .hasMessageContaining("generated oracle tests");
    }

    @Test
    void deleteProblemRejectsProblemWithCounterexamples() {
        Problem problem = Problem.builder().id(10L).contest(Contest.builder().id(1L).build()).build();
        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(counterexampleRepository.existsByProblem_Id(10L)).thenReturn(true);

        assertThatThrownBy(() -> problemService.deleteProblem(10L))
                .isInstanceOf(ProblemDeletionConflictException.class)
                .hasMessageContaining("counterexamples");
    }

    private ProblemRequest baseRequest(String comparePolicy) {
        ProblemRequest request = new ProblemRequest();
        request.setContestId(1L);
        request.setTitle("A + B");
        request.setDescription("Add two integers");
        request.setTimeLimit(1000);
        request.setMemoryLimit(128);
        request.setDifficulty("EASY");
        request.setComparePolicy(comparePolicy);
        request.setBalloonColor("#2563EB");
        return request;
    }
}
