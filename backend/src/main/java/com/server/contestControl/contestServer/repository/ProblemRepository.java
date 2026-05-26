package com.server.contestControl.contestServer.repository;


import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByContest_Id(Long contestId);
    List<Problem> findAllByContest_id(Long contestId);
    List<Problem> findByContest_IdOrderByIdAsc(Long contestId);
    long countByContest_Id(Long contestId);

    @Query("""
            select problem from Problem problem
            join fetch problem.contest
            where problem.id = :id
            """)
    Optional<Problem> findByIdWithContest(@Param("id") Long id);
}
