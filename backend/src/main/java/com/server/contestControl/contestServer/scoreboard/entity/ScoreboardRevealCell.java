package com.server.contestControl.contestServer.scoreboard.entity;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.contestServer.entity.Problem;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "scoreboard_reveal_cells",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scoreboard_reveal_state_team_problem",
                columnNames = {"reveal_state_id", "team_id", "problem_id"}
        ),
        indexes = {
                @Index(name = "idx_scoreboard_reveal_cells_state_order", columnList = "reveal_state_id, reveal_order, id"),
                @Index(name = "idx_scoreboard_reveal_cells_state_revealed_order", columnList = "reveal_state_id, revealed, reveal_order, id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreboardRevealCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reveal_state_id", nullable = false)
    private ScoreboardRevealState revealState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private User team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false)
    private Integer revealOrder;

    @Column(nullable = false)
    private Boolean revealed;

    private Instant revealedAt;

    @PrePersist
    public void prePersist() {
        if (revealed == null) {
            revealed = false;
        }
    }
}
