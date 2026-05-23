package com.server.contestControl.contestServer.oracle.repository;

import com.server.contestControl.contestServer.oracle.entity.ReferenceSolution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferenceSolutionRepository extends JpaRepository<ReferenceSolution, Long> {
    Optional<ReferenceSolution> findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(Long problemId);
    List<ReferenceSolution> findByProblem_IdOrderByUpdatedAtDescIdDesc(Long problemId);
    long deleteByProblem_Id(Long problemId);
}
