package com.server.contestControl.contestServer.oracle.repository;

import com.server.contestControl.contestServer.oracle.entity.InputValidator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InputValidatorRepository extends JpaRepository<InputValidator, Long> {
    Optional<InputValidator> findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(Long problemId);
    List<InputValidator> findByProblem_IdOrderByUpdatedAtDescIdDesc(Long problemId);
    long deleteByProblem_Id(Long problemId);
}
