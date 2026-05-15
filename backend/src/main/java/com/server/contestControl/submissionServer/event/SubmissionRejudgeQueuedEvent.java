package com.server.contestControl.submissionServer.event;

public record SubmissionRejudgeQueuedEvent(
        Long submissionId,
        Long contestId,
        Long problemId,
        Long userId
) {
}
