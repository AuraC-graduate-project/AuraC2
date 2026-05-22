package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.repository.ContestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContestStatusSyncExecutorTest {

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestLifecycleService contestLifecycleService;

    @InjectMocks
    private ContestStatusSyncExecutor executor;

    @Test
    void autoStartStampsActualStartTimeAtScheduledStart() {
        Instant scheduledStart = Instant.parse("2026-05-15T09:00:00Z");
        Instant syncTime = scheduledStart.plus(2, ChronoUnit.SECONDS);
        Contest contest = Contest.builder()
                .id(1L)
                .status(ContestStatus.UPCOMING)
                .statusLocked(false)
                .startTime(scheduledStart)
                .durationMinutes(120)
                .build();
        when(contestRepository.findByIdWithLock(1L)).thenReturn(Optional.of(contest));
        when(contestLifecycleService.resolveEffectiveState(contest, syncTime)).thenReturn(ContestStatus.RUNNING);

        Optional<ContestStatus> result = executor.syncContestStatus(1L, syncTime);

        assertThat(result).contains(ContestStatus.RUNNING);
        assertThat(contest.getStatus()).isEqualTo(ContestStatus.RUNNING);
        assertThat(contest.getActualStartTime()).isEqualTo(scheduledStart);
        verify(contestRepository).save(contest);
    }

    @Test
    void autoEndPersistsEndedWhenEffectiveEndHasPassed() {
        Instant now = Instant.parse("2026-05-15T11:00:00Z");
        Contest contest = Contest.builder()
                .id(1L)
                .status(ContestStatus.RUNNING)
                .statusLocked(false)
                .actualStartTime(now.minus(120, ChronoUnit.MINUTES))
                .durationMinutes(120)
                .build();
        when(contestRepository.findByIdWithLock(1L)).thenReturn(Optional.of(contest));
        when(contestLifecycleService.resolveEffectiveState(contest, now)).thenReturn(ContestStatus.ENDED);

        Optional<ContestStatus> result = executor.syncContestStatus(1L, now);

        assertThat(result).contains(ContestStatus.ENDED);
        assertThat(contest.getStatus()).isEqualTo(ContestStatus.ENDED);
        verify(contestRepository).save(contest);
    }

    @Test
    void lockedContestIsNotAutoSynced() {
        Instant now = Instant.parse("2026-05-15T09:00:00Z");
        Contest contest = Contest.builder()
                .id(1L)
                .status(ContestStatus.UPCOMING)
                .statusLocked(true)
                .startTime(now)
                .durationMinutes(120)
                .build();
        when(contestRepository.findByIdWithLock(1L)).thenReturn(Optional.of(contest));
        when(contestLifecycleService.resolveEffectiveState(contest, now)).thenReturn(ContestStatus.RUNNING);

        Optional<ContestStatus> result = executor.syncContestStatus(1L, now);

        assertThat(result).isEmpty();
        assertThat(contest.getStatus()).isEqualTo(ContestStatus.UPCOMING);
        verify(contestRepository, never()).save(any());
    }

    @Test
    void disallowedAutoTransitionIsIgnored() {
        Instant now = Instant.parse("2026-05-15T10:00:00Z");
        Contest contest = Contest.builder()
                .id(1L)
                .status(ContestStatus.PAUSED)
                .actualStartTime(now.minus(1, ChronoUnit.HOURS))
                .durationMinutes(120)
                .build();
        when(contestRepository.findByIdWithLock(1L)).thenReturn(Optional.of(contest));
        when(contestLifecycleService.resolveEffectiveState(contest, now)).thenReturn(ContestStatus.ENDED);

        Optional<ContestStatus> result = executor.syncContestStatus(1L, now);

        assertThat(result).isEmpty();
        assertThat(contest.getStatus()).isEqualTo(ContestStatus.PAUSED);
        verify(contestRepository, never()).save(any());
    }
}
