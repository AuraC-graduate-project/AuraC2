package com.server.contestControl.submissionServer.run.repository;

import com.server.contestControl.submissionServer.run.entity.UserCustomTestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserCustomTestCaseRepository extends JpaRepository<UserCustomTestCase, Long> {

    List<UserCustomTestCase> findByContest_IdAndProblem_IdAndOwner_IdOrderByCreatedAtAscIdAsc(
            Long contestId,
            Long problemId,
            Long ownerId
    );

    List<UserCustomTestCase> findByIdInAndContest_IdAndProblem_IdAndOwner_IdOrderByCreatedAtAscIdAsc(
            Collection<Long> ids,
            Long contestId,
            Long problemId,
            Long ownerId
    );

    Optional<UserCustomTestCase> findByIdAndContest_IdAndProblem_IdAndOwner_Id(
            Long id,
            Long contestId,
            Long problemId,
            Long ownerId
    );

    long countByContest_IdAndProblem_IdAndOwner_Id(Long contestId, Long problemId, Long ownerId);
}
