package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class ContestLifecycleService {

    public ContestStatus resolveEffectiveState(Contest contest, Instant now) {
        ContestStatus persistedStatus = contest.getStatus();

        if (persistedStatus == ContestStatus.ENDED) {
            return ContestStatus.ENDED;
        }

        if (persistedStatus == ContestStatus.PAUSED) {
            return ContestStatus.PAUSED;
        }

        if (persistedStatus == ContestStatus.RUNNING) {
            Instant effectiveEndTime = resolveEffectiveEndTime(contest, now);
            if (effectiveEndTime != null && !now.isBefore(effectiveEndTime)
                    && !Boolean.TRUE.equals(contest.getStatusLocked())) {  // ← same guard as UPCOMING
                return ContestStatus.ENDED;
            }
            return ContestStatus.RUNNING;
        }

        // UPCOMING: auto-start if scheduled start time has passed and contest is not locked.
        // The scheduler will stamp actualStartTime when it applies this transition.
        if (contest.getStartTime() != null && !now.isBefore(contest.getStartTime())
                && !Boolean.TRUE.equals(contest.getStatusLocked())) {
            return ContestStatus.RUNNING;
        }
        return ContestStatus.UPCOMING;
    }

    public Instant resolveEffectiveStartTime(Contest contest) {
        return contest.getActualStartTime();
    }

    /**
     * Live end time, accounting for manual start and accumulated pause time.
     * Returns null when the contest hasn't started yet or is currently paused
     * (paused contests have no fixed wall-clock end).
     */
    public Instant resolveEffectiveEndTime(Contest contest, Instant now) {
        Instant actualStart = contest.getActualStartTime();
        Integer durationMinutes = contest.getDurationMinutes();

        log.info(
                "resolveEffectiveEndTime | contestId={} | persistedStatus={} | startTime={} | actualStartTime={} | totalPauseMillis={} | durationMinutes={} | now={}",
                contest.getId(),
                contest.getStatus(),
                contest.getStartTime(),
                actualStart,
                contest.getTotalPauseMillis(),
                durationMinutes,
                now
        );

        if (actualStart == null || durationMinutes == null) {
            log.info(
                    "resolveEffectiveEndTime -> null | contestId={} | reason=actualStartTime_or_duration_is_null | startTime={} | actualStartTime={}",
                    contest.getId(),
                    contest.getStartTime(),
                    actualStart
            );
            return null;
        }

        if (contest.getStatus() == ContestStatus.PAUSED) {
            log.info(
                    "resolveEffectiveEndTime -> null | contestId={} | reason=paused | startTime={} | actualStartTime={}",
                    contest.getId(),
                    contest.getStartTime(),
                    actualStart
            );
            return null;
        }

        long totalPause = contest.getTotalPauseMillis() != null ? contest.getTotalPauseMillis() : 0L;
        Instant effectiveEndTime = actualStart.plusMillis(durationMinutes * 60_000L + totalPause);

        log.info(
                "resolveEffectiveEndTime -> computed | contestId={} | startTime={} | actualStartTime={} | effectiveEndTime={}",
                contest.getId(),
                contest.getStartTime(),
                actualStart,
                effectiveEndTime
        );

        return effectiveEndTime;
    }

    /**
     * Milliseconds remaining on the contest clock.
     *  - UPCOMING: full duration (clock hasn't started)
     *  - RUNNING:  max(0, effectiveEndTime - now)
     *  - PAUSED:   frozen at the moment of pause
     *  - ENDED:    0
     */
    public long resolveRemainingMillis(Contest contest, Instant now) {
        Integer durationMinutes = contest.getDurationMinutes();
        if (durationMinutes == null) {
            return 0L;
        }
        long durationMillis = durationMinutes * 60_000L;
        ContestStatus persisted = contest.getStatus();

        if (persisted == ContestStatus.ENDED) {
            return 0L;
        }

        Instant actualStart = contest.getActualStartTime();
        if (actualStart == null || persisted == ContestStatus.UPCOMING) {
            return durationMillis;
        }

        long totalPause = contest.getTotalPauseMillis() != null ? contest.getTotalPauseMillis() : 0L;

        if (persisted == ContestStatus.PAUSED) {
            Instant pausedAt = contest.getPausedAt();
            if (pausedAt == null) {
                return durationMillis;
            }
            long elapsedBeforePause = pausedAt.toEpochMilli() - actualStart.toEpochMilli() - totalPause;
            return Math.max(0L, durationMillis - elapsedBeforePause);
        }

        // RUNNING
        long elapsed = now.toEpochMilli() - actualStart.toEpochMilli() - totalPause;
        return Math.max(0L, durationMillis - elapsed);
    }

    /**
     * Freeze time slides with pauses because it's anchored to effectiveEndTime.
     * Returns null while paused (no wall-clock end), UPCOMING (not started), or if freeze disabled.
     */
    public Instant resolveEffectiveScoreboardFreezeTime(Contest contest, Instant now) {
        Integer freezeMinutes = contest.getScoreboardFreezeMinutes();
        Instant effectiveEnd = resolveEffectiveEndTime(contest, now);
        if (freezeMinutes == null || effectiveEnd == null) {
            return null;
        }
        return effectiveEnd.minusMillis(freezeMinutes * 60_000L);
    }

    /**
     * Frozen when the contest is RUNNING or PAUSED and remaining time is within
     * the freeze window. Remaining-time based so pauses shift the window automatically.
     */
    public boolean isScoreboardFrozen(Contest contest, Instant now) {
        Integer freezeMinutes = contest.getScoreboardFreezeMinutes();
        if (freezeMinutes == null) {
            return false;
        }
        ContestStatus effectiveState = resolveEffectiveState(contest, now);
        if (effectiveState != ContestStatus.RUNNING && effectiveState != ContestStatus.PAUSED) {
            return false;
        }
        long remaining = resolveRemainingMillis(contest, now);
        long freezeWindow = freezeMinutes * 60_000L;
        return remaining > 0L && remaining <= freezeWindow;
    }
}
