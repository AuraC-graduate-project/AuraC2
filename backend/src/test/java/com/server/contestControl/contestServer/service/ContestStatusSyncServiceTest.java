package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.contestServer.repository.ContestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContestStatusSyncServiceTest {

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestStatusSyncExecutor syncExecutor;

    @Mock
    private ContestService contestService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ContestStatusSyncService syncService;

    private Contest upcomingContest;
    private Contest runningContest;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        upcomingContest = Contest.builder()
                .id(1L)
                .title("Upcoming Contest")
                .status(ContestStatus.UPCOMING)
                .statusLocked(false)
                .startTime(now.minus(5, ChronoUnit.MINUTES))
                .durationMinutes(120)
                .build();

        runningContest = Contest.builder()
                .id(2L)
                .title("Running Contest")
                .status(ContestStatus.RUNNING)
                .statusLocked(false)
                .startTime(now.minus(3, ChronoUnit.HOURS))
                .durationMinutes(120)
                .build();
    }

    @Nested
    @DisplayName("syncAllEligibleContests - no candidates")
    class NoCandidatesTests {

        @Test
        @DisplayName("should return 0 when no contests require sync")
        void shouldReturnZeroWhenNoContests() {
            when(contestRepository.findSyncCandidates(any())).thenReturn(List.of());

            int syncedCount = syncService.syncAllEligibleContests();

            assertThat(syncedCount).isEqualTo(0);
            verify(syncExecutor, never()).syncContestStatus(any(), any());
        }

        @Test
        @DisplayName("should query for UPCOMING and RUNNING statuses only")
        void shouldQueryForCorrectStatuses() {
            when(contestRepository.findSyncCandidates(any())).thenReturn(List.of());

            syncService.syncAllEligibleContests();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ContestStatus>> statusCaptor = ArgumentCaptor.forClass(List.class);
            verify(contestRepository).findSyncCandidates(statusCaptor.capture());
            assertThat(statusCaptor.getValue()).containsExactlyInAnyOrder(
                    ContestStatus.UPCOMING,
                    ContestStatus.RUNNING
            );
        }
    }

    @Nested
    @DisplayName("syncAllEligibleContests - transition occurs")
    class TransitionOccursTests {

        @Test
        @DisplayName("should return 1 and publish event when UPCOMING transitions to RUNNING")
        void shouldTransitionUpcomingToRunning() {
            when(contestRepository.findSyncCandidates(any()))
                    .thenReturn(List.of(upcomingContest));
            when(syncExecutor.syncContestStatus(eq(upcomingContest.getId()), any()))
                    .thenReturn(Optional.of(ContestStatus.RUNNING));

            ContestResponse snapshot = ContestResponse.builder()
                    .id(upcomingContest.getId()).title("Upcoming Contest")
                    .status(ContestStatus.RUNNING.name()).build();
            when(contestService.buildResponseForId(upcomingContest.getId()))
                    .thenReturn(Optional.of(snapshot));

            int result = syncService.syncAllEligibleContests();

            assertThat(result).isEqualTo(1);
            verify(eventPublisher).publishEvent(any(ContestUpdatedEvent.class));
        }

        @Test
        @DisplayName("should return 1 and publish event when RUNNING transitions to ENDED")
        void shouldTransitionRunningToEnded() {
            when(contestRepository.findSyncCandidates(any()))
                    .thenReturn(List.of(runningContest));
            when(syncExecutor.syncContestStatus(eq(runningContest.getId()), any()))
                    .thenReturn(Optional.of(ContestStatus.ENDED));

            ContestResponse snapshot = ContestResponse.builder()
                    .id(runningContest.getId()).title("Running Contest")
                    .status(ContestStatus.ENDED.name()).build();
            when(contestService.buildResponseForId(runningContest.getId()))
                    .thenReturn(Optional.of(snapshot));

            int result = syncService.syncAllEligibleContests();

            assertThat(result).isEqualTo(1);
            verify(eventPublisher).publishEvent(any(ContestUpdatedEvent.class));
        }
    }

    @Nested
    @DisplayName("syncAllEligibleContests - no transition needed")
    class NoTransitionTests {

        @Test
        @DisplayName("should return 0 when executor reports no transition needed")
        void shouldReturnZeroWhenAlreadyInSync() {
            when(contestRepository.findSyncCandidates(any()))
                    .thenReturn(List.of(upcomingContest));
            when(syncExecutor.syncContestStatus(eq(upcomingContest.getId()), any()))
                    .thenReturn(Optional.empty());

            int result = syncService.syncAllEligibleContests();

            assertThat(result).isEqualTo(0);
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("syncAllEligibleContests - error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should return 0 and not throw when executor throws exception")
        void shouldHandleExecutorException() {
            when(contestRepository.findSyncCandidates(any()))
                    .thenReturn(List.of(upcomingContest));
            when(syncExecutor.syncContestStatus(any(), any()))
                    .thenThrow(new RuntimeException("Database error"));

            int result = syncService.syncAllEligibleContests();

            assertThat(result).isEqualTo(0);
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
