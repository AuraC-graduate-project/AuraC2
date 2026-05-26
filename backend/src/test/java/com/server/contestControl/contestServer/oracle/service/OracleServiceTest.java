package com.server.contestControl.contestServer.oracle.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.enums.ValidationMode;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestBatchRequest;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestBatchResponse;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestPromotionResponse;
import com.server.contestControl.contestServer.oracle.dto.OracleProgramRequest;
import com.server.contestControl.contestServer.oracle.dto.OracleProgramResponse;
import com.server.contestControl.contestServer.oracle.entity.Counterexample;
import com.server.contestControl.contestServer.oracle.entity.GeneratedTestBatch;
import com.server.contestControl.contestServer.oracle.entity.GeneratedTestCase;
import com.server.contestControl.contestServer.oracle.entity.InputGenerator;
import com.server.contestControl.contestServer.oracle.entity.InputValidator;
import com.server.contestControl.contestServer.oracle.entity.ReferenceSolution;
import com.server.contestControl.contestServer.oracle.enums.GeneratedTestBatchStatus;
import com.server.contestControl.contestServer.oracle.enums.GeneratedTestCaseStatus;
import com.server.contestControl.contestServer.oracle.repository.CounterexampleRepository;
import com.server.contestControl.contestServer.oracle.repository.GeneratedTestBatchRepository;
import com.server.contestControl.contestServer.oracle.repository.GeneratedTestCaseRepository;
import com.server.contestControl.contestServer.oracle.repository.InputGeneratorRepository;
import com.server.contestControl.contestServer.oracle.repository.InputValidatorRepository;
import com.server.contestControl.contestServer.oracle.repository.ReferenceSolutionRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.contestServer.service.TestCaseDuplicateService;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.service.compare.OutputComparator;
import com.server.contestControl.submissionServer.service.validator.CustomValidatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OracleServiceTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private ReferenceSolutionRepository referenceSolutionRepository;
    @Mock private InputGeneratorRepository inputGeneratorRepository;
    @Mock private InputValidatorRepository inputValidatorRepository;
    @Mock private GeneratedTestBatchRepository generatedTestBatchRepository;
    @Mock private GeneratedTestCaseRepository generatedTestCaseRepository;
    @Mock private CounterexampleRepository counterexampleRepository;
    @Mock private OracleJudge0ExecutionService oracleJudge0ExecutionService;
    @Mock private CustomValidatorService customValidatorService;

    private OracleService oracleService;
    private final OutputComparator outputComparator = new OutputComparator();
    private TestCaseDuplicateService testCaseDuplicateService;
    private final AtomicLong ids = new AtomicLong(100);
    private final List<GeneratedTestCase> savedGeneratedCases = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testCaseDuplicateService = new TestCaseDuplicateService(testCaseRepository);
        oracleService = new OracleService(
                problemRepository,
                userRepository,
                submissionRepository,
                testCaseRepository,
                referenceSolutionRepository,
                inputGeneratorRepository,
                inputValidatorRepository,
                generatedTestBatchRepository,
                generatedTestCaseRepository,
                counterexampleRepository,
                oracleJudge0ExecutionService,
                outputComparator,
                customValidatorService,
                testCaseDuplicateService
        );

        when(generatedTestBatchRepository.save(any(GeneratedTestBatch.class))).thenAnswer(invocation -> {
            GeneratedTestBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(ids.incrementAndGet());
            }
            if (batch.getCreatedAt() == null) {
                batch.setCreatedAt(LocalDateTime.now());
            }
            return batch;
        });
        when(generatedTestCaseRepository.save(any(GeneratedTestCase.class))).thenAnswer(invocation -> {
            GeneratedTestCase testCase = invocation.getArgument(0);
            if (testCase.getId() == null) {
                testCase.setId(ids.incrementAndGet());
            }
            if (testCase.getCreatedAt() == null) {
                testCase.setCreatedAt(LocalDateTime.now());
            }
            savedGeneratedCases.removeIf(existing -> existing.getId().equals(testCase.getId()));
            savedGeneratedCases.add(testCase);
            return testCase;
        });
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(invocation -> {
            TestCase testCase = invocation.getArgument(0);
            if (testCase.getId() == null) {
                testCase.setId(ids.incrementAndGet());
            }
            return testCase;
        });
        when(testCaseRepository.findByProblemIdForDuplicatePromotionCheck(any())).thenReturn(List.of());
        when(generatedTestCaseRepository.findByBatch_IdOrderByTestNumberAsc(any()))
                .thenAnswer(invocation -> List.copyOf(savedGeneratedCases));
        when(counterexampleRepository.save(any(Counterexample.class))).thenAnswer(invocation -> {
            Counterexample counterexample = invocation.getArgument(0);
            if (counterexample.getId() == null) {
                counterexample.setId(ids.incrementAndGet());
            }
            if (counterexample.getCreatedAt() == null) {
                counterexample.setCreatedAt(LocalDateTime.now());
            }
            return counterexample;
        });
        when(referenceSolutionRepository.save(any(ReferenceSolution.class))).thenAnswer(invocation -> {
            ReferenceSolution solution = invocation.getArgument(0);
            if (solution.getId() == null) {
                solution.setId(ids.incrementAndGet());
            }
            if (solution.getCreatedAt() == null) {
                solution.setCreatedAt(LocalDateTime.now());
            }
            if (solution.getUpdatedAt() == null) {
                solution.setUpdatedAt(LocalDateTime.now());
            }
            return solution;
        });
        when(inputGeneratorRepository.save(any(InputGenerator.class))).thenAnswer(invocation -> {
            InputGenerator generator = invocation.getArgument(0);
            if (generator.getId() == null) {
                generator.setId(ids.incrementAndGet());
            }
            if (generator.getCreatedAt() == null) {
                generator.setCreatedAt(LocalDateTime.now());
            }
            if (generator.getUpdatedAt() == null) {
                generator.setUpdatedAt(LocalDateTime.now());
            }
            return generator;
        });
        when(inputValidatorRepository.save(any(InputValidator.class))).thenAnswer(invocation -> {
            InputValidator validator = invocation.getArgument(0);
            if (validator.getId() == null) {
                validator.setId(ids.incrementAndGet());
            }
            if (validator.getCreatedAt() == null) {
                validator.setCreatedAt(LocalDateTime.now());
            }
            if (validator.getUpdatedAt() == null) {
                validator.setUpdatedAt(LocalDateTime.now());
            }
            return validator;
        });
    }

    @Test
    void referenceSolutionConfigurationStoresHashWithoutReturningSource() {
        Problem problem = problem();
        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        OracleProgramResponse response = oracleService.configureReferenceSolution(
                10L,
                programRequest(54, "reference source", true),
                "admin"
        );

        ArgumentCaptor<ReferenceSolution> captor = ArgumentCaptor.forClass(ReferenceSolution.class);
        verify(referenceSolutionRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("reference source");
        assertThat(captor.getValue().getSourceHash()).hasSize(64);
        assertThat(response.sourceHash()).isEqualTo(captor.getValue().getSourceHash());
    }

    @Test
    void generatedBatchStoresInputReferenceOutputAndCounterexampleOnMismatch() {
        Problem problem = problem();
        Submission submission = submission(problem);
        ReferenceSolution reference = referenceSolution(problem);
        InputGenerator generator = inputGenerator(problem, 1);

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(referenceSolutionRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(reference));
        when(inputGeneratorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(generator));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.empty());
        when(submissionRepository.findByIdWithContestProblemUser(99L)).thenReturn(Optional.of(submission));

        when(oracleJudge0ExecutionService.run("generator", 71, "123\n1\n")).thenReturn(accepted("4\n"));
        when(oracleJudge0ExecutionService.run("reference", 54, "4\n")).thenReturn(accepted("YES\n"));
        when(oracleJudge0ExecutionService.run("team", 62, "4\n")).thenReturn(accepted("NO\n"));

        GeneratedTestBatchResponse response = oracleService.createGeneratedTestBatch(
                10L,
                batchRequest(1, 123L, 99L),
                "admin"
        );

        assertThat(response.status()).isEqualTo(GeneratedTestBatchStatus.COMPLETED.name());
        assertThat(response.generatedCount()).isEqualTo(1);
        assertThat(response.counterexampleCount()).isEqualTo(1);
        assertThat(response.testCases()).hasSize(1);
        assertThat(response.testCases().getFirst().inputData()).isEqualTo("4\n");
        assertThat(response.testCases().getFirst().referenceOutput()).isEqualTo("YES\n");

        ArgumentCaptor<Counterexample> counterexampleCaptor = ArgumentCaptor.forClass(Counterexample.class);
        verify(counterexampleRepository).save(counterexampleCaptor.capture());
        assertThat(counterexampleCaptor.getValue().getGeneratedInput()).isEqualTo("4\n");
        assertThat(counterexampleCaptor.getValue().getReferenceOutput()).isEqualTo("YES\n");
        assertThat(counterexampleCaptor.getValue().getTeamOutput()).isEqualTo("NO\n");
        assertThat(counterexampleCaptor.getValue().getVerdict()).isEqualTo(Verdict.WRONG_ANSWER);
    }

    @Test
    void acceptedGeneratedComparisonDoesNotStoreCounterexample() {
        Problem problem = problem();
        Submission submission = submission(problem);

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(referenceSolutionRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(referenceSolution(problem)));
        when(inputGeneratorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(inputGenerator(problem, 1)));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.empty());
        when(submissionRepository.findByIdWithContestProblemUser(99L)).thenReturn(Optional.of(submission));

        when(oracleJudge0ExecutionService.run("generator", 71, "123\n1\n")).thenReturn(accepted("4\n"));
        when(oracleJudge0ExecutionService.run("reference", 54, "4\n")).thenReturn(accepted("YES\n"));
        when(oracleJudge0ExecutionService.run("team", 62, "4\n")).thenReturn(accepted("YES\n"));

        GeneratedTestBatchResponse response = oracleService.createGeneratedTestBatch(
                10L,
                batchRequest(1, 123L, 99L),
                "admin"
        );

        assertThat(response.generatedCount()).isEqualTo(1);
        assertThat(response.counterexampleCount()).isZero();
        verify(counterexampleRepository, never()).save(any());
    }

    @Test
    void generatedBatchWithoutSubmissionCreatesCandidateTestsOnly() {
        Problem problem = problem();

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(referenceSolutionRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(referenceSolution(problem)));
        when(inputGeneratorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(inputGenerator(problem, 1)));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.empty());

        when(oracleJudge0ExecutionService.run("generator", 71, "123\n1\n")).thenReturn(accepted("4\n"));
        when(oracleJudge0ExecutionService.run("reference", 54, "4\n")).thenReturn(accepted("YES\n"));

        GeneratedTestBatchResponse response = oracleService.createGeneratedTestBatch(
                10L,
                batchRequest(1, 123L, null),
                "admin"
        );

        assertThat(response.status()).isEqualTo(GeneratedTestBatchStatus.COMPLETED.name());
        assertThat(response.generatedCount()).isEqualTo(1);
        assertThat(response.counterexampleCount()).isZero();
        assertThat(response.testCases().getFirst().inputData()).isEqualTo("4\n");
        verify(submissionRepository, never()).findByIdWithContestProblemUser(any());
        verify(counterexampleRepository, never()).save(any());
    }

    @Test
    void inputValidatorRejectsInvalidGeneratedInputSafely() {
        Problem problem = problem();

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(referenceSolutionRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(referenceSolution(problem)));
        when(inputGeneratorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(inputGenerator(problem, 1)));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(inputValidator(problem)));

        when(oracleJudge0ExecutionService.run("generator", 71, "123\n1\n")).thenReturn(accepted("-1\n"));
        when(oracleJudge0ExecutionService.run("validator", 71, "-1\n")).thenReturn(accepted("INVALID\n"));

        GeneratedTestBatchResponse response = oracleService.createGeneratedTestBatch(
                10L,
                batchRequest(1, 123L, null),
                "admin"
        );

        assertThat(response.status()).isEqualTo(GeneratedTestBatchStatus.FAILED.name());
        assertThat(response.generatedCount()).isZero();
        assertThat(response.invalidCount()).isEqualTo(1);
        assertThat(response.testCases().getFirst().status()).isEqualTo(GeneratedTestCaseStatus.INVALID_INPUT.name());
    }

    @Test
    void mixedGeneratedBatchIsMarkedPartial() {
        Problem problem = problem();

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(referenceSolutionRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(referenceSolution(problem)));
        when(inputGeneratorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(inputGenerator(problem, 2)));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(inputValidator(problem)));

        when(oracleJudge0ExecutionService.run("generator", 71, "123\n1\n")).thenReturn(accepted("4\n"));
        when(oracleJudge0ExecutionService.run("validator", 71, "4\n")).thenReturn(accepted("VALID\n"));
        when(oracleJudge0ExecutionService.run("reference", 54, "4\n")).thenReturn(accepted("YES\n"));
        when(oracleJudge0ExecutionService.run("generator", 71, "123\n2\n")).thenReturn(accepted("-1\n"));
        when(oracleJudge0ExecutionService.run("validator", 71, "-1\n")).thenReturn(accepted("INVALID\n"));

        GeneratedTestBatchResponse response = oracleService.createGeneratedTestBatch(
                10L,
                batchRequest(2, 123L, null),
                "admin"
        );

        assertThat(response.status()).isEqualTo(GeneratedTestBatchStatus.PARTIAL.name());
        assertThat(response.generatedCount()).isEqualTo(1);
        assertThat(response.invalidCount()).isEqualTo(1);
    }

    @Test
    void referenceSolutionFailureIsStoredAsSafeFailedGeneratedCase() {
        Problem problem = problem();

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(referenceSolutionRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(referenceSolution(problem)));
        when(inputGeneratorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(inputGenerator(problem, 1)));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.empty());

        when(oracleJudge0ExecutionService.run("generator", 71, "123\n1\n")).thenReturn(accepted("4\n"));
        when(oracleJudge0ExecutionService.run("reference", 54, "4\n"))
                .thenReturn(result(Verdict.RUNTIME_ERROR, 11, "Runtime Error", null, "boom"));

        GeneratedTestBatchResponse response = oracleService.createGeneratedTestBatch(
                10L,
                batchRequest(1, 123L, null),
                "admin"
        );

        assertThat(response.status()).isEqualTo(GeneratedTestBatchStatus.FAILED.name());
        assertThat(response.generatedCount()).isZero();
        assertThat(response.testCases().getFirst().status()).isEqualTo(GeneratedTestCaseStatus.REFERENCE_FAILED.name());
        assertThat(response.testCases().getFirst().diagnostic()).contains("Reference solution failed");
    }

    @Test
    void generatorFailureIsStoredSafelyAndStopsBatch() {
        Problem problem = problem();

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(referenceSolutionRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(referenceSolution(problem)));
        when(inputGeneratorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(inputGenerator(problem, 2)));
        when(inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(10L))
                .thenReturn(Optional.empty());

        when(oracleJudge0ExecutionService.run("generator", 71, "123\n1\n"))
                .thenReturn(result(Verdict.TLE, 5, "Time Limit Exceeded", null, null));

        GeneratedTestBatchResponse response = oracleService.createGeneratedTestBatch(
                10L,
                batchRequest(2, 123L, null),
                "admin"
        );

        assertThat(response.status()).isEqualTo(GeneratedTestBatchStatus.FAILED.name());
        assertThat(response.testCases()).hasSize(1);
        assertThat(response.testCases().getFirst().status()).isEqualTo(GeneratedTestCaseStatus.GENERATOR_FAILED.name());
    }

    @Test
    void promoteCounterexampleCreatesHiddenOfficialTestCase() {
        Problem problem = problem();
        GeneratedTestBatch batch = GeneratedTestBatch.builder()
                .id(1L)
                .problem(problem)
                .seed(123L)
                .generatorSourceHash("a".repeat(64))
                .referenceSolutionSourceHash("b".repeat(64))
                .status(GeneratedTestBatchStatus.COMPLETED)
                .requestedCount(1)
                .build();
        GeneratedTestCase generatedTestCase = GeneratedTestCase.builder()
                .id(2L)
                .batch(batch)
                .problem(problem)
                .testNumber(1)
                .seed(123L)
                .inputData("4\n")
                .referenceOutput("YES\n")
                .status(GeneratedTestCaseStatus.GENERATED)
                .build();
        Counterexample counterexample = Counterexample.builder()
                .id(3L)
                .problem(problem)
                .submission(submission(problem))
                .generatedTestCase(generatedTestCase)
                .judgeRunId(8L)
                .generatedInput("4\n")
                .referenceOutput("YES\n")
                .teamOutput("NO\n")
                .verdict(Verdict.WRONG_ANSWER)
                .build();

        when(counterexampleRepository.findById(3L)).thenReturn(Optional.of(counterexample));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(invocation -> {
            TestCase testCase = invocation.getArgument(0);
            testCase.setId(44L);
            return testCase;
        });

        oracleService.promoteCounterexample(3L);

        ArgumentCaptor<TestCase> testCaseCaptor = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseRepository).save(testCaseCaptor.capture());
        assertThat(testCaseCaptor.getValue().getInputData()).isEqualTo("4\n");
        assertThat(testCaseCaptor.getValue().getExpectedOutput()).isEqualTo("YES\n");
        assertThat(testCaseCaptor.getValue().isPublic()).isFalse();
        assertThat(counterexample.getPromoted()).isTrue();
        assertThat(generatedTestCase.getPromoted()).isTrue();
    }

    @Test
    void promoteGeneratedCaseCreatesHiddenOfficialTestCase() {
        Problem problem = problem();
        GeneratedTestCase generatedTestCase = generatedTestCase(problem, 1, "4\n", "YES\n");

        when(generatedTestCaseRepository.findByIdForPromotion(2L)).thenReturn(Optional.of(generatedTestCase));

        oracleService.promoteGeneratedTestCase(2L);

        ArgumentCaptor<TestCase> testCaseCaptor = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseRepository).save(testCaseCaptor.capture());
        assertThat(testCaseCaptor.getValue().getInputData()).isEqualTo("4");
        assertThat(testCaseCaptor.getValue().getExpectedOutput()).isEqualTo("YES");
        assertThat(testCaseCaptor.getValue().isPublic()).isFalse();
        assertThat(generatedTestCase.getPromoted()).isTrue();
        assertThat(generatedTestCase.getPromotedTestCase()).isNotNull();
    }

    @Test
    void duplicateGeneratedCasePromotionReturnsExistingHiddenTestCaseWithoutCreatingAnother() {
        Problem problem = problem();
        TestCase existingHidden = TestCase.builder()
                .id(44L)
                .problem(problem)
                .inputData("4\n")
                .expectedOutput("YES\n")
                .isPublic(false)
                .build();
        GeneratedTestCase generatedTestCase = generatedTestCase(problem, 1, "4\n", "YES\n");
        generatedTestCase.setPromoted(true);
        generatedTestCase.setPromotedTestCase(existingHidden);

        when(generatedTestCaseRepository.findByIdForPromotion(2L)).thenReturn(Optional.of(generatedTestCase));

        oracleService.promoteGeneratedTestCase(2L);

        verify(testCaseRepository, never()).save(any());
    }

    @Test
    void promoteSelectedGeneratedCasesCreatesHiddenOfficialTestCases() {
        Problem problem = problem();
        GeneratedTestCase first = generatedTestCase(problem, 1, "4\n", "YES\n");
        GeneratedTestCase second = generatedTestCase(problem, 2, "7\n", "NO\n");
        second.setId(3L);

        when(generatedTestCaseRepository.findAllByIdInForPromotion(List.of(2L, 3L)))
                .thenReturn(List.of(first, second));

        GeneratedTestPromotionResponse response = oracleService.promoteGeneratedTestCases(List.of(2L, 3L));

        assertThat(response.promotedCount()).isEqualTo(2);
        assertThat(response.promotedTestCases()).hasSize(2);
        assertThat(first.getPromoted()).isTrue();
        assertThat(second.getPromoted()).isTrue();
        verify(testCaseRepository, times(2)).save(any(TestCase.class));
    }

    @Test
    void promoteAllValidGeneratedCasesInBatchSkipsInvalidCasesByRepositoryQuery() {
        Problem problem = problem();
        GeneratedTestCase first = generatedTestCase(problem, 1, "4\n", "YES\n");
        GeneratedTestCase second = generatedTestCase(problem, 2, "7\n", "NO\n");
        second.setId(3L);

        when(generatedTestCaseRepository.findByBatchIdAndStatusForPromotion(
                1L,
                GeneratedTestCaseStatus.GENERATED
        )).thenReturn(List.of(first, second));

        GeneratedTestPromotionResponse response = oracleService.promoteAllValidGeneratedTestCases(1L);

        assertThat(response.promotedCount()).isEqualTo(2);
        assertThat(response.promotedTestCases()).hasSize(2);
        verify(testCaseRepository, times(2)).save(any(TestCase.class));
    }

    @Test
    void generatedPromotionSkipsExistingOfficialInputDuplicate() {
        Problem problem = problem();
        GeneratedTestCase generatedTestCase = generatedTestCase(problem, 1, "4\n", "YES\n");
        TestCase existingOfficial = TestCase.builder()
                .id(55L)
                .problem(problem)
                .inputData("4")
                .expectedOutput("OLD\n")
                .isPublic(false)
                .build();

        when(generatedTestCaseRepository.findByIdForPromotion(2L)).thenReturn(Optional.of(generatedTestCase));
        when(testCaseRepository.findByProblemIdForDuplicatePromotionCheck(10L)).thenReturn(List.of(existingOfficial));

        GeneratedTestPromotionResponse response = oracleService.promoteGeneratedTestCase(2L);

        assertThat(response.promotedCount()).isZero();
        assertThat(response.skippedDuplicateCount()).isEqualTo(1);
        assertThat(generatedTestCase.getStatus()).isEqualTo(GeneratedTestCaseStatus.DUPLICATE);
        assertThat(generatedTestCase.getDiagnostic()).contains("official test case");
        verify(testCaseRepository, never()).save(any());
        verify(generatedTestCaseRepository).save(generatedTestCase);
    }

    @Test
    void selectedGeneratedPromotionSkipsDuplicateInputsWithinRequest() {
        Problem problem = problem();
        GeneratedTestCase first = generatedTestCase(problem, 1, "4\n", "YES\n");
        GeneratedTestCase second = generatedTestCase(problem, 2, "4", "YES\n");
        second.setId(3L);

        when(generatedTestCaseRepository.findAllByIdInForPromotion(List.of(2L, 3L)))
                .thenReturn(List.of(first, second));

        GeneratedTestPromotionResponse response = oracleService.promoteGeneratedTestCases(List.of(2L, 3L));

        assertThat(response.promotedCount()).isEqualTo(1);
        assertThat(response.skippedDuplicateCount()).isEqualTo(1);
        assertThat(first.getPromoted()).isTrue();
        assertThat(second.getStatus()).isEqualTo(GeneratedTestCaseStatus.DUPLICATE);
        verify(testCaseRepository, times(1)).save(any(TestCase.class));
    }

    private OracleProgramRequest programRequest(int languageId, String source, boolean active) {
        OracleProgramRequest request = new OracleProgramRequest();
        request.setLanguageId(languageId);
        request.setSource(source);
        request.setActive(active);
        return request;
    }

    private GeneratedTestBatchRequest batchRequest(Integer testCount, Long seed, Long submissionId) {
        GeneratedTestBatchRequest request = new GeneratedTestBatchRequest();
        request.setTestCount(testCount);
        request.setSeed(seed);
        request.setSubmissionId(submissionId);
        return request;
    }

    private User admin() {
        return User.builder().id(1L).username("admin").role(Role.ADMIN).build();
    }

    private Problem problem() {
        return Problem.builder()
                .id(10L)
                .contest(Contest.builder().id(1L).build())
                .title("Oracle")
                .description("Generated tests")
                .timeLimit(1000)
                .memoryLimit(128)
                .difficulty(Difficulty.EASY)
                .comparePolicy(ComparePolicy.NORMALIZED_TEXT)
                .validationMode(ValidationMode.BUILTIN_COMPARE_POLICY)
                .validatorEnabled(false)
                .build();
    }

    private ReferenceSolution referenceSolution(Problem problem) {
        return ReferenceSolution.builder()
                .id(20L)
                .problem(problem)
                .languageId(54)
                .source("reference")
                .sourceHash("b".repeat(64))
                .active(true)
                .build();
    }

    private InputGenerator inputGenerator(Problem problem, int defaultTestCount) {
        return InputGenerator.builder()
                .id(30L)
                .problem(problem)
                .languageId(71)
                .source("generator")
                .sourceHash("a".repeat(64))
                .active(true)
                .defaultTestCount(defaultTestCount)
                .build();
    }

    private InputValidator inputValidator(Problem problem) {
        return InputValidator.builder()
                .id(40L)
                .problem(problem)
                .languageId(71)
                .source("validator")
                .sourceHash("c".repeat(64))
                .active(true)
                .build();
    }

    private Submission submission(Problem problem) {
        return Submission.builder()
                .id(99L)
                .contest(problem.getContest())
                .problem(problem)
                .user(User.builder().id(2L).username("team").role(Role.TEAM).build())
                .code("team")
                .language("java")
                .judgeRunId(8L)
                .verdict(Verdict.ACCEPTED)
                .build();
    }

    private GeneratedTestCase generatedTestCase(Problem problem, int testNumber, String input, String referenceOutput) {
        GeneratedTestBatch batch = GeneratedTestBatch.builder()
                .id(1L)
                .problem(problem)
                .seed(123L)
                .generatorSourceHash("a".repeat(64))
                .referenceSolutionSourceHash("b".repeat(64))
                .status(GeneratedTestBatchStatus.COMPLETED)
                .requestedCount(1)
                .build();

        return GeneratedTestCase.builder()
                .id(2L)
                .batch(batch)
                .problem(problem)
                .testNumber(testNumber)
                .seed(123L)
                .inputData(input)
                .referenceOutput(referenceOutput)
                .status(GeneratedTestCaseStatus.GENERATED)
                .promoted(false)
                .build();
    }

    private OracleJudge0ExecutionService.SandboxExecutionResult accepted(String stdout) {
        return result(Verdict.ACCEPTED, 3, "Accepted", stdout, null);
    }

    private OracleJudge0ExecutionService.SandboxExecutionResult result(
            Verdict verdict,
            int statusId,
            String statusDescription,
            String stdout,
            String diagnostic
    ) {
        return new OracleJudge0ExecutionService.SandboxExecutionResult(
                verdict,
                statusId,
                statusDescription,
                stdout,
                null,
                10,
                1024,
                diagnostic
        );
    }
}
