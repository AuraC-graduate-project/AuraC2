package com.server.contestControl.contestServer.oracle.dto;

import com.server.contestControl.contestServer.oracle.entity.GeneratedTestCase;

import java.time.Instant;
import java.time.ZoneId;

public record GeneratedTestCaseResponse(
        Long id,
        Long batchId,
        Long problemId,
        Integer testNumber,
        Long seed,
        String inputData,
        String referenceOutput,
        String status,
        Boolean promoted,
        Long promotedTestCaseId,
        String diagnostic,
        Instant createdAt
) {
    public static GeneratedTestCaseResponse from(GeneratedTestCase testCase) {
        return new GeneratedTestCaseResponse(
                testCase.getId(),
                testCase.getBatch().getId(),
                testCase.getProblem().getId(),
                testCase.getTestNumber(),
                testCase.getSeed(),
                testCase.getInputData(),
                testCase.getReferenceOutput(),
                testCase.getStatus().name(),
                testCase.getPromoted(),
                testCase.getPromotedTestCase() == null ? null : testCase.getPromotedTestCase().getId(),
                testCase.getDiagnostic(),
                testCase.getCreatedAt() == null
                        ? null
                        : testCase.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
        );
    }
}
