package com.server.contestControl.submissionServer.sse;

import com.server.contestControl.submissionServer.enums.Verdict;

import java.time.LocalDateTime;

/**
 * Wire payload sent to SSE clients on every submission status change.
 * Null fields (e.g. executionTime before judging completes) are included
 * so the frontend can treat any event as a full snapshot of the submission.
 */
public record SubmissionStreamEvent(
        SubmissionStreamEventType eventType,
        Long submissionId,
        Long contestId,
        Long problemId,
        Long userId,
        String username,
        Verdict verdict,
        Long judgeRunId,
        Integer executionTime,
        Integer memoryUsage,
        LocalDateTime createdAt,
        LocalDateTime occurredAt
) {
}
