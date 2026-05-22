package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardMetadata;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardProblemCell;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRevealResponse;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealCell;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealState;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
import com.server.contestControl.contestServer.scoreboard.exception.InvalidScoreboardRevealStateException;
import com.server.contestControl.contestServer.scoreboard.model.ScoreboardCellKey;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealCellRepository;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealStateRepository;
import com.server.contestControl.contestServer.service.ContestLifecycleService;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void startBuildsRevealQueueFromBottomRankedFrozenCells() {
        Contest contest = contest();
        Instant freezeTime = Instant.parse("2026-05-15T10:00:00Z");
        User alpha = team(100L, "alpha");
        User beta = team(200L, "beta");
        Problem problemA = Problem.builder().id(10L).contest(contest).title("A").build();
        Problem problemB = Problem.builder().id(20L).contest(contest).title("B").build();

        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.ENDED);
        when(revealStateRepository.findByContest_Id(1L)).thenReturn(Optional.empty());
        when(revealStateRepository.save(any())).thenAnswer(invocation -> {
            ScoreboardRevealState state = invocation.getArgument(0);
            if (state.getId() == null) {
                state.setId(9L);
            }
            return state;
        });
        when(freezePolicy.freezeTime(eq(contest), any())).thenReturn(freezeTime);
        when(submissionRepository.findAllByContestIdForScoreboard(1L)).thenReturn(List.of());
        when(calculator.hiddenCellsAfterFreeze(eq(freezeTime), any())).thenReturn(java.util.Set.of(
                new ScoreboardCellKey(alpha.getId(), problemA.getId()),
                new ScoreboardCellKey(beta.getId(), problemB.getId())
        ));
        when(problemRepository.findByContest_IdOrderByIdAsc(1L)).thenReturn(List.of(problemA, problemB));
        when(userRepository.findAllByRoleOrderByUsernameAscIdAsc(Role.TEAM)).thenReturn(List.of(alpha, beta));
        when(versionService.current(1L, ScoreboardAudience.PUBLIC)).thenReturn(5L);
        when(scoreboardService.getSnapshot(1L, ScoreboardAudience.PUBLIC, 5L))
                .thenReturn(snapshot(
                        row(1, alpha, problemA, problemB),
                        row(2, beta, problemA, problemB)
                ));
        when(revealCellRepository.countByRevealState_Id(9L)).thenReturn(2L);
        when(revealCellRepository.countByRevealState_IdAndRevealedTrue(9L)).thenReturn(0L);
        when(revealCellRepository.findFirstByRevealState_IdAndRevealedFalseOrderByRevealOrderAscIdAsc(9L))
                .thenReturn(Optional.empty());

        ScoreboardRevealResponse response = revealService.start(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ScoreboardRevealCell>> queueCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(revealCellRepository).saveAll(queueCaptor.capture());
        List<ScoreboardRevealCell> queue = StreamSupport.stream(queueCaptor.getValue().spliterator(), false)
                .toList();

        assertThat(response.status()).isEqualTo(RevealStatus.IN_PROGRESS);
        assertThat(queue).hasSize(2);
        assertThat(queue.get(0).getTeam().getId()).isEqualTo(beta.getId());
        assertThat(queue.get(0).getProblem().getId()).isEqualTo(problemB.getId());
        assertThat(queue.get(0).getRevealOrder()).isEqualTo(1);
        assertThat(queue.get(1).getTeam().getId()).isEqualTo(alpha.getId());
        assertThat(queue.get(1).getProblem().getId()).isEqualTo(problemA.getId());
        assertThat(queue.get(1).getRevealOrder()).isEqualTo(2);
    }

    @Test
    void revealAllMarksAllCellsAndCompletesReveal() {
        Contest contest = contest();
        ScoreboardRevealState state = revealState(contest);
        List<ScoreboardRevealCell> cells = List.of(
                revealCell(1L, state, 100L, 10L, 1, false),
                revealCell(2L, state, 200L, 20L, 2, false)
        );
        when(revealStateRepository.findByContest_Id(1L)).thenReturn(Optional.of(state));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.ENDED);
        when(revealCellRepository.findByRevealState_IdOrderByRevealOrderAscIdAsc(9L)).thenReturn(cells);
        when(revealCellRepository.countByRevealState_Id(9L)).thenReturn(2L);
        when(revealCellRepository.countByRevealState_IdAndRevealedTrue(9L)).thenReturn(2L);
        when(revealCellRepository.findFirstByRevealState_IdAndRevealedFalseOrderByRevealOrderAscIdAsc(9L))
                .thenReturn(Optional.empty());

        ScoreboardRevealResponse response = revealService.revealAll(1L);

        assertThat(cells).allSatisfy(cell -> {
            assertThat(cell.getRevealed()).isTrue();
            assertThat(cell.getRevealedAt()).isNotNull();
        });
        assertThat(state.getStatus()).isEqualTo(RevealStatus.COMPLETED);
        assertThat(state.getCompletedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(RevealStatus.COMPLETED);
        assertThat(response.revealedCells()).isEqualTo(2L);
        verify(revealCellRepository).saveAll(cells);
        verify(revealStateRepository).save(state);
    }

    @Test
    void resetClearsRevealedCellsAndReturnsToNotStarted() {
        Contest contest = contest();
        ScoreboardRevealState state = revealState(contest);
        state.setCompletedAt(Instant.parse("2026-05-15T12:15:00Z"));
        List<ScoreboardRevealCell> cells = List.of(
                revealCell(1L, state, 100L, 10L, 1, true),
                revealCell(2L, state, 200L, 20L, 2, true)
        );
        when(revealStateRepository.findByContest_Id(1L)).thenReturn(Optional.of(state));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(contestLifecycleService.resolveEffectiveState(any(), any())).thenReturn(ContestStatus.ENDED);
        when(revealCellRepository.findByRevealState_IdOrderByRevealOrderAscIdAsc(9L)).thenReturn(cells);
        when(revealCellRepository.countByRevealState_Id(9L)).thenReturn(2L);
        when(revealCellRepository.countByRevealState_IdAndRevealedTrue(9L)).thenReturn(0L);
        when(revealCellRepository.findFirstByRevealState_IdAndRevealedFalseOrderByRevealOrderAscIdAsc(9L))
                .thenReturn(Optional.of(cells.getFirst()));

        ScoreboardRevealResponse response = revealService.reset(1L);

        assertThat(cells).allSatisfy(cell -> {
            assertThat(cell.getRevealed()).isFalse();
            assertThat(cell.getRevealedAt()).isNull();
        });
        assertThat(state.getStatus()).isEqualTo(RevealStatus.NOT_STARTED);
        assertThat(state.getCompletedAt()).isNull();
        assertThat(response.status()).isEqualTo(RevealStatus.NOT_STARTED);
        assertThat(response.revealedCells()).isZero();
        verify(revealCellRepository).saveAll(cells);
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

    private ScoreboardRevealState revealState(Contest contest) {
        return ScoreboardRevealState.builder()
                .id(9L)
                .contest(contest)
                .status(RevealStatus.IN_PROGRESS)
                .startedAt(Instant.parse("2026-05-15T12:00:00Z"))
                .build();
    }

    private ScoreboardRevealCell revealCell(
            Long id,
            ScoreboardRevealState state,
            Long teamId,
            Long problemId,
            int order,
            boolean revealed
    ) {
        return ScoreboardRevealCell.builder()
                .id(id)
                .revealState(state)
                .team(User.builder().id(teamId).username("team-" + teamId).build())
                .problem(Problem.builder().id(problemId).contest(state.getContest()).build())
                .revealOrder(order)
                .revealed(revealed)
                .revealedAt(revealed ? Instant.parse("2026-05-15T12:10:00Z") : null)
                .build();
    }

    private User team(Long id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .role(Role.TEAM)
                .build();
    }

    private ScoreboardSnapshot snapshot(ScoreboardRow... rows) {
        ScoreboardMetadata metadata = new ScoreboardMetadata(
                1L,
                "ICPC Local",
                ScoreboardAudience.PUBLIC,
                5L,
                Instant.parse("2026-05-15T12:00:00Z"),
                "ENDED",
                "ENDED",
                false,
                true,
                Instant.parse("2026-05-15T10:00:00Z"),
                30,
                20,
                RevealStatus.NOT_STARTED,
                0L,
                2L,
                List.of()
        );
        return new ScoreboardSnapshot(metadata, List.of(rows));
    }

    private ScoreboardRow row(int rank, User team, Problem problemA, Problem problemB) {
        return new ScoreboardRow(
                rank,
                team.getId(),
                team.getUsername(),
                0,
                0,
                List.of(
                        cell(problemA, "A"),
                        cell(problemB, "B")
                )
        );
    }

    private ScoreboardProblemCell cell(Problem problem, String label) {
        return new ScoreboardProblemCell(
                problem.getId(),
                label,
                false,
                0,
                0,
                0,
                null,
                null,
                false,
                true,
                false
        );
    }
}
