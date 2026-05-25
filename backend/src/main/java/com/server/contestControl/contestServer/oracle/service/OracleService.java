package com.server.contestControl.contestServer.oracle.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.exception.api.UserNotFoundException;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.ValidationMode;
import com.server.contestControl.contestServer.exceptions.ProblemDoesNotBelongToContestException;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.oracle.dto.CounterexampleResponse;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestBatchRequest;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestBatchResponse;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestCaseResponse;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestPromotionResponse;
import com.server.contestControl.contestServer.oracle.dto.OracleProgramRequest;
import com.server.contestControl.contestServer.oracle.dto.OracleProgramResponse;
import com.server.contestControl.contestServer.oracle.dto.OracleProgramSourceResponse;
import com.server.contestControl.contestServer.oracle.entity.Counterexample;
import com.server.contestControl.contestServer.oracle.entity.GeneratedTestBatch;
import com.server.contestControl.contestServer.oracle.entity.GeneratedTestCase;
import com.server.contestControl.contestServer.oracle.entity.InputGenerator;
import com.server.contestControl.contestServer.oracle.entity.InputValidator;
import com.server.contestControl.contestServer.oracle.entity.ReferenceSolution;
import com.server.contestControl.contestServer.oracle.enums.GeneratedTestBatchStatus;
import com.server.contestControl.contestServer.oracle.enums.GeneratedTestCaseStatus;
import com.server.contestControl.contestServer.oracle.exception.OracleConfigurationException;
import com.server.contestControl.contestServer.oracle.exception.OracleNotFoundException;
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
import com.server.contestControl.submissionServer.util.Judge0AuditUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.LinkedHashSet;

import static com.server.contestControl.submissionServer.util.LanguageMapper.convertLanguage;

@Service
@RequiredArgsConstructor
@Slf4j
public class OracleService {

    private static final int MAX_GENERATED_TESTS = 100;

    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final ReferenceSolutionRepository referenceSolutionRepository;
    private final InputGeneratorRepository inputGeneratorRepository;
    private final InputValidatorRepository inputValidatorRepository;
    private final GeneratedTestBatchRepository generatedTestBatchRepository;
    private final GeneratedTestCaseRepository generatedTestCaseRepository;
    private final CounterexampleRepository counterexampleRepository;
    private final OracleJudge0ExecutionService oracleJudge0ExecutionService;
    private final OutputComparator outputComparator;
    private final CustomValidatorService customValidatorService;
    private final TestCaseDuplicateService testCaseDuplicateService;

    @Transactional
    public OracleProgramResponse configureReferenceSolution(
            Long problemId,
            OracleProgramRequest request,
            String username
    ) {
        Problem problem = problem(problemId);
        User user = user(username);
        validateProgramRequest(request);
        deactivateReferenceSolutions(problemId);

        ReferenceSolution solution = ReferenceSolution.builder()
                .problem(problem)
                .languageId(request.getLanguageId())
                .source(request.getSource())
                .sourceHash(sha256Hex(request.getSource()))
                .active(request.getActive() == null || request.getActive())
                .createdBy(user)
                .build();

        return OracleProgramResponse.from(referenceSolutionRepository.save(solution));
    }

    @Transactional
    public OracleProgramResponse configureInputGenerator(
            Long problemId,
            OracleProgramRequest request,
            String username
    ) {
        Problem problem = problem(problemId);
        User user = user(username);
        validateProgramRequest(request);
        deactivateInputGenerators(problemId);

        InputGenerator generator = InputGenerator.builder()
                .problem(problem)
                .languageId(request.getLanguageId())
                .source(request.getSource())
                .sourceHash(sha256Hex(request.getSource()))
                .active(request.getActive() == null || request.getActive())
                .defaultTestCount(request.getDefaultTestCount() == null ? 10 : request.getDefaultTestCount())
                .createdBy(user)
                .build();

        return OracleProgramResponse.from(inputGeneratorRepository.save(generator));
    }

