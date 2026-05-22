package com.server.contestControl.contestServer.dto.testcase;

import com.server.contestControl.contestServer.entity.TestCase;

public record PublicTestCaseResponse(
        Long id,
        Long problemId,
        String inputData,
        String expectedOutput,
        boolean isPublic
) {

    public static PublicTestCaseResponse fromEntity(TestCase entity) {
        return new PublicTestCaseResponse(
                entity.getId(),
                entity.getProblem().getId(),
                entity.getInputData(),
                entity.getExpectedOutput(),
                entity.isPublic()
        );
    }
}
