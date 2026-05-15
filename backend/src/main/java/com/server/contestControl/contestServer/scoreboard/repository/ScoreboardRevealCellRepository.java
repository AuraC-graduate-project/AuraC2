package com.server.contestControl.contestServer.scoreboard.repository;

import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealCell;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScoreboardRevealCellRepository extends JpaRepository<ScoreboardRevealCell, Long> {
    List<ScoreboardRevealCell> findByRevealState_IdOrderByRevealOrderAscIdAsc(Long revealStateId);

    Optional<ScoreboardRevealCell> findFirstByRevealState_IdAndRevealedFalseOrderByRevealOrderAscIdAsc(Long revealStateId);

    long countByRevealState_Id(Long revealStateId);

    long countByRevealState_IdAndRevealedTrue(Long revealStateId);

    void deleteByRevealState_Id(Long revealStateId);
}
