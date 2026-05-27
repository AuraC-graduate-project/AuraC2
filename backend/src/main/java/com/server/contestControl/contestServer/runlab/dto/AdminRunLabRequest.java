package com.server.contestControl.contestServer.runlab.dto;

public record AdminRunLabRequest(
        Long contestId,
        Long problemId,
        Integer languageId,
        String sourceCode,
        String customInput
) {
}
