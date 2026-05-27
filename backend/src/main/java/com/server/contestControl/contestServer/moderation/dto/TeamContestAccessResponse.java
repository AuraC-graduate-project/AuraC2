package com.server.contestControl.contestServer.moderation.dto;

import com.server.contestControl.contestServer.moderation.enums.ContestTeamStatus;

public record TeamContestAccessResponse(
        Long contestId,
        ContestTeamStatus status,
        boolean hiddenFromScoreboard,
        boolean submitEnabled,
        boolean runEnabled,
        boolean workspaceVisible,
        String message
) {
}
