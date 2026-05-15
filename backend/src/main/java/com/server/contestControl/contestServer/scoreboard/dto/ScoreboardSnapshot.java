package com.server.contestControl.contestServer.scoreboard.dto;

import java.util.List;

public record ScoreboardSnapshot(
        ScoreboardMetadata metadata,
        List<ScoreboardRow> rows
) {
}
