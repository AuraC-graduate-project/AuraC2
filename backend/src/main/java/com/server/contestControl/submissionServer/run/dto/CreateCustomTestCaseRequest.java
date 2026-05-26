package com.server.contestControl.submissionServer.run.dto;

public record CreateCustomTestCaseRequest(
        String input,
        String expectedOutput
) {
}
