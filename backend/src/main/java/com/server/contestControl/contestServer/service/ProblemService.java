package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.problem.ProblemRequest;
import com.server.contestControl.contestServer.dto.problem.ProblemResponse;
import com.server.contestControl.contestServer.dto.problem.ProblemUpdateRequest;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.exceptions.ContestNotFoundException;
import com.server.contestControl.contestServer.exceptions.InvalidComparePolicyException;
import com.server.contestControl.contestServer.exceptions.InvalidDifficultyException;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.repository.ClarificationRepository;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.util.ProblemBalloonColors;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.repository.SubmissionJudgeResultRepository;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final ContestRepository contestRepository;
    private final ClarificationRepository clarificationRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionJudgeResultRepository submissionJudgeResultRepository;

    @Transactional
    public ProblemResponse createProblem(ProblemRequest request) {

        Contest contest = contestRepository.findById(request.getContestId())
                .orElseThrow(() -> new ContestNotFoundException(request.getContestId()));
        int nextProblemIndex = Math.toIntExact(problemRepository.countByContest_Id(contest.getId()));

        Problem problem = Problem.builder()
                .contest(contest)
                .title(request.getTitle())
                .description(request.getDescription())
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

        problemRepository.save(problem);

        return ProblemResponse.from(problem, nextProblemIndex);
    }

    @Transactional
    public ProblemResponse updateProblem(Long id, ProblemUpdateRequest request) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        problem.setTitle(request.getTitle());
        problem.setDescription(request.getDescription());
        problem.setTimeLimit(request.getTimeLimit());
        problem.setMemoryLimit(request.getMemoryLimit());
        problem.setDifficulty(parseDifficulty(request.getDifficulty()));
        ComparePolicy comparePolicy = parseComparePolicyOrExisting(request.getComparePolicy(), problem.getComparePolicy());
        Double floatAbsoluteEpsilon = request.getFloatAbsoluteEpsilon();
        Double floatRelativeEpsilon = request.getFloatRelativeEpsilon();
        if (comparePolicy == ComparePolicy.FLOAT_TOLERANCE) {
            floatAbsoluteEpsilon = floatAbsoluteEpsilon == null
                    ? problem.getFloatAbsoluteEpsilon()
                    : floatAbsoluteEpsilon;
            floatRelativeEpsilon = floatRelativeEpsilon == null
                    ? problem.getFloatRelativeEpsilon()
                    : floatRelativeEpsilon;
        }
        applyCompareSettings(problem, comparePolicy, floatAbsoluteEpsilon, floatRelativeEpsilon);
        if (request.getBalloonColor() != null && !request.getBalloonColor().isBlank()) {
            problem.setBalloonColor(ProblemBalloonColors.normalize(request.getBalloonColor()));
        }

        problemRepository.save(problem);
        return ProblemResponse.from(problem, problemIndex(problem));
    }

    public ProblemResponse getProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        return ProblemResponse.from(problem, problemIndex(problem));
    }


    public Problem getProblemEntity(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        return problem;
    }

    public List<ProblemResponse> getAllProblems(Long contestId) {
        List<Problem> problems = problemRepository.findByContest_IdOrderByIdAsc(contestId);
        return java.util.stream.IntStream.range(0, problems.size())
                .mapToObj(index -> ProblemResponse.from(problems.get(index), index))
                .toList();
    }

    @Transactional
    public void deleteProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        List<Long> submissionIds = submissionRepository.findAllByProblem_Id(id).stream()
                .map(Submission::getId)
                .toList();

        if (!submissionIds.isEmpty()) {
            submissionJudgeResultRepository.deleteAllBySubmissionIds(submissionIds);
            submissionRepository.deleteAllByIdInBatch(submissionIds);
        }

        clarificationRepository.deleteAllByProblem_Id(id);
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

    private void applyCompareSettings(
            Problem problem,
            ComparePolicy comparePolicy,
            Double floatAbsoluteEpsilon,
            Double floatRelativeEpsilon
    ) {
        validateEpsilon("floatAbsoluteEpsilon", floatAbsoluteEpsilon);
        validateEpsilon("floatRelativeEpsilon", floatRelativeEpsilon);

        if (comparePolicy != ComparePolicy.FLOAT_TOLERANCE
                && (floatAbsoluteEpsilon != null || floatRelativeEpsilon != null)) {
            throw new InvalidComparePolicyException(
                    "Floating-point epsilon values are only valid for FLOAT_TOLERANCE compare policy"
            );
        }

        if (comparePolicy == ComparePolicy.FLOAT_TOLERANCE
                && !hasPositiveEpsilon(floatAbsoluteEpsilon, floatRelativeEpsilon)) {
            throw new InvalidComparePolicyException(
                    "FLOAT_TOLERANCE requires a positive absolute or relative epsilon"
            );
        }

        problem.setComparePolicy(comparePolicy);
        problem.setFloatAbsoluteEpsilon(
                comparePolicy == ComparePolicy.FLOAT_TOLERANCE ? floatAbsoluteEpsilon : null
        );
        problem.setFloatRelativeEpsilon(
                comparePolicy == ComparePolicy.FLOAT_TOLERANCE ? floatRelativeEpsilon : null
        );
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

    private int problemIndex(Problem problem) {
        List<Problem> problems = problemRepository.findByContest_IdOrderByIdAsc(problem.getContest().getId());
        for (int i = 0; i < problems.size(); i++) {
            if (problems.get(i).getId().equals(problem.getId())) {
                return i;
            }
        }
        return -1;
    }
}
