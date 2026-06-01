package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.problem.ProblemRequest;
import com.server.contestControl.contestServer.dto.problem.ProblemResponse;
import com.server.contestControl.contestServer.dto.problem.ProblemUpdateRequest;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.enums.ValidationMode;
import com.server.contestControl.contestServer.exceptions.ContestNotFoundException;
import com.server.contestControl.contestServer.exceptions.InvalidComparePolicyException;
import com.server.contestControl.contestServer.exceptions.InvalidDifficultyException;
import com.server.contestControl.contestServer.exceptions.InvalidValidatorConfigurationException;
import com.server.contestControl.contestServer.exceptions.ProblemDeletionConflictException;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
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
import com.server.contestControl.contestServer.util.ProblemBalloonColors;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final ContestRepository contestRepository;
    private final ClarificationRepository clarificationRepository;
    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final ScoreboardRevealCellRepository scoreboardRevealCellRepository;
    private final GeneratedTestBatchRepository generatedTestBatchRepository;
    private final CounterexampleRepository counterexampleRepository;
    private final ReferenceSolutionRepository referenceSolutionRepository;
    private final InputGeneratorRepository inputGeneratorRepository;
    private final InputValidatorRepository inputValidatorRepository;

    @Transactional
    public ProblemResponse createProblem(ProblemRequest request) {

        Contest contest = contestRepository.findById(request.getContestId())
                .orElseThrow(() -> new ContestNotFoundException(request.getContestId()));
        int nextProblemIndex = Math.toIntExact(problemRepository.countByContest_Id(contest.getId()));

        Problem problem = Problem.builder()
                .contest(contest)
                .title(request.getTitle())
                .description(request.getDescription())
                .statement(blankToNull(request.getStatement()))
                .inputFormat(blankToNull(request.getInputFormat()))
                .outputFormat(blankToNull(request.getOutputFormat()))
                .constraintsText(blankToNull(request.getConstraintsText()))
                .publicNotes(blankToNull(request.getPublicNotes()))
                .adminNotes(blankToNull(request.getAdminNotes()))
                .timeLimit(request.getTimeLimit())
                .memoryLimit(request.getMemoryLimit())
                .difficulty(parseDifficulty(request.getDifficulty()))
                .balloonColor(ProblemBalloonColors.normalizeOrFallback(request.getBalloonColor(), nextProblemIndex))
                .build();
        applyCompareSettings(
                problem,
                parseComparePolicyOrDefault(request.getComparePolicy()),
                request.getFloatAbsoluteEpsilon(),
                request.getFloatRelativeEpsilon()
        );
        applyValidatorSettings(
                problem,
                parseValidationModeForCreate(request.getValidationMode()),
                request.getValidatorEnabled(),
                request.getValidatorLanguageId(),
                request.getValidatorSource(),
                false
        );

        problemRepository.save(problem);

        return ProblemResponse.from(problem, nextProblemIndex, true);
    }

    @Transactional
    public ProblemResponse updateProblem(Long id, ProblemUpdateRequest request) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        problem.setTitle(request.getTitle());
        problem.setDescription(request.getDescription());
        problem.setStatement(blankToNull(request.getStatement()));
        problem.setInputFormat(blankToNull(request.getInputFormat()));
        problem.setOutputFormat(blankToNull(request.getOutputFormat()));
        problem.setConstraintsText(blankToNull(request.getConstraintsText()));
        problem.setPublicNotes(blankToNull(request.getPublicNotes()));
        problem.setAdminNotes(blankToNull(request.getAdminNotes()));
        problem.setTimeLimit(request.getTimeLimit());
        problem.setMemoryLimit(request.getMemoryLimit());
        problem.setDifficulty(parseDifficulty(request.getDifficulty()));

        ComparePolicy comparePolicy = parseComparePolicyOrExisting(request.getComparePolicy(), problem.getComparePolicy());
        Double floatAbsoluteEpsilon = request.getFloatAbsoluteEpsilon();
        Double floatRelativeEpsilon = request.getFloatRelativeEpsilon();


        applyCompareSettings(problem, comparePolicy, floatAbsoluteEpsilon, floatRelativeEpsilon);
        applyValidatorSettings(
                problem,
                parseValidationModeForUpdate(request.getValidationMode(), problem.getValidationMode()),
                request.getValidatorEnabled(),
                request.getValidatorLanguageId(),
                request.getValidatorSource(),
                true
        );
        if (request.getBalloonColor() != null && !request.getBalloonColor().isBlank()) {
            problem.setBalloonColor(ProblemBalloonColors.normalize(request.getBalloonColor()));
        }

        problemRepository.save(problem);
        return ProblemResponse.from(problem, problemIndex(problem), true);
    }


    public ProblemResponse getProblem(Long id, boolean includeAdminFields) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        return ProblemResponse.from(problem, problemIndex(problem), includeAdminFields);
    }


    public Problem getProblemEntity(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        return problem;
    }

    public List<ProblemResponse> getAllProblems(Long contestId, boolean includeAdminFields) {
        List<Problem> problems = problemRepository.findByContest_IdOrderByIdAsc(contestId);
        return IntStream.range(0, problems.size())
                .mapToObj(index -> ProblemResponse.from(problems.get(index), index, includeAdminFields))
                .toList();
    }

    @Transactional
    public void deleteProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        if (submissionRepository.existsByProblem_Id(id)) {
            throw new ProblemDeletionConflictException(id, "submissions or judging history exist");
        }
        if (scoreboardRevealCellRepository.existsByProblem_Id(id)) {
            throw new ProblemDeletionConflictException(id, "scoreboard reveal history exists");
        }
        if (clarificationRepository.existsByProblem_Id(id)) {
            throw new ProblemDeletionConflictException(id, "clarifications reference this problem");
        }
        if (generatedTestBatchRepository.existsByProblem_Id(id)) {
            throw new ProblemDeletionConflictException(id, "generated oracle tests exist");
        }
        if (counterexampleRepository.existsByProblem_Id(id)) {
            throw new ProblemDeletionConflictException(id, "counterexamples exist");
        }

        referenceSolutionRepository.deleteByProblem_Id(id);
        inputGeneratorRepository.deleteByProblem_Id(id);
        inputValidatorRepository.deleteByProblem_Id(id);
        testCaseRepository.deleteByProblem_Id(id);
        problemRepository.delete(problem);
    }

    private Difficulty parseDifficulty(String value) {
        try {
            return Difficulty.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidDifficultyException(value);
        }
    }

    private ComparePolicy parseComparePolicyOrDefault(String value) {
        return parseComparePolicy(value, ComparePolicy.EXACT);
    }

    private ComparePolicy parseComparePolicyOrExisting(String value, ComparePolicy existing) {
        return parseComparePolicy(value, existing == null ? ComparePolicy.EXACT : existing);
    }

    private ComparePolicy parseComparePolicy(String value, ComparePolicy fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return ComparePolicy.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidComparePolicyException(
                    "Invalid compare policy: " + value
                            + ". Valid values: EXACT, NORMALIZED_TEXT, TOKEN_NORMALIZED, FLOAT_TOLERANCE"
            );
        }
    }

    private ValidationMode parseValidationModeForCreate(String value) {
        return parseValidationMode(value, ValidationMode.BUILTIN_COMPARE_POLICY);
    }

    private ValidationMode parseValidationModeForUpdate(String value, ValidationMode existing) {
        return parseValidationMode(value, existing == null ? ValidationMode.BUILTIN_COMPARE_POLICY : existing);
    }

    private ValidationMode parseValidationMode(String value, ValidationMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return ValidationMode.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidValidatorConfigurationException(
                    "Invalid validation mode: " + value
                            + ". Valid values: BUILTIN_COMPARE_POLICY, CUSTOM_VALIDATOR"
            );
        }
    }

    private void applyCompareSettings(
            Problem problem,
            ComparePolicy comparePolicy,
            Double floatAbsoluteEpsilon,
            Double floatRelativeEpsilon
    ) {
        if (comparePolicy != ComparePolicy.FLOAT_TOLERANCE) {
            if (floatAbsoluteEpsilon != null || floatRelativeEpsilon != null) {
                throw new InvalidComparePolicyException(
                        "Floating-point epsilon values are only valid for FLOAT_TOLERANCE compare policy"
                );
            }

            problem.setComparePolicy(comparePolicy);
            problem.setFloatAbsoluteEpsilon(null);
            problem.setFloatRelativeEpsilon(null);
            return;
        }

        validateEpsilon("floatAbsoluteEpsilon", floatAbsoluteEpsilon);
        validateEpsilon("floatRelativeEpsilon", floatRelativeEpsilon);

        if (!hasPositiveEpsilon(floatAbsoluteEpsilon, floatRelativeEpsilon)) {
            throw new InvalidComparePolicyException(
                    "FLOAT_TOLERANCE requires a positive absolute or relative epsilon"
            );
        }

        problem.setComparePolicy(comparePolicy);
        problem.setFloatAbsoluteEpsilon(floatAbsoluteEpsilon);
        problem.setFloatRelativeEpsilon(floatRelativeEpsilon);
    }

    private void validateEpsilon(String field, Double value) {
        if (value == null) {
            return;
        }

        if (!Double.isFinite(value) || value < 0) {
            throw new InvalidComparePolicyException(field + " must be a finite non-negative number");
        }
    }

    private boolean hasPositiveEpsilon(Double... values) {
        for (Double value : values) {
            if (value != null && value > 0) {
                return true;
            }
        }
        return false;
    }

    private void applyValidatorSettings(
            Problem problem,
            ValidationMode validationMode,
            Boolean validatorEnabled,
            Integer validatorLanguageId,
            String validatorSource,
            boolean update
    ) {
        if (validationMode == ValidationMode.BUILTIN_COMPARE_POLICY) {
            clearValidatorSettings(problem);
            return;
        }

        boolean enabled = resolveValidatorEnabled(problem, validatorEnabled, update);
        Integer languageId = resolveValidatorLanguageId(problem, validatorLanguageId, update);
        String source = resolveValidatorSource(problem, validatorSource, update);

        validateCustomValidatorSettings(enabled, languageId, source);

        problem.setValidationMode(ValidationMode.CUSTOM_VALIDATOR);
        problem.setValidatorEnabled(enabled);
        problem.setValidatorLanguageId(languageId);

        if (hasText(source)) {
            updateValidatorSource(problem, source);
        }
    }

    private void clearValidatorSettings(Problem problem) {
        problem.setValidationMode(ValidationMode.BUILTIN_COMPARE_POLICY);
        problem.setValidatorEnabled(false);
        problem.setValidatorLanguageId(null);
        problem.setValidatorSource(null);
        problem.setValidatorSourceHash(null);
        problem.setValidatorCreatedAt(null);
        problem.setValidatorUpdatedAt(null);
    }

    private void validateValidatorLanguageId(Integer languageId) {
        if (languageId != null && languageId <= 0) {
            throw new InvalidValidatorConfigurationException("validatorLanguageId must be positive");
        }
    }

    private void updateValidatorSource(Problem problem, String source) {
        String hash = sha256Hex(source);
        if (!hash.equals(problem.getValidatorSourceHash())) {
            if (problem.getValidatorCreatedAt() == null) {
                problem.setValidatorCreatedAt(LocalDateTime.now());
            }
            problem.setValidatorUpdatedAt(LocalDateTime.now());
        }
        problem.setValidatorSource(source);
        problem.setValidatorSourceHash(hash);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private int problemIndex(Problem problem) {
        List<Problem> problems = problemRepository.findByContest_IdOrderByIdAsc(problem.getContest().getId());
        for (int i = 0; i < problems.size(); i++) {
            if (problems.get(i).getId().equals(problem.getId())) {
                return i;
            }
        }
        return -1;
    }

    private boolean resolveValidatorEnabled(Problem problem, Boolean validatorEnabled, boolean update) {
        if (validatorEnabled != null) {
            return validatorEnabled;
        }

        if (update && problem.getValidationMode() == ValidationMode.CUSTOM_VALIDATOR) {
            return Boolean.TRUE.equals(problem.getValidatorEnabled());
        }

        return true;
    }

    private Integer resolveValidatorLanguageId(Problem problem, Integer validatorLanguageId, boolean update) {
        if (validatorLanguageId != null) {
            return validatorLanguageId;
        }

        return update ? problem.getValidatorLanguageId() : null;
    }

    private String resolveValidatorSource(Problem problem, String validatorSource, boolean update) {
        if (hasText(validatorSource)) {
            return validatorSource;
        }

        return update ? problem.getValidatorSource() : null;
    }

    private void validateCustomValidatorSettings(boolean enabled, Integer languageId, String source) {
        validateValidatorLanguageId(languageId);

        if (enabled && !hasText(source)) {
            throw new InvalidValidatorConfigurationException(
                    "Enabled custom validators require validatorSource"
            );
        }

        if (enabled && languageId == null) {
            throw new InvalidValidatorConfigurationException(
                    "Enabled custom validators require validatorLanguageId"
            );
        }
    }

}
