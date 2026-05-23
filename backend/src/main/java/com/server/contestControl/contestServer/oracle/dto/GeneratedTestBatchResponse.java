package com.server.contestControl.contestServer.oracle.dto;

import com.server.contestControl.contestServer.oracle.entity.GeneratedTestBatch;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public record GeneratedTestBatchResponse(
        Long id,
        Long problemId,
        Long seed,
        String generatorSourceHash,
        String referenceSolutionSourceHash,
        String status,
        Integer requestedCount,
        Integer generatedCount,
        Integer invalidCount,
        Integer counterexampleCount,
        String diagnostic,
        Instant createdAt,
        Instant completedAt,
        List<GeneratedTestCaseResponse> testCases
) {
    public static GeneratedTestBatchResponse from(
            GeneratedTestBatch batch,
            List<GeneratedTestCaseResponse> testCases
    ) {
        return new GeneratedTestBatchResponse(
                batch.getId(),
                batch.getProblem().getId(),
                batch.getSeed(),
                batch.getGeneratorSourceHash(),
                batch.getReferenceSolutionSourceHash(),
                batch.getStatus().name(),
                batch.getRequestedCount(),
                batch.getGeneratedCount(),
                batch.getInvalidCount(),
                batch.getCounterexampleCount(),
                batch.getDiagnostic(),
                toInstant(batch.getCreatedAt()),
                toInstant(batch.getCompletedAt()),
                testCases == null ? List.of() : testCases
        );
    }

    private static Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
