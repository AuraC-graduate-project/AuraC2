package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.problem.ProblemRequest;
import com.server.contestControl.contestServer.dto.problem.ProblemResponse;
import com.server.contestControl.contestServer.dto.problem.ProblemUpdateRequest;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.exceptions.ContestNotFoundException;
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
        if (request.getBalloonColor() != null && !request.getBalloonColor().isBlank()) {
            problem.setBalloonColor(ProblemBalloonColors.normalize(request.getBalloonColor()));
        }

        problemRepository.save(problem);
        return ProblemResponse.from(problem, problemIndex(problem));
    }

    public ProblemResponse getProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));

        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .timeLimit(problem.getTimeLimit())
                .memoryLimit(problem.getMemoryLimit())
                .difficulty(problem.getDifficulty().name())
                .contestId(problem.getContest().getId())
                .balloonColor(ProblemBalloonColors.valueOrFallback(problem.getBalloonColor(), problemIndex(problem)))
                .build();
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
