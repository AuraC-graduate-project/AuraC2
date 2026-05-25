package com.server.contestControl.contestServer.oracle.dto;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.oracle.entity.InputGenerator;
import com.server.contestControl.contestServer.oracle.entity.InputValidator;
import com.server.contestControl.contestServer.oracle.entity.ReferenceSolution;
import com.server.contestControl.submissionServer.language.SupportedLanguageCatalog;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record OracleProgramSourceResponse(
        Long id,
        Long problemId,
        String kind,
        Integer languageId,
        String source,
        String sourceHash,
        Boolean active,
        String fileName,
        Instant createdAt,
        Instant updatedAt
) {
    public static OracleProgramSourceResponse from(ReferenceSolution solution) {
        return new OracleProgramSourceResponse(
                solution.getId(),
                solution.getProblem().getId(),
                "REFERENCE_SOLUTION",
                solution.getLanguageId(),
                solution.getSource(),
                solution.getSourceHash(),
                solution.getActive(),
                fileName(solution.getLanguageId(), "reference"),
                toInstant(solution.getCreatedAt()),
                toInstant(solution.getUpdatedAt())
        );
    }

    public static OracleProgramSourceResponse from(InputGenerator generator) {
        return new OracleProgramSourceResponse(
                generator.getId(),
                generator.getProblem().getId(),
                "INPUT_GENERATOR",
                generator.getLanguageId(),
                generator.getSource(),
                generator.getSourceHash(),
                generator.getActive(),
                fileName(generator.getLanguageId(), "generator"),
                toInstant(generator.getCreatedAt()),
                toInstant(generator.getUpdatedAt())
        );
    }

    public static OracleProgramSourceResponse from(InputValidator validator) {
        return new OracleProgramSourceResponse(
                validator.getId(),
                validator.getProblem().getId(),
                "INPUT_VALIDATOR",
                validator.getLanguageId(),
                validator.getSource(),
                validator.getSourceHash(),
                validator.getActive(),
                fileName(validator.getLanguageId(), "validator"),
                toInstant(validator.getCreatedAt()),
                toInstant(validator.getUpdatedAt())
        );
    }

    public static OracleProgramSourceResponse customValidator(Problem problem) {
        return new OracleProgramSourceResponse(
                problem.getId(),
                problem.getId(),
                "CUSTOM_OUTPUT_VALIDATOR",
                problem.getValidatorLanguageId(),
                problem.getValidatorSource(),
                problem.getValidatorSourceHash(),
                problem.hasActiveCustomValidator(),
                fileName(problem.getValidatorLanguageId(), "checker"),
                toInstant(problem.getValidatorCreatedAt()),
                toInstant(problem.getValidatorUpdatedAt())
        );
    }

    private static String fileName(Integer languageId, String kind) {
        return SupportedLanguageCatalog.findByJudge0LanguageId(languageId)
                .map(language -> switch (kind) {
                    case "reference" -> language.referenceFileName();
                    case "generator" -> language.generatorFileName();
                    case "validator" -> language.validatorFileName();
                    case "checker" -> language.checkerFileName();
                    default -> kind + ".txt";
                })
                .orElse(kind + ".txt");
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
