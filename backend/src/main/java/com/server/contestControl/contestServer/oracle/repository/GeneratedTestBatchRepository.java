package com.server.contestControl.contestServer.oracle.repository;

import com.server.contestControl.contestServer.oracle.entity.GeneratedTestBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedTestBatchRepository extends JpaRepository<GeneratedTestBatch, Long> {
    List<GeneratedTestBatch> findByProblem_IdOrderByCreatedAtDescIdDesc(Long problemId);
    boolean existsByProblem_Id(Long problemId);
}
