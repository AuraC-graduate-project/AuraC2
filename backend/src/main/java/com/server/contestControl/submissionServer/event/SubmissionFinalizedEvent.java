package com.server.contestControl.submissionServer.event;

import com.server.contestControl.submissionServer.enums.Verdict;

public record SubmissionFinalizedEvent(
        Long submissionId,
        Long contestId,
        Long problemId,
        Long userId,
        Verdict verdict
) {
}
