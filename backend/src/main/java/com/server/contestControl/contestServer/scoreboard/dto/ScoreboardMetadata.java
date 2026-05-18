package com.server.contestControl.contestServer.scoreboard.dto;

import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;

import java.time.Instant;
import java.util.List;

public record ScoreboardMetadata(
        Long contestId,
        String contestTitle,
        ScoreboardAudience audience,
        long version,
        Instant generatedAt,
        String contestStatus,
        String effectiveState,
        boolean adminLive,
        boolean scoreboardFrozen,
        Instant freezeTime,
        Integer freezeMinutes,
        Integer penaltyMinutes,
        RevealStatus revealStatus,
        long revealedCells,
        long totalHiddenCells,
        List<ProblemColumn> problemColumns
) {
    public record ProblemColumn(Long problemId, String label, String title, String balloonColor) {
    }
}
