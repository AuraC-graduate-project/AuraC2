package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.problem.ProblemRequest;
import com.server.contestControl.contestServer.dto.problem.ProblemResponse;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final ContestRepository contestRepository;

    public ProblemResponse createProblem(ProblemRequest request) {

        Contest contest = contestRepository.findById(request.getContestId())
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        Problem problem = Problem.builder()
                .contest(contest)
                .title(request.getTitle())
                .description(request.getDescription())
                .timeLimit(request.getTimeLimit())
                .memoryLimit(request.getMemoryLimit())
                .difficulty(Difficulty.fromString(request.getDifficulty()))
                .build();

        problemRepository.save(problem);

        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .timeLimit(problem.getTimeLimit())
                .memoryLimit(problem.getMemoryLimit())
                .difficulty(problem.getDifficulty().name())
                .contestId(contest.getId())
                .build();
    }

    public ProblemResponse getProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Problem not found"));

        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .timeLimit(problem.getTimeLimit())
                .memoryLimit(problem.getMemoryLimit())
                .difficulty(problem.getDifficulty().name())
                .contestId(problem.getContest().getId())
                .build();
    }


    public Problem getProblemEntity(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Problem not found"));

        return problem;
    }

    public List<ProblemResponse> getAllProblems(Long contestId) {

        List<Problem> problems = problemRepository.findAllByContest_id(contestId);

        if (problems.isEmpty()) {
            throw new RuntimeException("No problems found for contest id " + contestId);
        }

        return problems.stream()
                .map(ProblemResponse::from)
                .toList();
    }
}
