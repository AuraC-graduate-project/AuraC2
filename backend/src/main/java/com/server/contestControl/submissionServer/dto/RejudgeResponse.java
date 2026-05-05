package com.server.contestControl.submissionServer.dto;

import java.util.List;

public record RejudgeResponse(
        String scope,
        Long scopeId,
        int requestedCount,
        int foundCount,
        int queuedCount,
        int skippedCount,
        List<Long> queuedSubmissionIds,
        List<Long> skippedSubmissionIds,
        List<Long> missingSubmissionIds
) {
}
