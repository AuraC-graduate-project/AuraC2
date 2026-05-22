package com.server.contestControl.contestServer.entity;

import com.server.contestControl.contestServer.enums.ContestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Entity
@Table(
        name = "contests",
        indexes = {
                @Index(name = "idx_contests_status_start_time", columnList = "status, start_time"),
                @Index(name = "idx_contests_start_time", columnList = "start_time")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    /** Scheduled start time — planning/display only. Real countdown begins at actualStartTime. */
    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Integer durationMinutes;

    /** Set the first time the contest transitions UPCOMING -> RUNNING. Never overwritten on resume. */
    private Instant actualStartTime;

    /** Set on RUNNING -> PAUSED; cleared on PAUSED -> RUNNING. */
    private Instant pausedAt;

    /** Accumulated milliseconds spent paused across all pause/resume cycles. */
    @Builder.Default
    @Column(nullable = false)
    private Long totalPauseMillis = 0L;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "contest", cascade = CascadeType.ALL)
    private List<Problem> problems;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContestStatus status;

    /**
     * If true, the scheduler will NOT auto-update this contest's status.
     * Use this for contests that are manually controlled by jury/admin.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean statusLocked = false;

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
    @Column(nullable = false)
    private Integer penaltyMinutes = 20;

    @PrePersist
    public void prePersist() {
        if (startTime == null) {
            startTime = Instant.now();
        }
        if (penaltyMinutes == null) {
            penaltyMinutes = 20;
        }
        if (statusLocked == null) {
            statusLocked = false;
        }
        if (totalPauseMillis == null) {
            totalPauseMillis = 0L;
        }
    }

    /**
     * Computes the *scheduled* end time based on startTime + durationMinutes.
     * This is planning data only — use ContestLifecycleService#resolveEffectiveEndTime
     * for the live, pause-aware end time.
     */
    public Instant getEndTime() {
        if (startTime == null || durationMinutes == null) {
            return null;
        }
        return startTime.plus(durationMinutes, ChronoUnit.MINUTES);
    }
}
