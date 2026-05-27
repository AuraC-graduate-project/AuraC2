package com.server.contestControl.contestServer.moderation.dto;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.moderation.entity.ContestTeamModeration;
import com.server.contestControl.contestServer.moderation.enums.ContestTeamStatus;

import java.time.LocalDateTime;

public record ContestTeamModerationResponse(
        Long id,
        Long contestId,
        String contestTitle,
        Long teamId,
        String teamUsername,
        ContestTeamStatus status,
        boolean hiddenFromScoreboard,
        boolean submitEnabled,
        boolean runEnabled,
        String reason,
        Long updatedByAdminId,
        String updatedByAdminUsername,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ContestTeamModerationResponse activeDefault(Contest contest, User team) {
        return new ContestTeamModerationResponse(
                null,
                contest.getId(),
                contest.getTitle(),
                team.getId(),
                team.getUsername(),
                ContestTeamStatus.ACTIVE,
                false,
                true,
                true,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static ContestTeamModerationResponse from(ContestTeamModeration moderation) {
        User admin = moderation.getUpdatedByAdmin();
        return new ContestTeamModerationResponse(
                moderation.getId(),
                moderation.getContest().getId(),
                moderation.getContest().getTitle(),
                moderation.getTeam().getId(),
                moderation.getTeam().getUsername(),
                moderation.getStatus(),
                Boolean.TRUE.equals(moderation.getHiddenFromScoreboard()),
                !Boolean.FALSE.equals(moderation.getSubmitEnabled()),
                !Boolean.FALSE.equals(moderation.getRunEnabled()),
                moderation.getReason(),
                admin == null ? null : admin.getId(),
                admin == null ? null : admin.getUsername(),
                moderation.getCreatedAt(),
                moderation.getUpdatedAt()
        );
    }
}
