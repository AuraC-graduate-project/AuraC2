package com.server.contestControl.contestServer.scoreboard.repository;

import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScoreboardRevealStateRepository extends JpaRepository<ScoreboardRevealState, Long> {
    Optional<ScoreboardRevealState> findByContest_Id(Long contestId);
}
