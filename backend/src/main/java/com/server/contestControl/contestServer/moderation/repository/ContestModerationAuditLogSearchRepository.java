package com.server.contestControl.contestServer.moderation.repository;

import com.server.contestControl.contestServer.moderation.entity.ContestModerationAuditLog;
import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;

import java.time.LocalDateTime;
import java.util.List;

public interface ContestModerationAuditLogSearchRepository {

    List<ContestModerationAuditLog> search(
            Long contestId,
            Long teamId,
            Long adminId,
            ModerationActionType actionType,
            LocalDateTime fromTime,
            LocalDateTime toTime
    );
}
