package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ContestLifecycleServiceTest {

    private static final Instant START = Instant.parse("2026-05-15T09:00:00Z");
    private static final long MINUTE = 60_000L;

    private ContestLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new ContestLifecycleService();
    }

    @Test
    void upcomingContestStaysUpcomingBeforeScheduledStart() {
        Contest contest = contest(ContestStatus.UPCOMING)
                .startTime(START)
                .durationMinutes(120)
                .build();

        Instant oneMillisecondBeforeStart = START.minusMillis(1);

        assertThat(lifecycleService.resolveEffectiveState(contest, oneMillisecondBeforeStart))
                .isEqualTo(ContestStatus.UPCOMING);
        assertThat(lifecycleService.resolveEffectiveEndTime(contest, oneMillisecondBeforeStart))
                .isNull();
        assertThat(lifecycleService.resolveRemainingMillis(contest, oneMillisecondBeforeStart))
                .isEqualTo(120 * MINUTE);
    }

    @Test
    void scheduledStartBoundaryIsInclusiveWhenContestIsUnlocked() {
        Contest contest = contest(ContestStatus.UPCOMING)
                .startTime(START)
                .durationMinutes(120)
                .statusLocked(false)
                .build();

        assertThat(lifecycleService.resolveEffectiveState(contest, START))
                .isEqualTo(ContestStatus.RUNNING);
        assertThat(lifecycleService.resolveEffectiveState(contest, START.plusMillis(1)))
                .isEqualTo(ContestStatus.RUNNING);
    }

    @Test
    void lockedUpcomingContestDoesNotAutoStartAtBoundary() {
        Contest contest = contest(ContestStatus.UPCOMING)
                .startTime(START)
                .durationMinutes(120)
                .statusLocked(true)
                .build();

        assertThat(lifecycleService.resolveEffectiveState(contest, START.plus(1, ChronoUnit.HOURS)))
                .isEqualTo(ContestStatus.UPCOMING);
    }

    @Test
    void effectiveEndBoundaryIsInclusiveForUnlockedRunningContest() {
        Contest contest = contest(ContestStatus.RUNNING)
                .startTime(START)
                .actualStartTime(START)
                .durationMinutes(120)
                .statusLocked(false)
                .build();

        Instant effectiveEnd = START.plus(120, ChronoUnit.MINUTES);

        assertThat(lifecycleService.resolveEffectiveEndTime(contest, START))
                .isEqualTo(effectiveEnd);
        assertThat(lifecycleService.resolveEffectiveState(contest, effectiveEnd.minusMillis(1)))
                .isEqualTo(ContestStatus.RUNNING);
        assertThat(lifecycleService.resolveEffectiveState(contest, effectiveEnd))
                .isEqualTo(ContestStatus.ENDED);
        assertThat(lifecycleService.resolveRemainingMillis(contest, effectiveEnd))
                .isZero();
    }

    @Test
    void pausedContestFreezesRemainingTimeAndHasNoWallClockEnd() {
        Contest contest = contest(ContestStatus.PAUSED)
                .startTime(START)
                .actualStartTime(START)
                .pausedAt(START.plus(45, ChronoUnit.MINUTES))
                .totalPauseMillis(10 * MINUTE)
                .durationMinutes(120)
                .scoreboardFreezeMinutes(90)
                .build();

        Instant now = START.plus(90, ChronoUnit.MINUTES);

        assertThat(lifecycleService.resolveEffectiveState(contest, now))
                .isEqualTo(ContestStatus.PAUSED);
        assertThat(lifecycleService.resolveEffectiveEndTime(contest, now))
                .isNull();
        assertThat(lifecycleService.resolveRemainingMillis(contest, now))
                .isEqualTo(85 * MINUTE);
        assertThat(lifecycleService.isScoreboardFrozen(contest, now))
                .isTrue();
    }

    @Test
    void accumulatedPauseDurationExtendsEffectiveEndTime() {
        Contest contest = contest(ContestStatus.RUNNING)
                .startTime(START)
                .actualStartTime(START)
                .totalPauseMillis(15 * MINUTE)
                .durationMinutes(120)
                .build();

        assertThat(lifecycleService.resolveEffectiveEndTime(contest, START.plus(1, ChronoUnit.HOURS)))
                .isEqualTo(START.plus(135, ChronoUnit.MINUTES));
        assertThat(lifecycleService.resolveRemainingMillis(contest, START.plus(60, ChronoUnit.MINUTES)))
                .isEqualTo(75 * MINUTE);
    }

    @Test
    void freezeWindowStartsAtPauseAwareFreezeBoundaryAndEndsWithContest() {
        Contest contest = contest(ContestStatus.RUNNING)
                .startTime(START)
                .actualStartTime(START)
                .totalPauseMillis(15 * MINUTE)
                .durationMinutes(120)
                .scoreboardFreezeMinutes(60)
                .statusLocked(false)
                .build();

        Instant freezeTime = START.plus(75, ChronoUnit.MINUTES);
        Instant effectiveEnd = START.plus(135, ChronoUnit.MINUTES);

        assertThat(lifecycleService.resolveEffectiveScoreboardFreezeTime(contest, START))
                .isEqualTo(freezeTime);
        assertThat(lifecycleService.isScoreboardFrozen(contest, freezeTime.minusMillis(1)))
                .isFalse();
        assertThat(lifecycleService.isScoreboardFrozen(contest, freezeTime))
                .isTrue();
        assertThat(lifecycleService.isScoreboardFrozen(contest, effectiveEnd.minusMillis(1)))
                .isTrue();
        assertThat(lifecycleService.isScoreboardFrozen(contest, effectiveEnd))
                .isFalse();
    }

    private Contest.ContestBuilder contest(ContestStatus status) {
        return Contest.builder()
                .id(1L)
                .title("ICPC Local")
                .status(status);
    }
}
