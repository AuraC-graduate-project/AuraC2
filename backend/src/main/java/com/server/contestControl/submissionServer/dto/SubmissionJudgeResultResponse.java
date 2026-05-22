package com.server.contestControl.submissionServer.dto;

import com.server.contestControl.submissionServer.entity.SubmissionJudgeResult;
import com.server.contestControl.submissionServer.enums.Verdict;

import java.time.Instant;
import java.time.ZoneId;

public record SubmissionJudgeResultResponse(
        Long judgeRunId,
        Integer testCaseNumber,
        Integer judge0StatusId,
        String judge0StatusDescription,
        Verdict verdict,
        Integer executionTime,
        Integer memoryUsage,
        String diagnostic,
        Instant receivedAt
) {
    public static SubmissionJudgeResultResponse fromEntity(SubmissionJudgeResult result) {
        return new SubmissionJudgeResultResponse(
                result.getJudgeRunId(),
                result.getTestCaseNumber(),
                result.getJudge0StatusId(),
                result.getJudge0StatusDescription(),
                result.getVerdict(),
                result.getExecutionTime(),
                result.getMemoryUsage(),
                result.getDiagnostic(),
                result.getReceivedAt() == null
                        ? null
                        : result.getReceivedAt().atZone(ZoneId.systemDefault()).toInstant()
        );
    }
}
