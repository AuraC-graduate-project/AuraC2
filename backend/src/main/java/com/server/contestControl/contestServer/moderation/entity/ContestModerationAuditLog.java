package com.server.contestControl.contestServer.moderation.entity;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "contest_moderation_audit_logs",
        indexes = {
                @Index(name = "idx_moderation_audit_contest_created", columnList = "contest_id, created_at"),
                @Index(name = "idx_moderation_audit_team_created", columnList = "team_id, created_at"),
                @Index(name = "idx_moderation_audit_admin_created", columnList = "admin_id, created_at"),
                @Index(name = "idx_moderation_audit_problem_created", columnList = "problem_id, created_at"),
                @Index(name = "idx_moderation_audit_action_created", columnList = "action_type, created_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContestModerationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private User team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 64)
    private ModerationActionType actionType;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "old_value_json", nullable = false, columnDefinition = "TEXT")
    private String oldValueJson;

    @Column(name = "new_value_json", nullable = false, columnDefinition = "TEXT")
    private String newValueJson;

    @Column(name = "language_id")
    private Integer languageId;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "execution_mode", length = 64)
    private String executionMode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
