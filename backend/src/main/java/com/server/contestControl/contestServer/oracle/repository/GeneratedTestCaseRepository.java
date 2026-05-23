package com.server.contestControl.contestServer.oracle.repository;

import com.server.contestControl.contestServer.oracle.entity.GeneratedTestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedTestCaseRepository extends JpaRepository<GeneratedTestCase, Long> {
    List<GeneratedTestCase> findByBatch_IdOrderByTestNumberAsc(Long batchId);
}
