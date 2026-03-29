package com.server.contestControl.contestServer.repository;

import com.server.contestControl.contestServer.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemId(Long problemId);

    int countByProblemId(Long id);
}