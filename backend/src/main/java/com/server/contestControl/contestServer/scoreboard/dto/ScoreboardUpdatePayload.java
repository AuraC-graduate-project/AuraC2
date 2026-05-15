package com.server.contestControl.contestServer.scoreboard.dto;

import java.util.List;

public record ScoreboardUpdatePayload(
        String eventType,
        String reason,
        Long contestId,
        long version,
        long previousVersion,
        boolean fullSnapshot,
        List<Long> changedTeamIds,
        List<ScoreboardRow> changedRows,
        ScoreboardMetadata metadata,
        ScoreboardSnapshot snapshot
) {
}
