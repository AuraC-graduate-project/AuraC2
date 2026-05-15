package com.server.contestControl.contestServer.scoreboard.dto;

import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;

import java.time.Instant;

public record ScoreboardRevealResponse(
        Long contestId,
        RevealStatus status,
        long totalCells,
        long revealedCells,
        Long nextTeamId,
        Long nextProblemId,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt
) {
}
