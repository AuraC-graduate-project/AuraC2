package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
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
    private ContestLifecycleService contestLifecycleService;

    @InjectMocks
    private ContestStatusSyncService syncService;

    private Contest upcomingContest;
    private Contest runningContest;
    private Contest pausedContest;
    private Contest endedContest;
    private Contest lockedContest;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        upcomingContest = Contest.builder()
                .id(1L)
                .title("Upcoming Contest")
                .status(ContestStatus.UPCOMING)
                .statusLocked(false)
                .startTime(now.minus(5, ChronoUnit.MINUTES)) // Started 5 min ago
                .durationMinutes(120)
                .build();

        runningContest = Contest.builder()
                .id(2L)
                .title("Running Contest")
                .status(ContestStatus.RUNNING)
                .statusLocked(false)
                .startTime(now.minus(3, ChronoUnit.HOURS)) // Started 3 hours ago
                .durationMinutes(120) // Duration 2 hours, so ended 1 hour ago
                .build();

        pausedContest = Contest.builder()
                .id(3L)
                .title("Paused Contest")
                .status(ContestStatus.PAUSED)
                .statusLocked(false)
                .startTime(now.minus(1, ChronoUnit.HOURS))
                .durationMinutes(120)
                .build();

        endedContest = Contest.builder()
                .id(4L)
                .title("Ended Contest")
                .status(ContestStatus.ENDED)
                .statusLocked(false)
                .startTime(now.minus(5, ChronoUnit.HOURS))
                .durationMinutes(120)
                .build();

        lockedContest = Contest.builder()
                .id(5L)
                .title("Locked Contest")
                .status(ContestStatus.UPCOMING)
                .statusLocked(true)
                .startTime(now.minus(5, ChronoUnit.MINUTES))
                .durationMinutes(120)
                .build();
    }

    @Nested
    @DisplayName("findContestsRequiringSync")
    class FindContestsRequiringSyncTests {

        @Test
        @DisplayName("should query for UPCOMING and RUNNING contests only")
        void shouldQueryForCorrectStatuses() {
            when(contestRepository.findSyncCandidates(any())).thenReturn(List.of());

            syncService.findContestsRequiringSync();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ContestStatus>> statusCaptor = ArgumentCaptor.forClass(List.class);
            verify(contestRepository).findSyncCandidates(statusCaptor.capture());

            List<ContestStatus> statuses = statusCaptor.getValue();
            assertThat(statuses).containsExactlyInAnyOrder(
                    ContestStatus.UPCOMING,
                    ContestStatus.RUNNING
            );
        }
    }

    @Nested
    @DisplayName("syncContestStatus - UPCOMING to RUNNING")
    class UpcomingToRunningTests {

        @Test
        @DisplayName("should transition UPCOMING to RUNNING when start time has passed")
        void shouldTransitionUpcomingToRunning() {
            Instant now = Instant.now();

            when(contestLifecycleService.resolveEffectiveState(upcomingContest, now))
                    .thenReturn(ContestStatus.RUNNING);
            when(contestRepository.findById(upcomingContest.getId()))
                    .thenReturn(Optional.of(upcomingContest));
            when(contestRepository.save(any(Contest.class)))
                    .thenReturn(upcomingContest);

            Optional<ContestStatus> result = syncService.syncContestStatus(upcomingContest, now);

            assertThat(result).contains(ContestStatus.RUNNING);
            verify(contestRepository).save(any(Contest.class));
        }

        @Test
        @DisplayName("should not transition UPCOMING when start time has NOT passed (already in sync)")
        void shouldNotTransitionUpcomingWhenNotStarted() {
            Instant now = Instant.now();

            when(contestLifecycleService.resolveEffectiveState(upcomingContest, now))
                    .thenReturn(ContestStatus.UPCOMING); // Effective state matches persisted

            Optional<ContestStatus> result = syncService.syncContestStatus(upcomingContest, now);

            assertThat(result).isEmpty();
            verify(contestRepository, never()).save(any(Contest.class));
        }
    }

    @Nested
    @DisplayName("syncContestStatus - RUNNING to ENDED")
    class RunningToEndedTests {

        @Test
        @DisplayName("should transition RUNNING to ENDED when end time has passed")
        void shouldTransitionRunningToEnded() {
            Instant now = Instant.now();

            when(contestLifecycleService.resolveEffectiveState(runningContest, now))
                    .thenReturn(ContestStatus.ENDED);
            when(contestRepository.findById(runningContest.getId()))
                    .thenReturn(Optional.of(runningContest));
            when(contestRepository.save(any(Contest.class)))
                    .thenReturn(runningContest);

            Optional<ContestStatus> result = syncService.syncContestStatus(runningContest, now);

            assertThat(result).contains(ContestStatus.ENDED);
            verify(contestRepository).save(any(Contest.class));
        }

        @Test
        @DisplayName("should not transition RUNNING when end time has NOT passed (already in sync)")
        void shouldNotTransitionRunningWhenNotEnded() {
            Instant now = Instant.now();

            when(contestLifecycleService.resolveEffectiveState(runningContest, now))
                    .thenReturn(ContestStatus.RUNNING); // Effective state matches persisted

            Optional<ContestStatus> result = syncService.syncContestStatus(runningContest, now);

            assertThat(result).isEmpty();
            verify(contestRepository, never()).save(any(Contest.class));
        }
    }

    @Nested
    @DisplayName("syncContestStatus - Admin already updated")
    class AdminAlreadyUpdatedTests {

        @Test
        @DisplayName("should be no-op when admin already started the contest")
        void shouldBeNoOpWhenAdminAlreadyStarted() {
            Instant now = Instant.now();
            Contest alreadyStartedByAdmin = Contest.builder()
                    .id(10L)
                    .status(ContestStatus.RUNNING) // Admin already clicked "Start"
                    .statusLocked(false)
                    .startTime(now.minus(5, ChronoUnit.MINUTES))
                    .durationMinutes(120)
                    .build();

            when(contestLifecycleService.resolveEffectiveState(alreadyStartedByAdmin, now))
                    .thenReturn(ContestStatus.RUNNING); // Effective = Persisted

            Optional<ContestStatus> result = syncService.syncContestStatus(alreadyStartedByAdmin, now);

            assertThat(result).isEmpty();
            verify(contestRepository, never()).save(any(Contest.class));
        }
    }

    @Nested
    @DisplayName("syncContestStatus - Locked contests")
    class LockedContestTests {

        @Test
        @DisplayName("should skip contest when statusLocked is true (checked during re-fetch)")
        void shouldSkipLockedContest() {
            Instant now = Instant.now();

            // Simulate: contest was not locked initially, but got locked during sync
            Contest initiallyUnlocked = Contest.builder()
                    .id(5L)
                    .status(ContestStatus.UPCOMING)
                    .statusLocked(false)
                    .startTime(now.minus(5, ChronoUnit.MINUTES))
                    .durationMinutes(120)
                    .build();

            Contest nowLocked = Contest.builder()
                    .id(5L)
                    .status(ContestStatus.UPCOMING)
                    .statusLocked(true) // Locked during re-fetch
                    .startTime(now.minus(5, ChronoUnit.MINUTES))
                    .durationMinutes(120)
                    .build();

            when(contestLifecycleService.resolveEffectiveState(initiallyUnlocked, now))
                    .thenReturn(ContestStatus.RUNNING);
            when(contestRepository.findById(5L))
                    .thenReturn(Optional.of(nowLocked));

            Optional<ContestStatus> result = syncService.syncContestStatus(initiallyUnlocked, now);

            assertThat(result).isEmpty();
            verify(contestRepository, never()).save(any(Contest.class));
        }
    }

    @Nested
    @DisplayName("syncContestStatus - Disallowed transitions")
    class DisallowedTransitionTests {

        @Test
        @DisplayName("should NOT auto-transition PAUSED to ENDED (manual action required)")
        void shouldNotAutoTransitionPausedToEnded() {
            Instant now = Instant.now();

            // Even if effective state says ENDED, PAUSED->ENDED requires manual action
            when(contestLifecycleService.resolveEffectiveState(pausedContest, now))
                    .thenReturn(ContestStatus.ENDED);

            Optional<ContestStatus> result = syncService.syncContestStatus(pausedContest, now);

            assertThat(result).isEmpty();
            verify(contestRepository, never()).save(any(Contest.class));
        }

        @Test
        @DisplayName("should NOT auto-transition to PAUSED (manual action only)")
        void shouldNotAutoTransitionToPaused() {
            Instant now = Instant.now();

            // This scenario shouldn't happen in practice (lifecycle service wouldn't return PAUSED)
            // but we test it to ensure safety
            when(contestLifecycleService.resolveEffectiveState(runningContest, now))
                    .thenReturn(ContestStatus.PAUSED);

            Optional<ContestStatus> result = syncService.syncContestStatus(runningContest, now);

            assertThat(result).isEmpty();
            verify(contestRepository, never()).save(any(Contest.class));
        }
    }

    @Nested
    @DisplayName("syncContestStatus - Race conditions")
    class RaceConditionTests {

        @Test
        @DisplayName("should skip if contest status changed during sync (manual action happened)")
        void shouldSkipIfStatusChangedDuringSync() {
            Instant now = Instant.now();

            Contest initialContest = Contest.builder()
                    .id(1L)
                    .status(ContestStatus.UPCOMING)
                    .statusLocked(false)
                    .startTime(now.minus(5, ChronoUnit.MINUTES))
                    .durationMinutes(120)
                    .build();

            // Admin clicked "Start" between our check and update
            Contest changedContest = Contest.builder()
                    .id(1L)
                    .status(ContestStatus.RUNNING) // Admin already changed it
                    .statusLocked(false)
                    .startTime(now.minus(5, ChronoUnit.MINUTES))
                    .durationMinutes(120)
                    .build();

            when(contestLifecycleService.resolveEffectiveState(initialContest, now))
                    .thenReturn(ContestStatus.RUNNING);
            when(contestRepository.findById(1L))
                    .thenReturn(Optional.of(changedContest));

            Optional<ContestStatus> result = syncService.syncContestStatus(initialContest, now);

            assertThat(result).isEmpty();
            verify(contestRepository, never()).save(any(Contest.class));
        }

        @Test
        @DisplayName("should skip if contest was deleted during sync")
        void shouldSkipIfContestDeleted() {
            Instant now = Instant.now();

            when(contestLifecycleService.resolveEffectiveState(upcomingContest, now))
                    .thenReturn(ContestStatus.RUNNING);
            when(contestRepository.findById(upcomingContest.getId()))
                    .thenReturn(Optional.empty()); // Contest deleted

            Optional<ContestStatus> result = syncService.syncContestStatus(upcomingContest, now);

            assertThat(result).isEmpty();
            verify(contestRepository, never()).save(any(Contest.class));
        }
    }

    @Nested
    @DisplayName("syncAllEligibleContests")
    class SyncAllEligibleContestsTests {

        @Test
        @DisplayName("should sync multiple contests and count updates")
        void shouldSyncMultipleContests() {
            Instant now = Instant.now();

            Contest contest1 = Contest.builder()
                    .id(1L)
                    .status(ContestStatus.UPCOMING)
                    .statusLocked(false)
                    .startTime(now.minus(5, ChronoUnit.MINUTES))
                    .durationMinutes(120)
                    .build();

            Contest contest2 = Contest.builder()
                    .id(2L)
                    .status(ContestStatus.RUNNING)
                    .statusLocked(false)
                    .startTime(now.minus(3, ChronoUnit.HOURS))
                    .durationMinutes(120)
                    .build();

            when(contestRepository.findSyncCandidates(any()))
                    .thenReturn(List.of(contest1, contest2));

            // Contest1: needs update (UPCOMING -> RUNNING)
            when(contestLifecycleService.resolveEffectiveState(eq(contest1), any()))
                    .thenReturn(ContestStatus.RUNNING);
            when(contestRepository.findById(1L))
                    .thenReturn(Optional.of(contest1));
            when(contestRepository.save(contest1))
                    .thenReturn(contest1);

            // Contest2: already in sync (RUNNING -> RUNNING)
            when(contestLifecycleService.resolveEffectiveState(eq(contest2), any()))
                    .thenReturn(ContestStatus.RUNNING);

            int syncedCount = syncService.syncAllEligibleContests();

            assertThat(syncedCount).isEqualTo(1);
            verify(contestRepository, times(1)).save(any(Contest.class));
        }

        @Test
        @DisplayName("should continue syncing other contests if one fails")
        void shouldContinueOnException() {
            Instant now = Instant.now();

            Contest failingContest = Contest.builder()
                    .id(1L)
                    .status(ContestStatus.UPCOMING)
                    .statusLocked(false)
                    .startTime(now.minus(5, ChronoUnit.MINUTES))
                    .durationMinutes(120)
                    .build();

            Contest successContest = Contest.builder()
                    .id(2L)
                    .status(ContestStatus.UPCOMING)
                    .statusLocked(false)
                    .startTime(now.minus(5, ChronoUnit.MINUTES))
                    .durationMinutes(120)
                    .build();

            when(contestRepository.findSyncCandidates(any()))
                    .thenReturn(List.of(failingContest, successContest));

            // First contest throws exception
            when(contestLifecycleService.resolveEffectiveState(eq(failingContest), any()))
                    .thenThrow(new RuntimeException("Database error"));

            // Second contest syncs successfully
            when(contestLifecycleService.resolveEffectiveState(eq(successContest), any()))
                    .thenReturn(ContestStatus.RUNNING);
            when(contestRepository.findById(2L))
                    .thenReturn(Optional.of(successContest));
            when(contestRepository.save(successContest))
                    .thenReturn(successContest);

            int syncedCount = syncService.syncAllEligibleContests();

            assertThat(syncedCount).isEqualTo(1);
            verify(contestRepository).save(successContest);
        }

        @Test
        @DisplayName("should return 0 when no contests require sync")
        void shouldReturnZeroWhenNoContests() {
            when(contestRepository.findSyncCandidates(any()))
                    .thenReturn(List.of());

            int syncedCount = syncService.syncAllEligibleContests();

            assertThat(syncedCount).isEqualTo(0);
            verify(contestRepository, never()).save(any(Contest.class));
        }
    }
}
