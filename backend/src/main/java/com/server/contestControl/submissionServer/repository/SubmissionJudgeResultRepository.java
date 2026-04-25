package com.server.contestControl.submissionServer.repository;

import com.server.contestControl.submissionServer.entity.SubmissionJudgeResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionJudgeResultRepository extends JpaRepository<SubmissionJudgeResult, Long> {

    Optional<SubmissionJudgeResult> findBySubmission_IdAndJudgeRunIdAndTestCaseNumber(
            Long submissionId,
            Long judgeRunId,
            Integer testCaseNumber
    );

    long countBySubmission_IdAndJudgeRunId(Long submissionId, Long judgeRunId);

    List<SubmissionJudgeResult> findBySubmission_IdAndJudgeRunId(Long submissionId, Long judgeRunId);
}
