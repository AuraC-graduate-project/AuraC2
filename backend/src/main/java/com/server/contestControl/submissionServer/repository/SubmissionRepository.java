package com.server.contestControl.submissionServer.repository;

import com.server.contestControl.submissionServer.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> getAllByUser_idAndProblemId(Long userId, Long problemId);
    List<Submission> getAllByUser_id(Long userId);

}
