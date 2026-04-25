package com.server.contestControl.contestServer.dto.problem;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.Difficulty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProblemResponse {

    private Long id;
    private String title;
    private String description;
    private Integer timeLimit;
    private Integer memoryLimit;
    private String difficulty;
    private Long contestId;

    public static ProblemResponse from(Problem problem) {
        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .timeLimit(problem.getTimeLimit())
                .memoryLimit(problem.getMemoryLimit())
                .difficulty(problem.getDifficulty().name()) // enum → String
                .contestId(problem.getContest().getId())
                .build();
    }
}