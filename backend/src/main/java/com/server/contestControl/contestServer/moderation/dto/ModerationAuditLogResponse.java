package com.server.contestControl.contestServer.moderation.dto;

import com.server.contestControl.contestServer.moderation.entity.ContestModerationAuditLog;
import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;

import java.time.LocalDateTime;

public record ModerationAuditLogResponse(
        Long id,
        Long contestId,
        String contestTitle,
        Long teamId,
        String teamUsername,
        Long adminId,
        String adminUsername,
        Long problemId,
        String problemTitle,
        Integer languageId,
        String sourceHash,
        String executionMode,
        ModerationActionType actionType,
        String reason,
        String oldValueJson,
        String newValueJson,
        LocalDateTime createdAt
) {
    public static ModerationAuditLogResponse from(ContestModerationAuditLog log) {
        return new ModerationAuditLogResponse(
                log.getId(),
                log.getContest().getId(),
                log.getContest().getTitle(),
                log.getTeam() == null ? null : log.getTeam().getId(),
                log.getTeam() == null ? null : log.getTeam().getUsername(),
                log.getAdmin().getId(),
                log.getAdmin().getUsername(),
                log.getProblem() == null ? null : log.getProblem().getId(),
                log.getProblem() == null ? null : log.getProblem().getTitle(),
                log.getLanguageId(),
                log.getSourceHash(),
                log.getExecutionMode(),
                log.getActionType(),
                log.getReason(),
                log.getOldValueJson(),
                log.getNewValueJson(),
                log.getCreatedAt()
        );
    }
}