    @Transactional
    public OracleProgramResponse configureInputValidator(
            Long problemId,
            OracleProgramRequest request,
            String username
    ) {
        Problem problem = problem(problemId);
        User user = user(username);
        validateProgramRequest(request);
        deactivateInputValidators(problemId);

        InputValidator validator = InputValidator.builder()
                .problem(problem)
                .languageId(request.getLanguageId())
                .source(request.getSource())
                .sourceHash(sha256Hex(request.getSource()))
                .active(request.getActive() == null || request.getActive())
                .createdBy(user)
                .build();

        return OracleProgramResponse.from(inputValidatorRepository.save(validator));
    }

    @Transactional(readOnly = true)
    public List<OracleProgramResponse> referenceSolutions(Long problemId) {
        return referenceSolutionRepository.findByProblem_IdOrderByUpdatedAtDescIdDesc(problemId)
                .stream()
                .map(OracleProgramResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OracleProgramResponse> inputGenerators(Long problemId) {
        return inputGeneratorRepository.findByProblem_IdOrderByUpdatedAtDescIdDesc(problemId)
                .stream()
                .map(OracleProgramResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OracleProgramResponse> inputValidators(Long problemId) {
        return inputValidatorRepository.findByProblem_IdOrderByUpdatedAtDescIdDesc(problemId)
                .stream()
                .map(OracleProgramResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OracleProgramSourceResponse referenceSolutionSource(Long referenceSolutionId) {
        return referenceSolutionRepository.findById(referenceSolutionId)
                .map(OracleProgramSourceResponse::from)
                .orElseThrow(() -> new OracleNotFoundException("Reference solution not found: " + referenceSolutionId));
    }

    @Transactional(readOnly = true)
    public OracleProgramSourceResponse inputGeneratorSource(Long inputGeneratorId) {
        return inputGeneratorRepository.findById(inputGeneratorId)
                .map(OracleProgramSourceResponse::from)
                .orElseThrow(() -> new OracleNotFoundException("Input generator not found: " + inputGeneratorId));
    }

    @Transactional(readOnly = true)
    public OracleProgramSourceResponse inputValidatorSource(Long inputValidatorId) {
        return inputValidatorRepository.findById(inputValidatorId)
                .map(OracleProgramSourceResponse::from)
                .orElseThrow(() -> new OracleNotFoundException("Input validator not found: " + inputValidatorId));
    }

    @Transactional(readOnly = true)
    public OracleProgramSourceResponse customOutputValidatorSource(Long problemId) {
        Problem problem = problem(problemId);
        if (problem.getValidatorSource() == null || problem.getValidatorSource().isBlank()) {
            throw new OracleNotFoundException("Custom output validator source not found for problem: " + problemId);
        }
        return OracleProgramSourceResponse.customValidator(problem);
    }

    @Transactional
    public GeneratedTestBatchResponse createGeneratedTestBatch(
            Long problemId,
            GeneratedTestBatchRequest request,
            String username
    ) {
        Problem problem = problem(problemId);
        User user = user(username);
        ReferenceSolution referenceSolution = activeReferenceSolution(problemId);
        InputGenerator generator = activeInputGenerator(problemId);
        Optional<InputValidator> validator = inputValidatorRepository
                .findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(problemId);

        Submission submission = submission(request.getSubmissionId(), problem);
        int testCount = requestedTestCount(request, generator);
        long seed = request.getSeed() == null ? System.currentTimeMillis() : request.getSeed();

        GeneratedTestBatch batch = GeneratedTestBatch.builder()
                .problem(problem)
                .seed(seed)
                .generatorSourceHash(generator.getSourceHash())
                .referenceSolutionSourceHash(referenceSolution.getSourceHash())
                .status(GeneratedTestBatchStatus.COMPLETED)
                .requestedCount(testCount)
                .createdBy(user)
                .build();
        generatedTestBatchRepository.save(batch);

        int generatedCount = 0;
        int invalidCount = 0;
        int counterexampleCount = 0;
        String batchDiagnostic = null;

        for (int testNumber = 1; testNumber <= testCount; testNumber++) {
            OracleJudge0ExecutionService.SandboxExecutionResult generatedInput =
                    oracleJudge0ExecutionService.run(
                            generator.getSource(),
                            generator.getLanguageId(),
                            generatorStdin(seed, testNumber)
                    );

            if (!generatedInput.accepted()) {
                GeneratedTestCase failedCase = saveGeneratedCase(
                        batch,
                        problem,
                        testNumber,
                        seed,
                        null,
                        null,
                        GeneratedTestCaseStatus.GENERATOR_FAILED,
                        diagnostic("Input generator failed", generatedInput)
                );
                batchDiagnostic = failedCase.getDiagnostic();
                break;
            }

            String inputData = normalizeGeneratedInput(generatedInput.stdout());
            InputValidationResult validation = validateGeneratedInput(validator, inputData);
            if (!validation.valid()) {
                invalidCount++;
                saveGeneratedCase(
                        batch,
                        problem,
                        testNumber,
                        seed,
                        inputData,
                        null,
                        GeneratedTestCaseStatus.INVALID_INPUT,
                        validation.diagnostic()
                );
                continue;
            }

            OracleJudge0ExecutionService.SandboxExecutionResult referenceOutput =
                    oracleJudge0ExecutionService.run(
                            referenceSolution.getSource(),
                            referenceSolution.getLanguageId(),
                            inputData
                    );

            if (!referenceOutput.accepted()) {
                saveGeneratedCase(
                        batch,
                        problem,
                        testNumber,
                        seed,
                        inputData,
                        null,
                        GeneratedTestCaseStatus.REFERENCE_FAILED,
                        diagnostic("Reference solution failed", referenceOutput)
                );
                continue;
            }

            GeneratedTestCase generatedCase = saveGeneratedCase(
                    batch,
                    problem,
                    testNumber,
                    seed,
                    inputData,
                    normalizeGeneratedOutput(referenceOutput.stdout()),
                    GeneratedTestCaseStatus.GENERATED,
                    null
            );
            generatedCount++;

            if (submission != null && evaluateSubmission(problem, submission, generatedCase).isPresent()) {
                counterexampleCount++;
            }
        }

        batch.setGeneratedCount(generatedCount);
        batch.setInvalidCount(invalidCount);
        batch.setCounterexampleCount(counterexampleCount);
        batch.setCompletedAt(LocalDateTime.now());
        batch.setDiagnostic(batchDiagnostic);
        if (generatedCount == 0) {
            batch.setStatus(GeneratedTestBatchStatus.FAILED);
        } else if (generatedCount < testCount || invalidCount > 0 || batchDiagnostic != null) {
            batch.setStatus(GeneratedTestBatchStatus.PARTIAL);
        }
        generatedTestBatchRepository.save(batch);

        return batchResponse(batch);
    }

    @Transactional(readOnly = true)
    public GeneratedTestBatchResponse getBatch(Long batchId) {
        return batchResponse(generatedTestBatchRepository.findById(batchId)
                .orElseThrow(() -> new OracleNotFoundException("Generated test batch not found: " + batchId)));
    }

    @Transactional(readOnly = true)
    public List<GeneratedTestBatchResponse> batches(Long problemId) {
        return generatedTestBatchRepository.findByProblem_IdOrderByCreatedAtDescIdDesc(problemId)
                .stream()
                .map(this::batchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CounterexampleResponse> counterexamples(Long problemId) {
        return counterexampleRepository.findByProblem_IdOrderByCreatedAtDescIdDesc(problemId)
                .stream()
                .map(CounterexampleResponse::from)
                .toList();
    }

    @Transactional
    public TestCaseResponse promoteCounterexample(Long counterexampleId) {
        Counterexample counterexample = counterexampleRepository.findById(counterexampleId)
                .orElseThrow(() -> new OracleNotFoundException("Counterexample not found: " + counterexampleId));

        if (Boolean.TRUE.equals(counterexample.getPromoted()) && counterexample.getPromotedTestCase() != null) {
            return TestCaseResponse.fromEntity(counterexample.getPromotedTestCase());
        }

        TestCase officialHiddenTestCase = TestCase.builder()
                .problem(counterexample.getProblem())
                .inputData(counterexample.getGeneratedInput())
                .expectedOutput(counterexample.getReferenceOutput())
                .isPublic(false)
                .build();
        testCaseRepository.save(officialHiddenTestCase);

        GeneratedTestCase generatedTestCase = counterexample.getGeneratedTestCase();
        generatedTestCase.setPromoted(true);
        generatedTestCase.setPromotedTestCase(officialHiddenTestCase);
        generatedTestCaseRepository.save(generatedTestCase);

        counterexample.setPromoted(true);
        counterexample.setPromotedTestCase(officialHiddenTestCase);
        counterexampleRepository.save(counterexample);

        log.info(
                "Counterexample promoted to hidden official test case. counterexampleId={} problemId={} testCaseId={}",
                counterexampleId,
                counterexample.getProblem().getId(),
                officialHiddenTestCase.getId()
        );

        return TestCaseResponse.fromEntity(officialHiddenTestCase);
    }

    @Transactional
    public GeneratedTestPromotionResponse promoteGeneratedTestCase(Long generatedTestCaseId) {
        GeneratedTestCase generatedTestCase = generatedTestCaseRepository.findByIdForPromotion(generatedTestCaseId)
                .orElseThrow(() -> new OracleNotFoundException("Generated test case not found: " + generatedTestCaseId));

        return promoteGeneratedTestCaseEntities(List.of(generatedTestCase), 1);
    }

    @Transactional
    public GeneratedTestPromotionResponse promoteGeneratedTestCases(List<Long> generatedTestCaseIds) {
        if (generatedTestCaseIds == null || generatedTestCaseIds.isEmpty()) {
            throw new OracleConfigurationException("At least one generated test case must be selected");
        }

        List<Long> uniqueIds = new LinkedHashSet<>(generatedTestCaseIds).stream().toList();
        List<GeneratedTestCase> generatedTestCases =
                generatedTestCaseRepository.findAllByIdInForPromotion(uniqueIds);

        if (generatedTestCases.size() != uniqueIds.size()) {
            throw new OracleNotFoundException("One or more generated test cases were not found");
        }

        return promoteGeneratedTestCaseEntities(generatedTestCases, uniqueIds.size());
    }

    @Transactional
    public GeneratedTestPromotionResponse promoteAllValidGeneratedTestCases(Long batchId) {
        List<GeneratedTestCase> generatedTestCases =
                generatedTestCaseRepository.findByBatchIdAndStatusForPromotion(
                        batchId,
                        GeneratedTestCaseStatus.GENERATED
                );

        if (generatedTestCases.isEmpty()) {
            throw new OracleNotFoundException("No valid generated test cases found for batch: " + batchId);
        }

        return promoteGeneratedTestCaseEntities(generatedTestCases, generatedTestCases.size());
    }

    private Optional<Counterexample> evaluateSubmission(
            Problem problem,
            Submission submission,
            GeneratedTestCase generatedTestCase
    ) {
        OracleJudge0ExecutionService.SandboxExecutionResult executionResult;
        try {
            executionResult = oracleJudge0ExecutionService.run(
                    submission.getCode(),
                    convertLanguage(submission.getLanguage()),
                    generatedTestCase.getInputData()
            );
        } catch (RuntimeException ex) {
            executionResult = OracleJudge0ExecutionService.SandboxExecutionResult.internalError(
                    "Team submission oracle execution failed: " + ex.getMessage()
            );
        }

        ComparisonOutcome outcome = compareGeneratedOutput(problem, generatedTestCase, executionResult);
        if (outcome.verdict() == Verdict.ACCEPTED) {
            return Optional.empty();
        }

        Counterexample counterexample = Counterexample.builder()
                .problem(problem)
                .submission(submission)
                .generatedTestCase(generatedTestCase)
                .judgeRunId(submission.getJudgeRunId() == null ? 0L : submission.getJudgeRunId())
                .generatedInput(safeLongText(generatedTestCase.getInputData()))
                .referenceOutput(safeLongText(generatedTestCase.getReferenceOutput()))
                .teamOutput(safeLongText(executionResult.stdout()))
                .verdict(outcome.verdict())
                .comparePolicy(problem.getComparePolicy())
                .validationMode(problem.getValidationMode())
                .diagnostic(Judge0AuditUtil.firstSafeDiagnostic(outcome.diagnostic(), executionResult.diagnostic()))
                .promoted(false)
                .build();

        return Optional.of(counterexampleRepository.save(counterexample));
    }

    private GeneratedTestPromotionResponse promoteGeneratedTestCaseEntities(
            List<GeneratedTestCase> generatedTestCases,
            int requestedCount
    ) {
        List<TestCaseResponse> promoted = new ArrayList<>();
        List<GeneratedTestCaseResponse> skipped = new ArrayList<>();
        Set<String> seenInputsInRequest = new HashSet<>();
        int alreadyPromoted = 0;
        int skippedDuplicate = 0;
        int skippedInvalid = 0;

        for (GeneratedTestCase generatedTestCase : generatedTestCases) {
            if (Boolean.TRUE.equals(generatedTestCase.getPromoted())
                    && generatedTestCase.getPromotedTestCase() != null) {
                alreadyPromoted++;
                promoted.add(TestCaseResponse.fromEntity(generatedTestCase.getPromotedTestCase()));
                continue;
            }

            if (generatedTestCase.getStatus() != GeneratedTestCaseStatus.GENERATED
                    || generatedTestCase.getInputData() == null
                    || generatedTestCase.getReferenceOutput() == null) {
                skippedInvalid++;
                skipped.add(GeneratedTestCaseResponse.from(generatedTestCase));
                continue;
            }

            String normalizedInput = testCaseDuplicateService.normalizeInput(generatedTestCase.getInputData());
            boolean duplicateInRequest = !seenInputsInRequest.add(normalizedInput);
            boolean duplicateOfficial = testCaseDuplicateService.inputDuplicateExistsForPromotion(
                    generatedTestCase.getProblem().getId(),
                    generatedTestCase.getInputData()
            );

            if (duplicateInRequest || duplicateOfficial) {
                skippedDuplicate++;
                generatedTestCase.setStatus(GeneratedTestCaseStatus.DUPLICATE);
                generatedTestCase.setDiagnostic(duplicateOfficial
                        ? "Skipped during promotion: an official test case with the same input already exists."
                        : "Skipped during promotion: another selected generated candidate has the same input.");
                generatedTestCaseRepository.save(generatedTestCase);
                skipped.add(GeneratedTestCaseResponse.from(generatedTestCase));
                continue;
            }

            TestCase officialHiddenTestCase = TestCase.builder()
                    .problem(generatedTestCase.getProblem())
                    .inputData(testCaseDuplicateService.normalizeInput(generatedTestCase.getInputData()))
                    .expectedOutput(testCaseDuplicateService.normalizeOutput(generatedTestCase.getReferenceOutput()))
                    .isPublic(false)
                    .build();
            testCaseRepository.save(officialHiddenTestCase);

            generatedTestCase.setPromoted(true);
            generatedTestCase.setPromotedTestCase(officialHiddenTestCase);
            generatedTestCaseRepository.save(generatedTestCase);
            promoted.add(TestCaseResponse.fromEntity(officialHiddenTestCase));

            log.info(
                    "Generated test case promoted to hidden official test case. generatedTestCaseId={} problemId={} testCaseId={}",
                    generatedTestCase.getId(),
                    generatedTestCase.getProblem().getId(),
                    officialHiddenTestCase.getId()
            );
        }

        String message = promotionMessage(promoted.size(), alreadyPromoted, skippedDuplicate, skippedInvalid);
        return new GeneratedTestPromotionResponse(
                requestedCount,
                promoted.size(),
                alreadyPromoted,
                skippedDuplicate,
                skippedInvalid,
                promoted,
                skipped,
                message
        );
    }

    private String promotionMessage(int promoted, int alreadyPromoted, int skippedDuplicate, int skippedInvalid) {
        List<String> parts = new ArrayList<>();
        parts.add(promoted + " promoted");
        if (alreadyPromoted > 0) {
            parts.add(alreadyPromoted + " already promoted");
        }
        if (skippedDuplicate > 0) {
            parts.add(skippedDuplicate + " duplicate skipped");
        }
        if (skippedInvalid > 0) {
            parts.add(skippedInvalid + " invalid skipped");
        }
        return String.join(", ", parts) + ".";
    }

    private ComparisonOutcome compareGeneratedOutput(
            Problem problem,
            GeneratedTestCase generatedTestCase,
            OracleJudge0ExecutionService.SandboxExecutionResult executionResult
    ) {
        if (executionResult.verdict() != Verdict.ACCEPTED) {
            return new ComparisonOutcome(
                    executionResult.verdict(),
                    diagnostic("Team submission execution failed", executionResult)
            );
        }

        if (problem.hasActiveCustomValidator()) {
            TestCase transientCase = TestCase.builder()
                    .problem(problem)
                    .inputData(generatedTestCase.getInputData())
                    .expectedOutput(generatedTestCase.getReferenceOutput())
                    .isPublic(false)
                    .build();
            CustomValidatorService.ValidatorResult result = customValidatorService.validate(
                    problem,
                    transientCase,
                    executionResult.stdout()
            );
            return new ComparisonOutcome(result.verdict(), result.diagnostic());
        }

        ComparePolicy comparePolicy = problem.getComparePolicy() == null ? ComparePolicy.EXACT : problem.getComparePolicy();
        OutputComparator.ComparisonResult comparison = outputComparator.compare(
                comparePolicy,
                generatedTestCase.getReferenceOutput(),
                executionResult.stdout(),
                problem.getFloatAbsoluteEpsilon(),
                problem.getFloatRelativeEpsilon()
        );

        return comparison.matches()
                ? new ComparisonOutcome(Verdict.ACCEPTED, null)
                : new ComparisonOutcome(Verdict.WRONG_ANSWER, comparison.diagnostic());
    }

    private InputValidationResult validateGeneratedInput(Optional<InputValidator> validator, String inputData) {
        if (validator.isEmpty()) {
            return InputValidationResult.accepted();
        }

        OracleJudge0ExecutionService.SandboxExecutionResult result = oracleJudge0ExecutionService.run(
                validator.get().getSource(),
                validator.get().getLanguageId(),
                inputData
        );

        if (!result.accepted()) {
            return InputValidationResult.invalid(diagnostic("Input validator execution failed", result));
        }

        String decision = firstDecisionLine(result.stdout());
        if (decision == null) {
            return InputValidationResult.invalid("Input validator produced no decision");
        }

        return switch (decision) {
            case "ACCEPT", "ACCEPTED", "OK", "VALID" -> InputValidationResult.accepted();
            case "REJECT", "REJECTED", "INVALID" -> InputValidationResult.invalid("Input validator rejected generated input");
            default -> InputValidationResult.invalid("Input validator produced invalid decision");
        };
    }

    private GeneratedTestCase saveGeneratedCase(
            GeneratedTestBatch batch,
            Problem problem,
            int testNumber,
            long seed,
            String inputData,
            String referenceOutput,
            GeneratedTestCaseStatus status,
            String diagnostic
    ) {
        GeneratedTestCase generatedTestCase = GeneratedTestCase.builder()
                .batch(batch)
                .problem(problem)
                .testNumber(testNumber)
                .seed(seed)
                .inputData(safeLongText(inputData))
                .referenceOutput(safeLongText(referenceOutput))
                .status(status)
                .promoted(false)
                .diagnostic(Judge0AuditUtil.firstSafeDiagnostic(diagnostic))
                .build();
        return generatedTestCaseRepository.save(generatedTestCase);
    }

    private GeneratedTestBatchResponse batchResponse(GeneratedTestBatch batch) {
        List<GeneratedTestCaseResponse> testCases = generatedTestCaseRepository
                .findByBatch_IdOrderByTestNumberAsc(batch.getId())
                .stream()
                .map(GeneratedTestCaseResponse::from)
                .toList();
        return GeneratedTestBatchResponse.from(batch, testCases);
    }

    private Problem problem(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));
    }

    private User user(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }

    private ReferenceSolution activeReferenceSolution(Long problemId) {
        return referenceSolutionRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(problemId)
                .orElseThrow(() -> new OracleConfigurationException(
                        "Problem " + problemId + " has no active reference solution"
                ));
    }

    private InputGenerator activeInputGenerator(Long problemId) {
        return inputGeneratorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(problemId)
                .orElseThrow(() -> new OracleConfigurationException(
                        "Problem " + problemId + " has no active input generator"
                ));
    }

    private Submission submission(Long submissionId, Problem problem) {
        if (submissionId == null) {
            return null;
        }

        Submission submission = submissionRepository.findByIdWithContestProblemUser(submissionId)
                .orElseThrow(() -> new OracleNotFoundException("Submission not found: " + submissionId));

        if (!submission.getProblem().getId().equals(problem.getId())) {
            throw new ProblemDoesNotBelongToContestException(problem.getId(), submission.getContest().getId());
        }

        return submission;
    }

    private int requestedTestCount(GeneratedTestBatchRequest request, InputGenerator generator) {
        int count = request.getTestCount() == null ? generator.getDefaultTestCount() : request.getTestCount();
        if (count <= 0 || count > MAX_GENERATED_TESTS) {
            throw new OracleConfigurationException(
                    "testCount must be between 1 and " + MAX_GENERATED_TESTS
            );
        }
        return count;
    }

    private void validateProgramRequest(OracleProgramRequest request) {
        if (request.getLanguageId() == null || request.getLanguageId() <= 0) {
            throw new OracleConfigurationException("languageId must be positive");
        }
        if (request.getSource() == null || request.getSource().isBlank()) {
            throw new OracleConfigurationException("source must not be blank");
        }
        if (request.getDefaultTestCount() != null
                && (request.getDefaultTestCount() <= 0 || request.getDefaultTestCount() > MAX_GENERATED_TESTS)) {
            throw new OracleConfigurationException(
                    "defaultTestCount must be between 1 and " + MAX_GENERATED_TESTS
            );
        }
    }

    private void deactivateReferenceSolutions(Long problemId) {
        referenceSolutionRepository.findByProblem_IdOrderByUpdatedAtDescIdDesc(problemId)
                .forEach(solution -> solution.setActive(false));
    }

    private void deactivateInputGenerators(Long problemId) {
        inputGeneratorRepository.findByProblem_IdOrderByUpdatedAtDescIdDesc(problemId)
                .forEach(generator -> generator.setActive(false));
    }

    private void deactivateInputValidators(Long problemId) {
        inputValidatorRepository.findByProblem_IdOrderByUpdatedAtDescIdDesc(problemId)
                .forEach(validator -> validator.setActive(false));
    }

    private String generatorStdin(long seed, int testNumber) {
        return seed + "\n" + testNumber + "\n";
    }

    private String normalizeGeneratedInput(String value) {
        return value == null ? "" : value;
    }

    private String normalizeGeneratedOutput(String value) {
        return value == null ? "" : value;
    }

    private String diagnostic(String prefix, OracleJudge0ExecutionService.SandboxExecutionResult result) {
        return Judge0AuditUtil.firstSafeDiagnostic(
                prefix + ": " + result.verdict(),
                result.statusDescription(),
                result.diagnostic()
        );
    }

    private String firstDecisionLine(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return null;
        }

        String normalized = stdout.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n")) {
            if (!line.isBlank()) {
                return line.trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private String safeLongText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("\u0000", "");
        int maxLength = 8192;
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record InputValidationResult(boolean valid, String diagnostic) {
        static InputValidationResult accepted() {
            return new InputValidationResult(true, null);
        }

        static InputValidationResult invalid(String diagnostic) {
            return new InputValidationResult(false, diagnostic);
        }
    }

    private record ComparisonOutcome(Verdict verdict, String diagnostic) {
    }
}
