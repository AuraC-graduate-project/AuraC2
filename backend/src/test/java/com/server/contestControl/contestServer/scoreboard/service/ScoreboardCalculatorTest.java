package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardProblemCell;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow;
import com.server.contestControl.contestServer.scoreboard.model.ScoreboardCellKey;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreboardCalculatorTest {

    private static final Instant START = Instant.parse("2026-05-15T09:00:00Z");

    private ScoreboardCalculator calculator;
    private Contest contest;
    private Problem problemA;
    private Problem problemB;
    private User alpha;
    private User beta;
    private User admin;

    @BeforeEach
    void setUp() {
        calculator = new ScoreboardCalculator(new ScoreboardRankingService());
        contest = Contest.builder()
                .id(1L)
                .title("ICPC Local")
                .actualStartTime(START)
                .penaltyMinutes(20)
                .build();
        problemA = Problem.builder().id(10L).contest(contest).title("A").build();
        problemB = Problem.builder().id(20L).contest(contest).title("B").build();
        alpha = team(100L, "alpha");
        beta = team(200L, "beta");
        admin = User.builder().id(1L).username("admin").role(Role.ADMIN).build();
    }

    @Test
    void calculatesIcpcScorePenaltyFirstToSolveAndExcludesAdmins() {
        List<Submission> submissions = List.of(
                submission(1L, alpha, problemA, Verdict.WRONG_ANSWER, 5),
                submission(2L, alpha, problemA, Verdict.ACCEPTED, 30),
                submission(3L, beta, problemA, Verdict.ACCEPTED, 25),
                submission(4L, beta, problemB, Verdict.WRONG_ANSWER, 10),
                submission(5L, admin, problemB, Verdict.ACCEPTED, 2)
        );

        List<ScoreboardRow> rows = calculator.calculateRows(
                contest,
                List.of(problemA, problemB),
                List.of(alpha, beta),
                submissions,
                Set.of(),
                Set.of()
        );

        ScoreboardRow betaRow = rows.get(0);
        ScoreboardRow alphaRow = rows.get(1);

        assertThat(betaRow.rank()).isEqualTo(1);
        assertThat(betaRow.teamName()).isEqualTo("beta");
        assertThat(betaRow.solvedCount()).isEqualTo(1);
        assertThat(betaRow.totalPenalty()).isEqualTo(25);
        assertThat(cell(betaRow, 10L).firstToSolve()).isTrue();
        assertThat(cell(betaRow, 20L).attempts()).isEqualTo(1);
        assertThat(cell(betaRow, 20L).solved()).isFalse();

        assertThat(alphaRow.rank()).isEqualTo(2);
        assertThat(alphaRow.teamName()).isEqualTo("alpha");
        assertThat(alphaRow.solvedCount()).isEqualTo(1);
        assertThat(alphaRow.totalPenalty()).isEqualTo(50);
        assertThat(cell(alphaRow, 10L).wrongAttempts()).isEqualTo(1);
        assertThat(cell(alphaRow, 10L).solvedTimeMinutes()).isEqualTo(30);
        assertThat(cell(alphaRow, 10L).penalty()).isEqualTo(50);
        assertThat(rows).allSatisfy(row -> assertThat(cell(row, 20L).solved()).isFalse());
    }

    @Test
    void tiedRowsShareRankAndRemainStableByNameThenId() {
        User charlie = team(300L, "charlie");
        User delta = team(400L, "delta");

        List<ScoreboardRow> rows = calculator.calculateRows(
                contest,
                List.of(problemA),
                List.of(delta, charlie),
                List.of(
                        submission(1L, delta, problemA, Verdict.ACCEPTED, 15),
                        submission(2L, charlie, problemA, Verdict.ACCEPTED, 15)
                ),
                Set.of(),
                Set.of()
        );

        assertThat(rows).extracting(ScoreboardRow::teamName).containsExactly("charlie", "delta");
        assertThat(rows).extracting(ScoreboardRow::rank).containsExactly(1, 1);
    }

    @Test
    void hiddenCellsAfterFreezeIncludesTerminalTeamSubmissionsAtOrAfterFreeze() {
        Instant freezeTime = START.plusSeconds(60 * 60);

        Set<ScoreboardCellKey> hiddenCells = calculator.hiddenCellsAfterFreeze(
                freezeTime,
                List.of(
                        submission(1L, alpha, problemA, Verdict.WRONG_ANSWER, 59),
                        submission(2L, alpha, problemA, Verdict.ACCEPTED, 60),
                        submission(3L, beta, problemB, Verdict.RUNNING, 61),
                        submission(4L, admin, problemB, Verdict.ACCEPTED, 90)
                )
        );

        assertThat(hiddenCells).containsExactly(new ScoreboardCellKey(alpha.getId(), problemA.getId()));
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
