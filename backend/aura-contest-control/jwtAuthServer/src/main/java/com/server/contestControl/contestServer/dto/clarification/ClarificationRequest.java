package com.server.contestControl.contestServer.dto.clarification;

public record ClarificationRequest(
        Long contestId,
        Long problemId,  // Optional: null for general contest questions
        String question
) {
}
