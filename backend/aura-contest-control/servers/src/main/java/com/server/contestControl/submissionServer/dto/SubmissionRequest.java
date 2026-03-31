package com.server.contestControl.submissionServer.dto;

public record SubmissionRequest(
        Long contestId,
        Long problemId,
        String language,
        String code
) { }