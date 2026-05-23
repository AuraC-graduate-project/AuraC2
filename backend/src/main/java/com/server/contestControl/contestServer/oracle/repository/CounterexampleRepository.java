package com.server.contestControl.contestServer.oracle.repository;

import com.server.contestControl.contestServer.oracle.entity.Counterexample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CounterexampleRepository extends JpaRepository<Counterexample, Long> {
    List<Counterexample> findByProblem_IdOrderByCreatedAtDescIdDesc(Long problemId);
    boolean existsByProblem_Id(Long problemId);
}
