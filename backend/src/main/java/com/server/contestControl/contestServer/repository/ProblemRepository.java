package com.server.contestControl.contestServer.repository;


import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByContest_Id(Long contestId);
    List<Problem> findAllByContest_id(Long contestId);
}
