package com.server.contestControl.submissionServer.run.dto;

public record RunCaseResult(
        Integer caseNumber,
        String caseType,
        Long caseId,
        String label,
        String input,
        String expectedOutput,
        String actualOutput,
        String stderr,
        String status,
        String diagnostic,
        Integer runtimeMillis,
        Integer memoryKb
) {
}
