package com.server.contestControl.contestServer.scoreboard.entity;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "scoreboard_reveal_states")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreboardRevealState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false, unique = true)
    private Contest contest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RevealStatus status;

    private Instant startedAt;
    private Instant updatedAt;
    private Instant completedAt;

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = RevealStatus.NOT_STARTED;
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
