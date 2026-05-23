package com.server.contestControl.contestServer.oracle.repository;

import com.server.contestControl.contestServer.oracle.entity.InputGenerator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InputGeneratorRepository extends JpaRepository<InputGenerator, Long> {
    Optional<InputGenerator> findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(Long problemId);
    List<InputGenerator> findByProblem_IdOrderByUpdatedAtDescIdDesc(Long problemId);
    long deleteByProblem_Id(Long problemId);
}
