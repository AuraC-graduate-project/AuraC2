package com.server.contestControl.contestServer.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Clarification;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.exceptions.ProblemDeletionConflictException;
import com.server.contestControl.contestServer.repository.ClarificationRepository;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealCell;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealState;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealCellRepository;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealStateRepository;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProblemDeletionPersistenceTest {

    @Autowired private ProblemRepository problemRepository;
    @Autowired private ContestRepository contestRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private ClarificationRepository clarificationRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private ScoreboardRevealCellRepository scoreboardRevealCellRepository;
    @Autowired private ScoreboardRevealStateRepository scoreboardRevealStateRepository;
    @Autowired private UserRepository userRepository;

    private ProblemService problemService;

    @BeforeEach
    void setUp() {
        problemService = new ProblemService(
                problemRepository,
                contestRepository,
                clarificationRepository,
                submissionRepository,
                testCaseRepository,
                scoreboardRevealCellRepository
        );
    }

    @Test
    void deletesProblemWithOnlyTestCasesWithoutTransientObjectException() {
        Problem problem = problem();
        TestCase testCase = testCaseRepository.save(TestCase.builder()
                .problem(problem)
                .inputData("1 2")
                .expectedOutput("3")
                .isPublic(false)
                .build());

        assertThatNoException().isThrownBy(() -> problemService.deleteProblem(problem.getId()));

        assertThat(problemRepository.findById(problem.getId())).isEmpty();
        assertThat(testCaseRepository.findById(testCase.getId())).isEmpty();
    }

    @Test
    void rejectsProblemDeletionWhenSubmissionsExist() {
        Problem problem = problem();
        User team = team("team-submission");
        submissionRepository.save(Submission.builder()
                .contest(problem.getContest())
                .problem(problem)
                .user(team)
                .code("class Main {}")
                .language("java")
                .verdict(Verdict.ACCEPTED)
                .judgeRunId(1L)
                .build());

        assertThatThrownBy(() -> problemService.deleteProblem(problem.getId()))
                .isInstanceOf(ProblemDeletionConflictException.class)
                .hasMessageContaining("submissions or judging history");

        assertThat(problemRepository.findById(problem.getId())).isPresent();
    }

    @Test
    void rejectsProblemDeletionWhenScoreboardRevealCellsExist() {
        Problem problem = problem();
        User team = team("team-reveal");
        ScoreboardRevealState state = scoreboardRevealStateRepository.save(ScoreboardRevealState.builder()
                .contest(problem.getContest())
                .build());
        scoreboardRevealCellRepository.save(ScoreboardRevealCell.builder()
                .revealState(state)
                .team(team)
                .problem(problem)
                .revealOrder(1)
                .revealed(false)
                .build());

        assertThatThrownBy(() -> problemService.deleteProblem(problem.getId()))
                .isInstanceOf(ProblemDeletionConflictException.class)
                .hasMessageContaining("scoreboard reveal history");

        assertThat(problemRepository.findById(problem.getId())).isPresent();
    }

    @Test
    void rejectsProblemDeletionWhenClarificationsReferenceProblem() {
        Problem problem = problem();
        User team = team("team-clarification");
        clarificationRepository.save(Clarification.builder()
                .contest(problem.getContest())
                .problem(problem)
                .user(team)
                .question("Can you clarify?")
                .build());

        assertThatThrownBy(() -> problemService.deleteProblem(problem.getId()))
                .isInstanceOf(ProblemDeletionConflictException.class)
                .hasMessageContaining("clarifications");

        assertThat(problemRepository.findById(problem.getId())).isPresent();
    }

    private Problem problem() {
        Contest contest = contestRepository.save(Contest.builder()
                .title("Contest")
                .startTime(Instant.now())
                .durationMinutes(120)
                .status(ContestStatus.UPCOMING)
                .build());

        return problemRepository.save(Problem.builder()
                .contest(contest)
                .title("A + B")
                .description("Add two integers")
                .timeLimit(1000)
                .memoryLimit(128)
                .difficulty(Difficulty.EASY)
                .build());
    }

    private User team(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .password("password")
                .role(Role.TEAM)
                .build());
    }
}
