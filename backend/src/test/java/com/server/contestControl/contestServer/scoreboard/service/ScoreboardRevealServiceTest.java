package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRevealResponse;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealCell;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealState;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.exception.InvalidScoreboardRevealStateException;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealCellRepository;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealStateRepository;
import com.server.contestControl.contestServer.service.ContestLifecycleService;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreboardRevealServiceTest {

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ScoreboardRevealStateRepository revealStateRepository;

    @Mock
    private ScoreboardRevealCellRepository revealCellRepository;

    @Mock
    private ContestLifecycleService contestLifecycleService;

    @Mock
    private ScoreboardFreezePolicy freezePolicy;

    @Mock
    private ScoreboardCalculator calculator;

    @Mock
    private ScoreboardService scoreboardService;

    @Mock
    private ScoreboardVersionService versionService;

    @InjectMocks
    private ScoreboardRevealService revealService;

    @Test
    void getStateReturnsNotStartedWhenRevealWasNeverPersisted() {
        Contest contest = contest();
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(revealStateRepository.findByContest_Id(1L)).thenReturn(Optional.empty());

        ScoreboardRevealResponse response = revealService.getState(1L);

        assertThat(response.contestId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(RevealStatus.NOT_STARTED);
        assertThat(response.totalCells()).isZero();
        assertThat(response.revealedCells()).isZero();
        verify(revealCellRepository, never()).countByRevealState_Id(any());
    }

    @Test
    void startRejectsContestThatHasNotEnded() {
        Contest contest = contest();
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.RUNNING);

        assertThatThrownBy(() -> revealService.start(1L))
                .isInstanceOf(InvalidScoreboardRevealStateException.class)
                .hasMessageContaining("after the contest ends");

        verify(revealStateRepository, never()).save(any());
        verify(revealCellRepository, never()).saveAll(any());
    }

    @Test
    void revealNextMarksTheNextCellAndCompletesWhenNoHiddenCellsRemain() {
        Contest contest = contest();
        ScoreboardRevealState state = ScoreboardRevealState.builder()
                .id(9L)
                .contest(contest)
                .status(RevealStatus.IN_PROGRESS)
                .startedAt(Instant.parse("2026-05-15T12:00:00Z"))
                .build();
        ScoreboardRevealCell cell = ScoreboardRevealCell.builder()
                .id(33L)
                .revealState(state)
                .team(User.builder().id(100L).username("alpha").build())
                .problem(Problem.builder().id(10L).contest(contest).build())
                .revealOrder(1)
                .revealed(false)
                .build();

        when(revealStateRepository.findByContest_Id(1L)).thenReturn(Optional.of(state));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.ENDED);
        when(revealCellRepository.findFirstByRevealState_IdAndRevealedFalseOrderByRevealOrderAscIdAsc(9L))
                .thenReturn(Optional.of(cell), Optional.empty(), Optional.empty());
        when(revealCellRepository.countByRevealState_Id(9L)).thenReturn(1L);
        when(revealCellRepository.countByRevealState_IdAndRevealedTrue(9L)).thenReturn(1L);

        ScoreboardRevealResponse response = revealService.revealNext(1L);

        assertThat(cell.getRevealed()).isTrue();
        assertThat(cell.getRevealedAt()).isNotNull();
        assertThat(state.getStatus()).isEqualTo(RevealStatus.COMPLETED);
        assertThat(state.getCompletedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(RevealStatus.COMPLETED);
        assertThat(response.revealedCells()).isEqualTo(1L);
        verify(revealCellRepository).save(cell);
        verify(revealStateRepository).save(state);
    }

    private Contest contest() {
        return Contest.builder()
                .id(1L)
                .title("ICPC Local")
                .status(ContestStatus.ENDED)
                .actualStartTime(Instant.parse("2026-05-15T09:00:00Z"))
                .durationMinutes(120)
                .scoreboardFreezeMinutes(30)
                .penaltyMinutes(20)
                .build();
    }
}
