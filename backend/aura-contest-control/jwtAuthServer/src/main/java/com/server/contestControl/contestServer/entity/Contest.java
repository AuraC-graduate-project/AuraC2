package com.server.contestControl.contestServer.entity;

import com.server.contestControl.contestServer.enums.ContestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Entity
@Table(name = "contests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private Instant startTime;
    private Integer durationMinutes;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "contest", cascade = CascadeType.ALL)
    private List<Problem> problems;

    @Enumerated(EnumType.STRING)
    private ContestStatus status;

    /**
     * ICPC: Minutes before end time when scoreboard freezes.
     * e.g., 60 means freeze 1 hour before contest ends.
     * Null means no freeze.
     */
    private Integer scoreboardFreezeMinutes;

    /**
     * ICPC: Penalty minutes per wrong submission (typically 20).
     */
    @Builder.Default
    private Integer penaltyMinutes = 20;

    @PrePersist
    public void prePersist() {
        if (startTime == null) {
            startTime = Instant.now();
        }
        if (penaltyMinutes == null) {
            penaltyMinutes = 20;
        }
    }

    /**
     * Computes the scheduled end time based on startTime + durationMinutes.
     */
    public Instant getEndTime() {
        if (startTime == null || durationMinutes == null) {
            return null;
        }
        return startTime.plus(durationMinutes, ChronoUnit.MINUTES);
    }

    /**
     * Computes when scoreboard should freeze.
     */
    public Instant getScoreboardFreezeTime() {
        Instant endTime = getEndTime();
        if (endTime == null || scoreboardFreezeMinutes == null) {
            return null;
        }
        return endTime.minus(scoreboardFreezeMinutes, ChronoUnit.MINUTES);
    }

    /**
     * Checks if scoreboard is currently frozen.
     */
    public boolean isScoreboardFrozen() {
        Instant freezeTime = getScoreboardFreezeTime();
        if (freezeTime == null) {
            return false;
        }
        Instant now = Instant.now();
        return status == ContestStatus.RUNNING
                && now.isAfter(freezeTime)
                && now.isBefore(getEndTime());
    }
}
