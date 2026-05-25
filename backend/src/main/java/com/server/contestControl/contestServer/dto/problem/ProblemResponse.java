package com.server.contestControl.contestServer.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.util.ProblemBalloonColors;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProblemResponse {

    private Long id;
    private String title;
    private String description;
    private String statement;
    private String inputFormat;
    private String outputFormat;
    private String constraintsText;
    private String publicNotes;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String adminNotes;
    private Integer timeLimit;
    private Integer memoryLimit;
    private String difficulty;
    private String comparePolicy;
    private Double floatAbsoluteEpsilon;
    private Double floatRelativeEpsilon;
    private String validationMode;
    private Boolean validatorEnabled;
    private Integer validatorLanguageId;
    private String validatorSourceHash;
    private Long contestId;
    private String balloonColor;

    public static ProblemResponse from(Problem problem) {
        return from(problem, -1);
    }

    public static ProblemResponse from(Problem problem, int problemIndex) {
        return from(problem, problemIndex, false);
    }

    public static ProblemResponse from(Problem problem, int problemIndex, boolean includeAdminFields) {
        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .statement(effectiveStatement(problem))
                .inputFormat(problem.getInputFormat())
                .outputFormat(problem.getOutputFormat())
                .constraintsText(problem.getConstraintsText())
                .publicNotes(problem.getPublicNotes())
                .adminNotes(includeAdminFields ? problem.getAdminNotes() : null)
                .timeLimit(problem.getTimeLimit())
                .memoryLimit(problem.getMemoryLimit())
                .difficulty(problem.getDifficulty().name())
                .comparePolicy(problem.getComparePolicy().name())
                .floatAbsoluteEpsilon(problem.getFloatAbsoluteEpsilon())
                .floatRelativeEpsilon(problem.getFloatRelativeEpsilon())
                .validationMode(problem.getValidationMode().name())
                .validatorEnabled(problem.getValidatorEnabled())
                .validatorLanguageId(problem.getValidatorLanguageId())
                .validatorSourceHash(problem.getValidatorSourceHash())
                .contestId(problem.getContest().getId())
                .balloonColor(problemIndex >= 0
                        ? ProblemBalloonColors.valueOrFallback(problem.getBalloonColor(), problemIndex)
                        : ProblemBalloonColors.valueOrDefault(problem.getBalloonColor()))
                .build();
    }

    private static String effectiveStatement(Problem problem) {
        return hasText(problem.getStatement()) ? problem.getStatement() : problem.getDescription();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
