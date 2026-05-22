package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
import com.server.contestControl.contestServer.service.ContestLifecycleService;
import com.server.contestControl.submissionServer.entity.Submission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreboardFreezePolicyTest {

    private static final Instant START = Instant.parse("2026-05-15T09:00:00Z");
    private ScoreboardFreezePolicy freezePolicy;

    @BeforeEach
    void setUp() {
        freezePolicy = new ScoreboardFreezePolicy(new ContestLifecycleService());
    }

    @Test
    void adminAudienceIsNeverFrozen() {
        Contest contest = runningContest();

        assertThat(freezePolicy.isFrozenForAudience(
                contest,
                ScoreboardAudience.ADMIN,
                RevealStatus.NOT_STARTED,
                START.plus(90, ChronoUnit.MINUTES)
        )).isFalse();
    }

    @Test
    void publicAudienceFreezesInsideRunningFreezeWindow() {
        Contest contest = runningContest();

        assertThat(freezePolicy.isFrozenForAudience(
                contest,
                ScoreboardAudience.PUBLIC,
                RevealStatus.NOT_STARTED,
                START.plus(59, ChronoUnit.MINUTES).plusSeconds(59)
        )).isFalse();
        assertThat(freezePolicy.isFrozenForAudience(
                contest,
                ScoreboardAudience.PUBLIC,
                RevealStatus.NOT_STARTED,
                START.plus(60, ChronoUnit.MINUTES)
        )).isTrue();
    }

    @Test
    void endedPublicScoreboardStaysFrozenUntilRevealCompletes() {
        Contest contest = runningContest();
        contest.setStatus(ContestStatus.ENDED);

        assertThat(freezePolicy.isFrozenForAudience(
                contest,
                ScoreboardAudience.PUBLIC,
                RevealStatus.NOT_STARTED,
                START.plus(121, ChronoUnit.MINUTES)
        )).isTrue();
        assertThat(freezePolicy.isFrozenForAudience(
                contest,
                ScoreboardAudience.PUBLIC,
                RevealStatus.COMPLETED,
                START.plus(121, ChronoUnit.MINUTES)
        )).isFalse();
    }

    @Test
    void submissionExactlyAtFreezeTimeIsNotBeforeFreeze() {
        Instant freezeTime = START.plus(60, ChronoUnit.MINUTES);
        Submission atFreeze = Submission.builder()
                .createdAt(LocalDateTime.ofInstant(freezeTime, ZoneId.systemDefault()))
                .build();

        assertThat(freezePolicy.isBeforeFreeze(atFreeze, freezeTime)).isFalse();
    }

    private Contest runningContest() {
        return Contest.builder()
                .id(1L)
                .status(ContestStatus.RUNNING)
                .statusLocked(false)
                .startTime(START)
                .actualStartTime(START)
                .durationMinutes(120)
                .scoreboardFreezeMinutes(60)
                .penaltyMinutes(20)
                .build();
    }
}
