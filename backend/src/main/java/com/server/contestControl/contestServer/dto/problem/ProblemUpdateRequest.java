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

    private String statement;

    private String inputFormat;

    private String outputFormat;

    private String constraintsText;

    private String publicNotes;

    private String adminNotes;

    @NotNull
    @Positive
    private Integer timeLimit;

    @NotNull
    @Positive
    private Integer memoryLimit;

    @NotBlank
    private String difficulty;   // EASY, MEDIUM, HARD

    private String comparePolicy;

    private Double floatAbsoluteEpsilon;

    private Double floatRelativeEpsilon;

    private String validationMode;

    private Integer validatorLanguageId;

    private String validatorSource;

    private Boolean validatorEnabled;

    private String balloonColor;
}
