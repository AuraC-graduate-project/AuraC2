package com.server.contestControl.contestServer.dto.problem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProblemUpdateRequest {
    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    private String description;

    @NotNull
    @Positive
    private Integer timeLimit;

    @NotNull
    @Positive
    private Integer memoryLimit;

    @NotBlank
    private String difficulty;   // EASY, MEDIUM, HARD
}
