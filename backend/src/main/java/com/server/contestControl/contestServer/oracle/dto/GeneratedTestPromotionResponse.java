package com.server.contestControl.contestServer.oracle.dto;

import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;

import java.util.List;

public record GeneratedTestPromotionResponse(
        int requestedCount,
        int promotedCount,
        int alreadyPromotedCount,
        int skippedDuplicateCount,
        int skippedInvalidCount,
        List<TestCaseResponse> promotedTestCases,
        List<GeneratedTestCaseResponse> skippedCandidates,
        String message
) {
}
