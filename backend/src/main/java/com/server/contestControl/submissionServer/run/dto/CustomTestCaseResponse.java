package com.server.contestControl.submissionServer.run.dto;

import com.server.contestControl.submissionServer.run.entity.UserCustomTestCase;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record CustomTestCaseResponse(
        Long id,
        Long contestId,
        Long problemId,
        String input,
        String expectedOutput,
        String createdAt,
        String updatedAt
) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public static CustomTestCaseResponse from(UserCustomTestCase testCase) {
        return new CustomTestCaseResponse(
                testCase.getId(),
                testCase.getContest().getId(),
                testCase.getProblem().getId(),
                testCase.getInputData(),
                testCase.getExpectedOutput(),
                testCase.getCreatedAt() == null
                        ? null
                        : FORMATTER.format(testCase.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()),
                testCase.getUpdatedAt() == null
                        ? null
                        : FORMATTER.format(testCase.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant())
        );
    }
}
