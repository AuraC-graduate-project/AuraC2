package com.server.contestControl.contestServer.oracle.dto;

import com.server.contestControl.contestServer.oracle.entity.Counterexample;
import com.server.contestControl.submissionServer.enums.Verdict;

import java.time.Instant;
import java.time.ZoneId;

public record CounterexampleResponse(
        Long id,
        Long problemId,
        Long submissionId,
        Long generatedTestCaseId,
        Long judgeRunId,
        String generatedInput,
        String referenceOutput,
        String teamOutput,
        Verdict verdict,
        String comparePolicy,
        String validationMode,
        String diagnostic,
        Boolean promoted,
        Long promotedTestCaseId,
        Instant createdAt
) {
    public static CounterexampleResponse from(Counterexample counterexample) {
        return new CounterexampleResponse(
                counterexample.getId(),
                counterexample.getProblem().getId(),
                counterexample.getSubmission().getId(),
                counterexample.getGeneratedTestCase().getId(),
                counterexample.getJudgeRunId(),
                counterexample.getGeneratedInput(),
                counterexample.getReferenceOutput(),
                counterexample.getTeamOutput(),
                counterexample.getVerdict(),
                counterexample.getComparePolicy() == null ? null : counterexample.getComparePolicy().name(),
                counterexample.getValidationMode() == null ? null : counterexample.getValidationMode().name(),
                counterexample.getDiagnostic(),
                counterexample.getPromoted(),
                counterexample.getPromotedTestCase() == null ? null : counterexample.getPromotedTestCase().getId(),
                counterexample.getCreatedAt() == null
                        ? null
                        : counterexample.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
        );
    }
}
