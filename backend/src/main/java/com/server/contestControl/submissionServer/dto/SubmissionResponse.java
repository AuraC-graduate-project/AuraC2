package com.server.contestControl.submissionServer.dto;

import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;

import java.time.Instant;
import java.time.ZoneId;

public record SubmissionResponse(
        Long id,
        Long contestId,
        Long problemId,
        String problemTitle,
        Long userId,
        String language,
        String code,
        Verdict verdict,
        Instant createdAt,
        Integer executionTime,
        Integer memoryUsage
) {
    public static SubmissionResponse fromEntity(Submission submission) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getContest().getId(),
                submission.getProblem().getId(),
                submission.getProblem().getTitle(),
                submission.getUser().getId(),
                submission.getLanguage(),
                submission.getCode(),
                submission.getVerdict(),
                submission.getCreatedAt() == null
                        ? null
                        : submission.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                submission.getExecutionTime(),
                submission.getMemoryUsage()
        );
    }


}
