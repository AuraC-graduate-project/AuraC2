package com.server.contestControl.contestServer.moderation.repository;

import com.server.contestControl.contestServer.moderation.entity.ContestModerationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestModerationAuditLogRepository
        extends JpaRepository<ContestModerationAuditLog, Long>, ContestModerationAuditLogSearchRepository {
}
