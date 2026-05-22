package com.server.contestControl.contestServer.scheduler;

import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.service.ContestLifecycleService;
import com.server.contestControl.contestServer.service.ContestStatusSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContestTransitionSchedulerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ContestStatusSyncService syncService;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestLifecycleService lifecycleService;

    @Mock
    private ScheduledFuture<?> oldFuture;

    @Mock
    private ScheduledFuture<?> newFuture;

    @InjectMocks
    private ContestTransitionScheduler scheduler;

    @Test
    @DisplayName("UPDATED event should cancel old task and schedule the edited start time")
    void shouldRescheduleOnUpdatedEvent() {
        Long contestId = 1L;
        Instant oldStart = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant newStart = Instant.now().plus(2, ChronoUnit.HOURS);
        Contest oldContest = upcomingContest(contestId, oldStart);
        Contest updatedContest = upcomingContest(contestId, newStart);
        ContestResponse snapshot = ContestResponse.builder().id(contestId).build();

        when(contestRepository.findById(contestId))
                .thenReturn(Optional.of(oldContest))
                .thenReturn(Optional.of(updatedContest));
        doReturn(oldFuture).when(taskScheduler).schedule(any(Runnable.class), eq(oldStart));
        doReturn(newFuture).when(taskScheduler).schedule(any(Runnable.class), eq(newStart));
        when(oldFuture.isDone()).thenReturn(false);

        scheduler.onContestUpdated(new ContestUpdatedEvent(ContestUpdatedEvent.Reason.CREATED, snapshot));
        scheduler.onContestUpdated(new ContestUpdatedEvent(ContestUpdatedEvent.Reason.UPDATED, snapshot));

        verify(oldFuture).cancel(false);
        verify(taskScheduler).schedule(any(Runnable.class), eq(newStart));
    }

    @Test
    @DisplayName("MANUAL_PAUSE event should cancel the pending transition without scheduling a new one")
    void shouldCancelPendingTaskOnManualPause() {
        Long contestId = 1L;
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Contest contest = upcomingContest(contestId, start);
        ContestResponse snapshot = ContestResponse.builder().id(contestId).build();

        when(contestRepository.findById(contestId)).thenReturn(Optional.of(contest));
        doReturn(oldFuture).when(taskScheduler).schedule(any(Runnable.class), eq(start));
        when(oldFuture.isDone()).thenReturn(false);

        scheduler.onContestUpdated(new ContestUpdatedEvent(ContestUpdatedEvent.Reason.CREATED, snapshot));
        scheduler.onContestUpdated(new ContestUpdatedEvent(ContestUpdatedEvent.Reason.MANUAL_PAUSE, snapshot));

        verify(oldFuture).cancel(false);
    }

    @Test
    @DisplayName("MANUAL_RESUME event should schedule the pause-aware effective end time")
    void shouldSchedulePauseAwareEndTimeAfterResume() {
        Long contestId = 1L;
        Instant effectiveEnd = Instant.now().plus(90, ChronoUnit.MINUTES);
        Contest contest = Contest.builder()
                .id(contestId)
                .title("Contest")
                .status(ContestStatus.RUNNING)
                .actualStartTime(Instant.now().minus(30, ChronoUnit.MINUTES))
                .durationMinutes(120)
                .totalPauseMillis(10_000L)
                .build();
        ContestResponse snapshot = ContestResponse.builder().id(contestId).build();

        when(contestRepository.findById(contestId)).thenReturn(Optional.of(contest));
        when(lifecycleService.resolveEffectiveEndTime(eq(contest), any())).thenReturn(effectiveEnd);
        doReturn(newFuture).when(taskScheduler).schedule(any(Runnable.class), eq(effectiveEnd));

        scheduler.onContestUpdated(new ContestUpdatedEvent(ContestUpdatedEvent.Reason.MANUAL_RESUME, snapshot));

        verify(taskScheduler).schedule(any(Runnable.class), eq(effectiveEnd));
    }

    private Contest upcomingContest(Long id, Instant startTime) {
        return Contest.builder()
                .id(id)
                .title("Contest")
                .status(ContestStatus.UPCOMING)
                .startTime(startTime)
                .durationMinutes(120)
                .build();
    }
}
