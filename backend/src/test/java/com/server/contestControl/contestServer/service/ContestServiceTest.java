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
    @DisplayName("updateContestDetails should reject non-upcoming contests")
    void shouldRejectNonUpcomingContest() {
        upcomingContest.setStatus(ContestStatus.RUNNING);
        when(contestRepository.findById(upcomingContest.getId())).thenReturn(Optional.of(upcomingContest));

        assertThatThrownBy(() -> contestService.updateContestDetails(
                upcomingContest.getId(),
                validRequest()
        )).isInstanceOf(InvalidContestStateException.class);

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

    private ContestUpdateRequest validRequest() {
        return new ContestUpdateRequest(
                "Updated Contest",
                Instant.now().plus(1, ChronoUnit.HOURS),
                120,
                30,
                20
        );
    }
}
