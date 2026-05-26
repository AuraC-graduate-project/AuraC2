package com.server.contestControl.submissionServer.run.dto;

public record UpdateCustomTestCaseRequest(
        String input,
        String expectedOutput
) {
}
