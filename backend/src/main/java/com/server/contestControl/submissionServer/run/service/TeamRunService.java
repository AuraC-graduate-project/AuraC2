package com.server.contestControl.submissionServer.run.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.exception.api.UserNotFoundException;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.exceptions.ProblemDoesNotBelongToContestException;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.moderation.service.ContestTeamModerationService;
import com.server.contestControl.contestServer.oracle.entity.InputValidator;
import com.server.contestControl.contestServer.oracle.repository.InputValidatorRepository;
import com.server.contestControl.contestServer.oracle.service.OracleJudge0ExecutionService;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.contestServer.service.ContestService;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.run.dto.CreateCustomTestCaseRequest;
import com.server.contestControl.submissionServer.run.dto.CustomTestCaseResponse;
import com.server.contestControl.submissionServer.run.dto.InlineCustomTestCaseRequest;
import com.server.contestControl.submissionServer.run.dto.RunCaseResult;
import com.server.contestControl.submissionServer.run.dto.RunRequest;
import com.server.contestControl.submissionServer.run.dto.RunResponse;
import com.server.contestControl.submissionServer.run.dto.UpdateCustomTestCaseRequest;
import com.server.contestControl.submissionServer.run.entity.UserCustomTestCase;
import com.server.contestControl.submissionServer.run.exception.CustomTestCaseNotFoundException;
import com.server.contestControl.submissionServer.run.exception.RunRateLimitException;
import com.server.contestControl.submissionServer.run.exception.RunRequestException;
import com.server.contestControl.submissionServer.run.repository.UserCustomTestCaseRepository;
import com.server.contestControl.submissionServer.service.compare.OutputComparator;
import com.server.contestControl.submissionServer.service.judge.Judge0ExpectedOutputPolicy;
import com.server.contestControl.submissionServer.service.validator.CustomValidatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.server.contestControl.submissionServer.util.LanguageMapper.convertLanguage;

@Service
@RequiredArgsConstructor
public class TeamRunService {

    private static final int MAX_SAVED_CUSTOM_TESTS_PER_PROBLEM = 20;
    private static final int MAX_CUSTOM_CASES_PER_RUN = 8;
    private static final int MAX_TOTAL_CASES_PER_RUN = 30;
    private static final int MAX_INPUT_CHARS = 20_000;
    private static final int MAX_EXPECTED_OUTPUT_CHARS = 20_000;
    private static final int MAX_RUNS_PER_WINDOW = 10;
    private static final Duration RUN_RATE_WINDOW = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final ContestService contestService;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final UserCustomTestCaseRepository customTestCaseRepository;
    private final InputValidatorRepository inputValidatorRepository;
    private final OracleJudge0ExecutionService judge0ExecutionService;
    private final OutputComparator outputComparator;
    private final Judge0ExpectedOutputPolicy expectedOutputPolicy;
    private final CustomValidatorService customValidatorService;
    private final ContestTeamModerationService moderationService;
    private final ConcurrentMap<Long, Deque<Instant>> userRunHistory = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<CustomTestCaseResponse> getCustomTests(Long problemId, String username) {
        RunContext context = context(problemId, username);
        return customTestCaseRepository.findByContest_IdAndProblem_IdAndOwner_IdOrderByCreatedAtAscIdAsc(
                        context.contest().getId(),
                        context.problem().getId(),
                        context.user().getId()
                )
                .stream()
                .map(CustomTestCaseResponse::from)
                .toList();
    }

    @Transactional
    public CustomTestCaseResponse createCustomTest(
            Long problemId,
            CreateCustomTestCaseRequest request,
            String username
    ) {
        RunContext context = context(problemId, username);
        String input = normalizeInput(request == null ? null : request.input());
        String expectedOutput = normalizeOptionalExpectedOutput(request == null ? null : request.expectedOutput());

        long existing = customTestCaseRepository.countByContest_IdAndProblem_IdAndOwner_Id(
                context.contest().getId(),
                context.problem().getId(),
                context.user().getId()
        );
        if (existing >= MAX_SAVED_CUSTOM_TESTS_PER_PROBLEM) {
            throw new RunRequestException(
                    "At most " + MAX_SAVED_CUSTOM_TESTS_PER_PROBLEM + " custom tests can be saved for one problem."
            );
        }

        UserCustomTestCase saved = customTestCaseRepository.save(UserCustomTestCase.builder()
                .contest(context.contest())
                .problem(context.problem())
                .owner(context.user())
                .inputData(input)
                .expectedOutput(expectedOutput)
                .build());

        return CustomTestCaseResponse.from(saved);
    }

