package com.server.contestControl.contestServer.moderation.entity;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.moderation.enums.ContestTeamStatus;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "contest_team_moderations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contest_team_moderations_contest_team",
                        columnNames = {"contest_id", "team_id"}
                )
        },
        indexes = {
                @Index(name = "idx_contest_team_moderations_contest", columnList = "contest_id"),
                @Index(name = "idx_contest_team_moderations_team", columnList = "team_id"),
                @Index(name = "idx_contest_team_moderations_scoreboard", columnList = "contest_id, hidden_from_scoreboard, status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContestTeamModeration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private User team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContestTeamStatus status;

    @Builder.Default
    @Column(name = "hidden_from_scoreboard", nullable = false)
    private Boolean hiddenFromScoreboard = false;

    @Builder.Default
    @Column(name = "submit_enabled", nullable = false)
    private Boolean submitEnabled = true;

    @Builder.Default
    @Column(name = "run_enabled", nullable = false)
    private Boolean runEnabled = true;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_admin_id")
    private User updatedByAdmin;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = ContestTeamStatus.ACTIVE;
        }
        if (hiddenFromScoreboard == null) {
            hiddenFromScoreboard = false;
        }
        if (submitEnabled == null) {
            submitEnabled = true;
        }
        if (runEnabled == null) {
            runEnabled = true;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
