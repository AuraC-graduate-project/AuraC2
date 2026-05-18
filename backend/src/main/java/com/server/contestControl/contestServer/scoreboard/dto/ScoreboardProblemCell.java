package com.server.contestControl.contestServer.scoreboard.dto;

public record ScoreboardProblemCell(
        Long problemId,
        String label,
        boolean solved,
        int attempts,
        int wrongAttempts,
        int pendingCount,
        Integer solvedTimeMinutes,
        Integer penalty,
        boolean firstToSolve,
        boolean hidden,
        boolean revealed
) {
}
