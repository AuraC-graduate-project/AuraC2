package com.server.contestControl.contestServer.repository;

import com.server.contestControl.contestServer.entity.TestCase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemId(Long problemId);
    List<TestCase> findByProblemIdOrderByIdAsc(Long problemId);
    List<TestCase> findByProblemIdAndIsPublicTrueOrderByIdAsc(Long problemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TestCase t where t.problem.id = :problemId")
    List<TestCase> findByProblemIdForDuplicatePromotionCheck(@Param("problemId") Long problemId);

    boolean existsByProblemIdAndInputDataAndExpectedOutput(Long problemId, String inputData, String expectedOutput);
    boolean existsByProblemIdAndInputDataAndExpectedOutputAndIdNot(Long problemId, String inputData, String expectedOutput, Long id);

    int countByProblemId(Long id);

    long deleteByProblem_Id(Long problemId);
}