    @Transactional
    public CustomTestCaseResponse updateCustomTest(
            Long problemId,
            Long customTestId,
            UpdateCustomTestCaseRequest request,
            String username
    ) {
        RunContext context = context(problemId, username);
        UserCustomTestCase customTestCase =
                customTestCaseRepository.findByIdAndContest_IdAndProblem_IdAndOwner_Id(
                                customTestId,
                                context.contest().getId(),
                                context.problem().getId(),
                                context.user().getId()
                        )
                        .orElseThrow(CustomTestCaseNotFoundException::new);

        customTestCase.setInputData(normalizeInput(request == null ? null : request.input()));
        customTestCase.setExpectedOutput(normalizeOptionalExpectedOutput(
                request == null ? null : request.expectedOutput()
        ));

        return CustomTestCaseResponse.from(customTestCase);
    }

    @Transactional
    public void deleteCustomTest(Long problemId, Long customTestId, String username) {
        RunContext context = context(problemId, username);
        UserCustomTestCase customTestCase =
                customTestCaseRepository.findByIdAndContest_IdAndProblem_IdAndOwner_Id(
                                customTestId,
                                context.contest().getId(),
                                context.problem().getId(),
                                context.user().getId()
                        )
                        .orElseThrow(CustomTestCaseNotFoundException::new);

        customTestCaseRepository.delete(customTestCase);
    }

    public RunResponse run(Long problemId, RunRequest request, String username) {
        RunContext context = context(problemId, username);
        moderationService.assertRunAllowed(context.contest(), context.user());
        checkRunRateLimit(context.user().getId());

        String sourceCode = sourceCode(request);
        int languageId = languageId(request);
        List<RunCase> cases = runCases(context, request);
        if (cases.isEmpty()) {
            throw new RunRequestException("No public samples or selected custom tests are available to run.");
        }
        if (cases.size() > MAX_TOTAL_CASES_PER_RUN) {
            throw new RunRequestException("At most " + MAX_TOTAL_CASES_PER_RUN + " cases can be run at once.");
        }

        Optional<InputValidator> inputValidator =
                inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(
                        context.problem().getId()
                );
        List<RunCaseResult> results = new ArrayList<>();
        int caseNumber = 1;
        for (RunCase runCase : cases) {
            if (runCase.custom() && inputValidator.isPresent()) {
                InputValidation validation = validateCustomInput(inputValidator.get(), runCase.input());
                if (!validation.valid()) {
                    results.add(validationResult(caseNumber, runCase, validation.status(), validation.message()));
                    caseNumber++;
                    continue;
                }
            }

            OracleJudge0ExecutionService.SandboxExecutionResult execution = judge0ExecutionService.run(
                    sourceCode,
                    languageId,
                    runCase.input(),
                    expectedOutputPolicy.expectedOutputForJudge0(context.problem(), runCase.expectedOutput()),
                    toJudge0CpuTimeLimitSeconds(context.problem().getTimeLimit()),
                    toJudge0MemoryLimitKilobytes(context.problem().getMemoryLimit())
            );
            results.add(toRunResult(context.problem(), caseNumber, runCase, execution));
            if (execution.verdict() == Verdict.COMPILATION_ERROR) {
                return new RunResponse(
                        "COMPILATION_ERROR",
                        firstText(execution.diagnostic(), execution.stderr()),
                        false,
                        (int) cases.stream().filter(item -> !item.custom()).count(),
                        (int) cases.stream().filter(RunCase::custom).count(),
                        skippedResults(cases)
                );
            }
            caseNumber++;
        }

        return new RunResponse(
                compileStatus(results),
                null,
                false,
                (int) cases.stream().filter(runCase -> !runCase.custom()).count(),
                (int) cases.stream().filter(RunCase::custom).count(),
                results
        );
    }

