package com.server.contestControl.submissionServer.run.dto;

public record InlineCustomTestCaseRequest(
        String input,
        String expectedOutput
) {
}
