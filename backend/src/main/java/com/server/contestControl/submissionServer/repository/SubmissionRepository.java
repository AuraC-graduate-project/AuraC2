package com.server.contestControl.submissionServer.repository;

import com.server.contestControl.submissionServer.entity.Submission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> getAllByUser_idAndProblemId(Long userId, Long problemId);
    List<Submission> getAllByUser_id(Long userId);
    List<Submission> findAllByProblem_Id(Long problemId);
    List<Submission> findAllByContest_Id(Long contestId);

    @Query("""
            select submission from Submission submission
            join fetch submission.contest
            join fetch submission.problem
            join fetch submission.user
            where submission.contest.id = :contestId
            order by submission.createdAt asc, submission.id asc
            """)
    List<Submission> findAllByContestIdForScoreboard(@Param("contestId") Long contestId);

    @Query("""
            select submission from Submission submission
            join fetch submission.contest
            join fetch submission.problem
            join fetch submission.user
            where submission.id = :id
            """)
    Optional<Submission> findByIdWithContestProblemUser(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select submission from Submission submission where submission.id = :id")
    Optional<Submission> findByIdForUpdate(@Param("id") Long id);
}
