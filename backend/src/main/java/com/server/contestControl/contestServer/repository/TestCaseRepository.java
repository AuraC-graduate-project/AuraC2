package com.server.contestControl.contestServer.repository;

import com.server.contestControl.contestServer.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemId(Long problemId);
    List<TestCase> findByProblemIdOrderByIdAsc(Long problemId);
    List<TestCase> findByProblemIdAndIsPublicTrueOrderByIdAsc(Long problemId);

    boolean existsByProblemIdAndInputDataAndExpectedOutput(Long problemId, String inputData, String expectedOutput);
    boolean existsByProblemIdAndInputDataAndExpectedOutputAndIdNot(Long problemId, String inputData, String expectedOutput, Long id);

    int countByProblemId(Long id);

    long deleteByProblem_Id(Long problemId);
}
