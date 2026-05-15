package com.server.contestControl.contestServer.scoreboard.dto;

import java.util.List;

public record ScoreboardRow(
        int rank,
        Long teamId,
        String teamName,
        int solvedCount,
        int totalPenalty,
        List<ScoreboardProblemCell> problemCells
) {
}