    private RunContext context(Long problemId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        Contest contest = contestService.getContestEntity();
        Problem problem = problemRepository.findByIdWithContest(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));

        if (problem.getContest() == null || !problem.getContest().getId().equals(contest.getId())) {
            throw new ProblemDoesNotBelongToContestException(problemId, contest.getId());
        }
        moderationService.assertWorkspaceVisible(contest.getId(), username);

        return new RunContext(user, contest, problem);
    }

    private List<RunCase> runCases(RunContext context, RunRequest request) {
        boolean includePublicSamples = request == null || !Boolean.FALSE.equals(request.includePublicSamples());
        List<RunCase> cases = new ArrayList<>();

        if (includePublicSamples) {
            List<TestCase> publicSamples =
                    testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(context.problem().getId());
            for (int i = 0; i < publicSamples.size(); i++) {
                TestCase sample = publicSamples.get(i);
                cases.add(new RunCase(
                        "PUBLIC_SAMPLE",
                        sample.getId(),
                        "Public sample " + (i + 1),
                        normalizeCaseText(sample.getInputData()),
                        normalizeCaseText(sample.getExpectedOutput()),
                        false,
                        sample
                ));
            }
        }

        List<UserCustomTestCase> savedCustomTests = requestedCustomTests(context, request);
        List<InlineCustomTestCaseRequest> inlineCustomTests =
                request == null || request.inlineCustomTests() == null ? List.of() : request.inlineCustomTests();
        int customCount = savedCustomTests.size() + inlineCustomTests.size();
        if (customCount > MAX_CUSTOM_CASES_PER_RUN) {
            throw new RunRequestException("At most " + MAX_CUSTOM_CASES_PER_RUN + " custom tests can be run at once.");
        }

        int customIndex = 1;
        for (UserCustomTestCase customTestCase : savedCustomTests) {
            cases.add(new RunCase(
                    "CUSTOM",
                    customTestCase.getId(),
                    "Custom test " + customIndex,
                    normalizeInput(customTestCase.getInputData()),
                    normalizeOptionalExpectedOutput(customTestCase.getExpectedOutput()),
                    true,
                    null
            ));
            customIndex++;
        }
        for (InlineCustomTestCaseRequest inline : inlineCustomTests) {
            cases.add(new RunCase(
                    "CUSTOM",
                    null,
                    "Custom test " + customIndex,
                    normalizeInput(inline == null ? null : inline.input()),
                    normalizeOptionalExpectedOutput(inline == null ? null : inline.expectedOutput()),
                    true,
                    null
            ));
            customIndex++;
        }

        return cases;
    }

    private List<UserCustomTestCase> requestedCustomTests(RunContext context, RunRequest request) {
        if (request == null || request.customTestCaseIds() == null || request.customTestCaseIds().isEmpty()) {
            return List.of();
        }

        Set<Long> uniqueIds = new LinkedHashSet<>(request.customTestCaseIds());
        if (uniqueIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new RunRequestException("customTestCaseIds must contain positive IDs.");
        }

        List<UserCustomTestCase> found =
                customTestCaseRepository.findByIdInAndContest_IdAndProblem_IdAndOwner_IdOrderByCreatedAtAscIdAsc(
                        uniqueIds,
                        context.contest().getId(),
                        context.problem().getId(),
                        context.user().getId()
                );
        if (found.size() != uniqueIds.size()) {
            throw new CustomTestCaseNotFoundException();
        }
        return found;
    }

    private RunCaseResult toRunResult(
            Problem problem,
            int caseNumber,
            RunCase runCase,
            OracleJudge0ExecutionService.SandboxExecutionResult execution
    ) {
        String status = executionStatus(execution.verdict());
        String diagnostic = execution.diagnostic();

        if (execution.verdict() == Verdict.ACCEPTED) {
            if (!hasText(runCase.expectedOutput())) {
                status = "RUN_COMPLETED";
                diagnostic = "No expected output was provided for this custom test.";
            } else if (expectedOutputPolicy.usesJudge0ExpectedOutput(problem)) {
                status = "PASSED";
                diagnostic = null;
            } else if (problem.hasActiveCustomValidator()) {
                CustomValidatorService.ValidatorResult validation = customValidatorService.validate(
                        problem,
                        testCaseForComparison(problem, runCase),
                        execution.stdout()
                );
                status = switch (validation.verdict()) {
                    case ACCEPTED -> "PASSED";
                    case WRONG_ANSWER -> "WRONG_ANSWER";
                    default -> "INTERNAL_ERROR";
                };
                diagnostic = validation.verdict() == Verdict.INTERNAL_ERROR
                        ? "Output validator could not evaluate this case."
                        : validation.diagnostic();
            } else {
                OutputComparator.ComparisonResult comparison = outputComparator.compare(
                        problem.getComparePolicy(),
                        runCase.expectedOutput(),
                        execution.stdout(),
                        problem.getFloatAbsoluteEpsilon(),
                        problem.getFloatRelativeEpsilon()
                );
                status = comparison.matches() ? "PASSED" : "WRONG_ANSWER";
                diagnostic = comparison.diagnostic();
            }
        } else if (execution.verdict() == Verdict.WRONG_ANSWER
                && hasText(runCase.expectedOutput())
                && expectedOutputPolicy.usesJudge0ExpectedOutput(problem)
                && !hasText(diagnostic)) {
            diagnostic = "Output mismatch under EXACT compare policy";
        }

        return new RunCaseResult(
                caseNumber,
                runCase.caseType(),
                runCase.caseId(),
                runCase.label(),
                runCase.input(),
                runCase.expectedOutput(),
                execution.stdout(),
                execution.stderr(),
                status,
                diagnostic,
                execution.executionTime(),
                execution.memoryUsage()
        );
    }

    private TestCase testCaseForComparison(Problem problem, RunCase runCase) {
        if (runCase.officialTestCase() != null) {
            return runCase.officialTestCase();
        }

        return TestCase.builder()
                .problem(problem)
                .inputData(runCase.input())
                .expectedOutput(runCase.expectedOutput())
                .isPublic(false)
                .build();
    }

    private InputValidation validateCustomInput(InputValidator validator, String input) {
        OracleJudge0ExecutionService.SandboxExecutionResult result = judge0ExecutionService.run(
                validator.getSource(),
                validator.getLanguageId(),
                input
        );
        if (result.verdict() != Verdict.ACCEPTED) {
            return InputValidation.validatorError(
                    "Input validator execution failed; check the problem input validator configuration."
            );
        }

        String decision = firstDecisionLine(result.stdout());
        if (decision == null) {
            return InputValidation.validatorError(
                    "Input validator produced no decision; expected VALID or INVALID."
            );
        }

        return switch (decision) {
            case "VALID", "ACCEPT", "ACCEPTED", "OK" -> InputValidation.accepted();
            case "REJECT", "REJECTED", "INVALID" ->
                    InputValidation.invalidInput("Custom input was rejected by the problem input validator.");
            default -> InputValidation.validatorError(
                    "Input validator produced an unsupported decision; expected VALID or INVALID."
            );
        };
    }

    private RunCaseResult validationResult(int caseNumber, RunCase runCase, String status, String diagnostic) {
        return new RunCaseResult(
                caseNumber,
                runCase.caseType(),
                runCase.caseId(),
                runCase.label(),
                runCase.input(),
                runCase.expectedOutput(),
                null,
                null,
                status,
                diagnostic,
                0,
                0
        );
    }

    private List<RunCaseResult> skippedResults(List<RunCase> cases) {
        List<RunCaseResult> results = new ArrayList<>();
        int caseNumber = 1;
        for (RunCase runCase : cases) {
            results.add(new RunCaseResult(
                    caseNumber,
                    runCase.caseType(),
                    runCase.caseId(),
                    runCase.label(),
                    runCase.input(),
                    runCase.expectedOutput(),
                    null,
                    null,
                    "SKIPPED",
                    "Not run because compilation failed.",
                    0,
                    0
            ));
            caseNumber++;
        }
        return results;
    }

    private String sourceCode(RunRequest request) {
        String sourceCode = request == null ? null : firstText(request.sourceCode(), request.code());
        if (!hasText(sourceCode)) {
            throw new RunRequestException("Source code cannot be empty.");
        }
        if (sourceCode.length() > 200_000) {
            throw new RunRequestException("Source code is too large to run.");
        }
        return sourceCode;
    }

    private int languageId(RunRequest request) {
        if (request != null && request.languageId() != null) {
            if (request.languageId() <= 0) {
                throw new RunRequestException("languageId must be positive.");
            }
            return request.languageId();
        }

        if (request != null && hasText(request.language())) {
            try {
                return convertLanguage(request.language());
            } catch (RuntimeException ex) {
                throw new RunRequestException("Unsupported language: " + request.language());
            }
        }

        throw new RunRequestException("languageId or language is required.");
    }

    private String normalizeInput(String value) {
        String normalized = normalizeCaseText(value);
        if (!hasText(normalized)) {
            throw new RunRequestException("Custom test input cannot be empty.");
        }
        if (normalized.length() > MAX_INPUT_CHARS) {
            throw new RunRequestException("Custom test input cannot exceed " + MAX_INPUT_CHARS + " characters.");
        }
        return normalized;
    }

    private String normalizeOptionalExpectedOutput(String value) {
        String normalized = normalizeCaseText(value);
        if (!hasText(normalized)) {
            return null;
        }
        if (normalized.length() > MAX_EXPECTED_OUTPUT_CHARS) {
            throw new RunRequestException(
                    "Custom test expected output cannot exceed " + MAX_EXPECTED_OUTPUT_CHARS + " characters."
            );
        }
        return normalized;
    }

    private String normalizeCaseText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private String executionStatus(Verdict verdict) {
        if (verdict == null) {
            return "INTERNAL_ERROR";
        }
        return switch (verdict) {
            case ACCEPTED -> "RUN_COMPLETED";
            case WRONG_ANSWER -> "WRONG_ANSWER";
            case TLE -> "TIME_LIMIT_EXCEEDED";
            case COMPILATION_ERROR -> "COMPILATION_ERROR";
            case RUNTIME_ERROR -> "RUNTIME_ERROR";
            default -> "INTERNAL_ERROR";
        };
    }

    private String compileStatus(List<RunCaseResult> results) {
        return results.stream()
                .map(RunCaseResult::status)
                .filter("COMPILATION_ERROR"::equals)
                .findFirst()
                .orElse(results.stream().anyMatch(result -> !"VALIDATION_ERROR".equals(result.status()))
                        ? "OK"
                        : "NOT_RUN");
    }

    private String firstDecisionLine(String stdout) {
        if (!hasText(stdout)) {
            return null;
        }
        for (String line : stdout.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            if (!line.isBlank()) {
                return line.trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private Double toJudge0CpuTimeLimitSeconds(Integer timeLimitMillis) {
        if (timeLimitMillis == null || timeLimitMillis <= 0) {
            return null;
        }
        return BigDecimal.valueOf(timeLimitMillis)
                .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .doubleValue();
    }

    private Integer toJudge0MemoryLimitKilobytes(Integer memoryLimitMegabytes) {
        if (memoryLimitMegabytes == null || memoryLimitMegabytes <= 0) {
            return null;
        }
        return Math.multiplyExact(memoryLimitMegabytes, 1024);
    }

    private void checkRunRateLimit(Long userId) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(RUN_RATE_WINDOW);
        Deque<Instant> history = userRunHistory.computeIfAbsent(userId, ignored -> new ArrayDeque<>());

        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst().isBefore(cutoff)) {
                history.removeFirst();
            }
            if (history.size() >= MAX_RUNS_PER_WINDOW) {
                throw new RunRateLimitException(
                        "Run limit reached. Please wait before running again."
                );
            }
            history.addLast(now);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RunContext(User user, Contest contest, Problem problem) {
    }

    private record RunCase(
            String caseType,
            Long caseId,
            String label,
            String input,
            String expectedOutput,
            boolean custom,
            TestCase officialTestCase
    ) {
    }

    private record InputValidation(boolean valid, String status, String message) {
        static InputValidation accepted() {
            return new InputValidation(true, null, null);
        }

        static InputValidation invalidInput(String message) {
            return new InputValidation(false, "VALIDATION_ERROR", message);
        }

        static InputValidation validatorError(String message) {
            return new InputValidation(false, "INTERNAL_ERROR", message);
        }
    }
}
