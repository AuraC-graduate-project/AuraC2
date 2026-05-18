package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.dto.contest.ContestUpdateRequest;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.contestServer.exception.ContestValidationException;
import com.server.contestControl.contestServer.exception.InvalidContestStateException;
import com.server.contestControl.contestServer.repository.ContestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContestServiceTest {

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestLifecycleService contestLifecycleService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ContestService contestService;

    private Contest upcomingContest;

    @BeforeEach
    void setUp() {
        upcomingContest = Contest.builder()
                .id(1L)
                .title("Original Contest")
                .description("Original description")
                .status(ContestStatus.UPCOMING)
                .startTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .durationMinutes(120)
                .scoreboardFreezeMinutes(30)
                .penaltyMinutes(20)
                .build();
    }

    @Test
    @DisplayName("updateContestDetails should save editable fields and publish UPDATED event")
    void shouldUpdateUpcomingContestAndPublishUpdatedEvent() {
        Instant newStart = Instant.now().plus(2, ChronoUnit.HOURS);
        ContestUpdateRequest request = new ContestUpdateRequest(
                "Updated Contest",
                "Updated description",
                newStart,
                180,
                45,
                15
        );
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.UPCOMING);
        when(contestLifecycleService.resolveRemainingMillis(any(), any())).thenReturn(180L * 60_000L);

        ContestResponse response = contestService.updateContestDetails(upcomingContest.getId(), request);

        assertThat(response.getTitle()).isEqualTo("Updated Contest");
        assertThat(response.getDurationMinutes()).isEqualTo(180);
        assertThat(response.getScoreboardFreezeMinutes()).isEqualTo(45);
        assertThat(response.getPenaltyMinutes()).isEqualTo(15);
        assertThat(upcomingContest.getTitle()).isEqualTo("Updated Contest");
        assertThat(upcomingContest.getDescription()).isEqualTo("Updated description");
        assertThat(upcomingContest.getStartTime()).isEqualTo(newStart);
        assertThat(upcomingContest.getDurationMinutes()).isEqualTo(180);
        assertThat(upcomingContest.getScoreboardFreezeMinutes()).isEqualTo(45);
        assertThat(upcomingContest.getPenaltyMinutes()).isEqualTo(15);
        verify(contestRepository).save(upcomingContest);

        ArgumentCaptor<ContestUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(ContestUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().reason()).isEqualTo(ContestUpdatedEvent.Reason.UPDATED);
        assertThat(eventCaptor.getValue().snapshot().getId()).isEqualTo(upcomingContest.getId());
    }

    @Test
    @DisplayName("updateContestDetails should allow safe fields while contest is running")
    void shouldAllowSafeRunningContestUpdatesAndDurationIncrease() {
        upcomingContest.setStatus(ContestStatus.RUNNING);
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.RUNNING);
        when(contestLifecycleService.resolveRemainingMillis(any(), any())).thenReturn(150L * 60_000L);

        ContestUpdateRequest request = new ContestUpdateRequest(
                "Running Rename",
                "Updated while running",
                upcomingContest.getStartTime(),
                180,
                15,
                25
        );

        ContestResponse response = contestService.updateContestDetails(upcomingContest.getId(), request);

        assertThat(response.getTitle()).isEqualTo("Running Rename");
        assertThat(upcomingContest.getDescription()).isEqualTo("Updated while running");
        assertThat(upcomingContest.getScoreboardFreezeMinutes()).isEqualTo(15);
        assertThat(upcomingContest.getPenaltyMinutes()).isEqualTo(25);
        assertThat(upcomingContest.getStartTime()).isEqualTo(request.startTime());
        assertThat(upcomingContest.getDurationMinutes()).isEqualTo(180);
        verify(contestRepository).save(upcomingContest);
        verify(eventPublisher).publishEvent(any(ContestUpdatedEvent.class));
    }

    @Test
    @DisplayName("updateContestDetails should reject timing changes while contest is running")
    void shouldRejectRunningContestTimingChanges() {
        upcomingContest.setStatus(ContestStatus.RUNNING);
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.RUNNING);

        assertThatThrownBy(() -> contestService.updateContestDetails(
                upcomingContest.getId(),
                new ContestUpdateRequest(
                        "Running Rename",
                        "Updated while running",
                        upcomingContest.getStartTime().plus(5, ChronoUnit.MINUTES),
                        upcomingContest.getDurationMinutes(),
                        15,
                        25
                )
        )).isInstanceOf(InvalidContestStateException.class)
                .hasMessageContaining("Start time is locked");

        verify(contestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("updateContestDetails should reject duration decreases while contest is running")
    void shouldRejectRunningContestDurationDecrease() {
        upcomingContest.setStatus(ContestStatus.RUNNING);
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.RUNNING);

        assertThatThrownBy(() -> contestService.updateContestDetails(
                upcomingContest.getId(),
                new ContestUpdateRequest(
                        "Running Rename",
                        "Updated while running",
                        upcomingContest.getStartTime(),
                        90,
                        15,
                        25
                )
        )).isInstanceOf(InvalidContestStateException.class)
                .hasMessageContaining("Duration can only be increased");

        verify(contestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("updateContestDetails should allow safe fields while contest is paused")
    void shouldAllowSafePausedContestUpdatesAndDurationIncrease() {
        upcomingContest.setStatus(ContestStatus.PAUSED);
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.PAUSED);
        when(contestLifecycleService.resolveRemainingMillis(any(), any())).thenReturn(120L * 60_000L);

        ContestUpdateRequest request = new ContestUpdateRequest(
                "Paused Rename",
                "Updated while paused",
                upcomingContest.getStartTime(),
                180,
                10,
                30
        );

        contestService.updateContestDetails(upcomingContest.getId(), request);

        assertThat(upcomingContest.getTitle()).isEqualTo("Paused Rename");
        assertThat(upcomingContest.getDescription()).isEqualTo("Updated while paused");
        assertThat(upcomingContest.getDurationMinutes()).isEqualTo(180);
        assertThat(upcomingContest.getScoreboardFreezeMinutes()).isEqualTo(10);
        assertThat(upcomingContest.getPenaltyMinutes()).isEqualTo(30);
        verify(contestRepository).save(upcomingContest);
        verify(eventPublisher).publishEvent(any(ContestUpdatedEvent.class));
    }

    @Test
    @DisplayName("updateContestDetails should allow title and description only after contest ended")
    void shouldAllowEndedContestTitleAndDescriptionOnly() {
        upcomingContest.setStatus(ContestStatus.ENDED);
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.ENDED);
        when(contestLifecycleService.resolveRemainingMillis(any(), any())).thenReturn(0L);

        ContestUpdateRequest request = new ContestUpdateRequest(
                "Archived Rename",
                "Clarified archive notes",
                upcomingContest.getStartTime(),
                upcomingContest.getDurationMinutes(),
                upcomingContest.getScoreboardFreezeMinutes(),
                upcomingContest.getPenaltyMinutes()
        );

        ContestResponse response = contestService.updateContestDetails(upcomingContest.getId(), request);

        assertThat(response.getTitle()).isEqualTo("Archived Rename");
        assertThat(upcomingContest.getDescription()).isEqualTo("Clarified archive notes");
        assertThat(upcomingContest.getScoreboardFreezeMinutes()).isEqualTo(30);
        assertThat(upcomingContest.getPenaltyMinutes()).isEqualTo(20);
        verify(contestRepository).save(upcomingContest);
        verify(eventPublisher).publishEvent(any(ContestUpdatedEvent.class));
    }

    @Test
    @DisplayName("updateContestDetails should reject scoring changes after contest ended")
    void shouldRejectEndedContestScoringChanges() {
        upcomingContest.setStatus(ContestStatus.ENDED);
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.ENDED);

        assertThatThrownBy(() -> contestService.updateContestDetails(
                upcomingContest.getId(),
                new ContestUpdateRequest(
                        "Archived Rename",
                        "Clarified archive notes",
                        upcomingContest.getStartTime(),
                        upcomingContest.getDurationMinutes(),
                        upcomingContest.getScoreboardFreezeMinutes(),
                        25
                )
        )).isInstanceOf(InvalidContestStateException.class)
                .hasMessageContaining("Ended contests only allow title and description");

        verify(contestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("updateContestDetails should reject past start time")
    void shouldRejectPastStartTime() {
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));

        assertThatThrownBy(() -> contestService.updateContestDetails(
                upcomingContest.getId(),
                new ContestUpdateRequest(
                        "Updated Contest",
                        "Updated description",
                        Instant.now().minus(1, ChronoUnit.MINUTES),
                        120,
                        30,
                        20
                )
        )).isInstanceOf(ContestValidationException.class)
                .hasMessageContaining("Start time");

        verify(contestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("updateContestDetails should reject freeze greater than or equal to duration")
    void shouldRejectFreezeAtOrAfterDuration() {
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));

        assertThatThrownBy(() -> contestService.updateContestDetails(
                upcomingContest.getId(),
                new ContestUpdateRequest(
                        "Updated Contest",
                        "Updated description",
                        Instant.now().plus(1, ChronoUnit.HOURS),
                        120,
                        120,
                        20
                )
        )).isInstanceOf(ContestValidationException.class)
                .hasMessageContaining("Scoreboard freeze");

        verify(contestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("updateContestDetails should reject negative freeze")
    void shouldRejectNegativeFreeze() {
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));

        assertThatThrownBy(() -> contestService.updateContestDetails(
                upcomingContest.getId(),
                new ContestUpdateRequest(
                        "Updated Contest",
                        "Updated description",
                        Instant.now().plus(1, ChronoUnit.HOURS),
                        120,
                        -1,
                        20
                )
        )).isInstanceOf(ContestValidationException.class)
                .hasMessageContaining("Scoreboard freeze");

        verify(contestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("updateContestDetails should reject negative penalty")
    void shouldRejectNegativePenalty() {
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));

        assertThatThrownBy(() -> contestService.updateContestDetails(
                upcomingContest.getId(),
                new ContestUpdateRequest(
                        "Updated Contest",
                        "Updated description",
                        Instant.now().plus(1, ChronoUnit.HOURS),
                        120,
                        30,
                        -1
                )
        )).isInstanceOf(ContestValidationException.class)
                .hasMessageContaining("Penalty");

        verify(contestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

}
