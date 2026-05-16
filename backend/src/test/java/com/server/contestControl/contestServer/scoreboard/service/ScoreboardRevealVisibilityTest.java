package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardProblemCell;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealCell;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealState;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealCellRepository;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealStateRepository;
import com.server.contestControl.contestServer.service.ContestLifecycleService;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreboardRevealVisibilityTest {

    private static final Long CONTEST_ID = 1L;
    private static final Instant START = Instant.parse("2026-05-15T09:00:00Z");
    private static final Instant FREEZE = START.plusSeconds(60 * 60);

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
    private ScoreboardVersionService versionService;

    private ScoreboardService service;
    private Contest contest;
    private Problem problemA;
    private Problem problemB;
    private User alpha;
    private User beta;

    @BeforeEach
    void setUp() {
        ScoreboardFreezePolicy freezePolicy = new ScoreboardFreezePolicy(contestLifecycleService);
        ScoreboardCalculator calculator = new ScoreboardCalculator(new ScoreboardRankingService());
        service = new ScoreboardService(
                contestRepository,
                problemRepository,
                userRepository,
                submissionRepository,
                revealStateRepository,
                revealCellRepository,
                contestLifecycleService,
                freezePolicy,
                calculator,
                versionService
        );

        contest = Contest.builder()
                .id(CONTEST_ID)
                .title("ICPC Local")
                .status(ContestStatus.ENDED)
                .actualStartTime(START)
                .durationMinutes(120)
                .scoreboardFreezeMinutes(60)
                .penaltyMinutes(20)
                .build();
        problemA = Problem.builder().id(10L).contest(contest).title("A").build();
        problemB = Problem.builder().id(20L).contest(contest).title("B").build();
        alpha = team(100L, "alpha");
        beta = team(200L, "beta");
    }

    @Test
    void frozenAcceptedSubmissionIsHiddenBeforeReveal() {
        givenScoreboard(List.of(
                submission(1L, alpha, problemA, Verdict.ACCEPTED, 30),
                submission(2L, beta, problemB, Verdict.ACCEPTED, 80)
        ));
        noRevealStarted();

        ScoreboardSnapshot snapshot = publicSnapshot();

        ScoreboardRow alphaRow = row(snapshot, alpha);
        ScoreboardRow betaRow = row(snapshot, beta);
        ScoreboardProblemCell hiddenCell = cell(betaRow, problemB);

        assertThat(alphaRow.rank()).isEqualTo(1);
        assertThat(betaRow.rank()).isEqualTo(2);
        assertThat(betaRow.solvedCount()).isZero();
        assertThat(betaRow.totalPenalty()).isZero();
        assertThat(hiddenCell.hidden()).isTrue();
        assertThat(hiddenCell.revealed()).isFalse();
        assertThat(hiddenCell.solved()).isFalse();
        assertThat(hiddenCell.penalty()).isNull();
        assertThat(hiddenCell.solvedTimeMinutes()).isNull();
        assertThat(hiddenCell.firstToSolve()).isFalse();
    }

    @Test
    void revealNextChangesScoreAndRank() {
        givenScoreboard(List.of(
                submission(1L, alpha, problemA, Verdict.WRONG_ANSWER, 5),
                submission(2L, alpha, problemA, Verdict.WRONG_ANSWER, 20),
                submission(3L, alpha, problemA, Verdict.ACCEPTED, 55),
                submission(4L, beta, problemB, Verdict.ACCEPTED, 61)
        ));
        noRevealStarted();

        ScoreboardSnapshot beforeReveal = publicSnapshot();

        assertThat(row(beforeReveal, alpha).rank()).isEqualTo(1);
        assertThat(row(beforeReveal, beta).rank()).isEqualTo(2);
        assertThat(row(beforeReveal, beta).solvedCount()).isZero();

        revealInProgress(revealedCell(beta, problemB));

        ScoreboardSnapshot afterReveal = publicSnapshot();

        ScoreboardRow betaRow = row(afterReveal, beta);
        assertThat(betaRow.rank()).isEqualTo(1);
        assertThat(betaRow.solvedCount()).isEqualTo(1);
        assertThat(betaRow.totalPenalty()).isEqualTo(61);
        assertThat(row(afterReveal, alpha).rank()).isEqualTo(2);
    }

    @Test
    void revealAllProducesFinalOfficialScoreboardMatchingAdminLive() {
        givenScoreboard(List.of(
                submission(1L, alpha, problemA, Verdict.ACCEPTED, 25),
                submission(2L, beta, problemA, Verdict.WRONG_ANSWER, 65),
                submission(3L, beta, problemA, Verdict.ACCEPTED, 70),
                submission(4L, beta, problemB, Verdict.ACCEPTED, 75)
        ));
        revealCompleted(
                revealedCell(beta, problemA),
                revealedCell(beta, problemB)
        );

        ScoreboardSnapshot official = publicSnapshot();
        ScoreboardSnapshot adminLive = adminSnapshot();

        assertThat(official.rows())
                .usingRecursiveComparison()
                .isEqualTo(adminLive.rows());
    }

    @Test
    void firstToSolveDuringFreezeAppearsOnlyAfterReveal() {
        givenScoreboard(List.of(
                submission(1L, beta, problemB, Verdict.ACCEPTED, 75)
        ));
        noRevealStarted();

        ScoreboardSnapshot beforeReveal = publicSnapshot();

        assertThat(cell(row(beforeReveal, beta), problemB).firstToSolve()).isFalse();
        assertThat(cell(row(beforeReveal, beta), problemB).solved()).isFalse();

        revealInProgress(revealedCell(beta, problemB));

        ScoreboardSnapshot afterReveal = publicSnapshot();

        assertThat(cell(row(afterReveal, beta), problemB).firstToSolve()).isTrue();
        assertThat(cell(row(afterReveal, beta), problemB).solved()).isTrue();
    }

    @Test
    void wrongAttemptsBeforeHiddenAcceptedSubmissionAreAppliedOnlyAfterReveal() {
        givenScoreboard(List.of(
                submission(1L, beta, problemB, Verdict.WRONG_ANSWER, 62),
                submission(2L, beta, problemB, Verdict.TLE, 63),
                submission(3L, beta, problemB, Verdict.ACCEPTED, 65)
        ));
        noRevealStarted();

        ScoreboardSnapshot beforeReveal = publicSnapshot();

        ScoreboardRow beforeRow = row(beforeReveal, beta);
        ScoreboardProblemCell beforeCell = cell(beforeRow, problemB);
        assertThat(beforeRow.solvedCount()).isZero();
        assertThat(beforeRow.totalPenalty()).isZero();
        assertThat(beforeCell.wrongAttempts()).isZero();
        assertThat(beforeCell.penalty()).isNull();

        revealInProgress(revealedCell(beta, problemB));

        ScoreboardSnapshot afterReveal = publicSnapshot();

        ScoreboardRow afterRow = row(afterReveal, beta);
        ScoreboardProblemCell afterCell = cell(afterRow, problemB);
        assertThat(afterRow.solvedCount()).isEqualTo(1);
        assertThat(afterRow.totalPenalty()).isEqualTo(105);
        assertThat(afterCell.wrongAttempts()).isEqualTo(2);
        assertThat(afterCell.penalty()).isEqualTo(105);
    }

    @Test
    void revealedFrozenCellRecalculatesAfterRejudgeChangesVerdict() {
        Submission frozenSubmission = submission(1L, beta, problemB, Verdict.ACCEPTED, 65);
        givenScoreboard(List.of(frozenSubmission));
        revealInProgress(revealedCell(beta, problemB));

        ScoreboardSnapshot accepted = publicSnapshot();
        assertThat(row(accepted, beta).rank()).isEqualTo(1);
        assertThat(row(accepted, beta).solvedCount()).isEqualTo(1);
        assertThat(row(accepted, beta).totalPenalty()).isEqualTo(65);

        frozenSubmission.setVerdict(Verdict.WRONG_ANSWER);

        ScoreboardSnapshot rejected = publicSnapshot();
        assertThat(row(rejected, beta).solvedCount()).isZero();
        assertThat(row(rejected, beta).totalPenalty()).isZero();
        assertThat(cell(row(rejected, beta), problemB).solved()).isFalse();

        frozenSubmission.setVerdict(Verdict.ACCEPTED);

        ScoreboardSnapshot acceptedAgain = publicSnapshot();
        assertThat(row(acceptedAgain, beta).solvedCount()).isEqualTo(1);
        assertThat(row(acceptedAgain, beta).totalPenalty()).isEqualTo(65);
        assertThat(cell(row(acceptedAgain, beta), problemB).solved()).isTrue();
    }

    @Test
    void adminLiveSnapshotKeepsFrozenCellsVisible() {
        givenScoreboard(List.of(
                submission(1L, beta, problemB, Verdict.ACCEPTED, 80)
        ));
        noRevealStarted();

        ScoreboardSnapshot adminLive = adminSnapshot();

        ScoreboardProblemCell cell = cell(row(adminLive, beta), problemB);
        assertThat(cell.solved()).isTrue();
        assertThat(cell.hidden()).isFalse();
        assertThat(row(adminLive, beta).solvedCount()).isEqualTo(1);
        assertThat(row(adminLive, beta).totalPenalty()).isEqualTo(80);
    }

    private void givenScoreboard(List<Submission> submissions) {
        when(contestRepository.findById(CONTEST_ID)).thenReturn(Optional.of(contest));
        when(problemRepository.findByContest_IdOrderByIdAsc(CONTEST_ID)).thenReturn(List.of(problemA, problemB));
        when(userRepository.findAllByRoleOrderByUsernameAscIdAsc(Role.TEAM)).thenReturn(List.of(alpha, beta));
        when(submissionRepository.findAllByContestIdForScoreboard(CONTEST_ID)).thenReturn(submissions);
        when(contestLifecycleService.resolveEffectiveScoreboardFreezeTime(eq(contest), any())).thenReturn(FREEZE);
        when(contestLifecycleService.resolveEffectiveState(eq(contest), any())).thenReturn(ContestStatus.ENDED);
    }

    private void noRevealStarted() {
        when(revealStateRepository.findByContest_Id(CONTEST_ID)).thenReturn(Optional.empty());
    }

    private void revealInProgress(ScoreboardRevealCell... cells) {
        revealState(RevealStatus.IN_PROGRESS, cells);
    }

    private void revealCompleted(ScoreboardRevealCell... cells) {
        revealState(RevealStatus.COMPLETED, cells);
    }

    private void revealState(RevealStatus status, ScoreboardRevealCell... cells) {
        ScoreboardRevealState state = ScoreboardRevealState.builder()
                .id(9L)
                .contest(contest)
                .status(status)
                .build();
        for (ScoreboardRevealCell cell : cells) {
            cell.setRevealState(state);
        }
        when(revealStateRepository.findByContest_Id(CONTEST_ID)).thenReturn(Optional.of(state));
        when(revealCellRepository.findByRevealState_IdOrderByRevealOrderAscIdAsc(9L)).thenReturn(List.of(cells));
    }

    private ScoreboardRevealCell revealedCell(User team, Problem problem) {
        return ScoreboardRevealCell.builder()
                .team(team)
                .problem(problem)
                .revealOrder(1)
                .revealed(true)
                .build();
    }

    private ScoreboardSnapshot publicSnapshot() {
        return service.getSnapshot(CONTEST_ID, ScoreboardAudience.PUBLIC, 1L);
    }

    private ScoreboardSnapshot adminSnapshot() {
        return service.getSnapshot(CONTEST_ID, ScoreboardAudience.ADMIN, 1L);
    }

    private ScoreboardRow row(ScoreboardSnapshot snapshot, User team) {
        return snapshot.rows().stream()
                .filter(candidate -> candidate.teamId().equals(team.getId()))
                .findFirst()
                .orElseThrow();
    }

    private ScoreboardProblemCell cell(ScoreboardRow row, Problem problem) {
        return cell(row, problem.getId());
    }

    private ScoreboardProblemCell cell(ScoreboardRow row, Long problemId) {
        return row.problemCells().stream()
                .filter(candidate -> candidate.problemId().equals(problemId))
                .findFirst()
                .orElseThrow();
    }

    private User team(Long id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .role(Role.TEAM)
                .build();
    }

    private Submission submission(Long id, User user, Problem problem, Verdict verdict, long minutesAfterStart) {
        return Submission.builder()
                .id(id)
                .contest(contest)
                .problem(problem)
                .user(user)
                .verdict(verdict)
                .createdAt(LocalDateTime.ofInstant(START.plusSeconds(minutesAfterStart * 60), ZoneId.systemDefault()))
                .build();
    }
}
