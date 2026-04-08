package com.server.contestControl.contestServer.dto.problem;

import lombok.Data;

@Data
public class ProblemRequest {
    private Long contestId;
    private String title;
    private String description;
    private Integer timeLimit;
    private Integer memoryLimit;
    private String difficulty;   // EASY, MEDIUM, HARD
}