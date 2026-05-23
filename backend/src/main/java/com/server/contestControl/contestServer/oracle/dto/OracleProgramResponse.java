package com.server.contestControl.contestServer.oracle.dto;

import com.server.contestControl.contestServer.oracle.entity.InputGenerator;
import com.server.contestControl.contestServer.oracle.entity.InputValidator;
import com.server.contestControl.contestServer.oracle.entity.ReferenceSolution;

import java.time.Instant;
import java.time.ZoneId;

public record OracleProgramResponse(
        Long id,
        Long problemId,
        Integer languageId,
        String sourceHash,
        Boolean active,
        Integer defaultTestCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static OracleProgramResponse from(ReferenceSolution solution) {
        return new OracleProgramResponse(
                solution.getId(),
                solution.getProblem().getId(),
                solution.getLanguageId(),
                solution.getSourceHash(),
                solution.getActive(),
                null,
                toInstant(solution.getCreatedAt()),
                toInstant(solution.getUpdatedAt())
        );
    }

    public static OracleProgramResponse from(InputGenerator generator) {
        return new OracleProgramResponse(
                generator.getId(),
                generator.getProblem().getId(),
                generator.getLanguageId(),
                generator.getSourceHash(),
                generator.getActive(),
                generator.getDefaultTestCount(),
                toInstant(generator.getCreatedAt()),
                toInstant(generator.getUpdatedAt())
        );
    }

    public static OracleProgramResponse from(InputValidator validator) {
        return new OracleProgramResponse(
                validator.getId(),
                validator.getProblem().getId(),
                validator.getLanguageId(),
                validator.getSourceHash(),
                validator.getActive(),
                null,
                toInstant(validator.getCreatedAt()),
                toInstant(validator.getUpdatedAt())
        );
    }

    private static Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
