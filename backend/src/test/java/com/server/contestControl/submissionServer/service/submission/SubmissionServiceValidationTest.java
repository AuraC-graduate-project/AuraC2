package com.server.contestControl.submissionServer.service.submission;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.service.ContestService;
import com.server.contestControl.contestServer.service.ProblemService;
import com.server.contestControl.submissionServer.dto.SubmissionRequest;
import com.server.contestControl.submissionServer.dto.SubmissionResponse;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.exceptions.InvalidSubmissionRequestException;
import com.server.contestControl.submissionServer.queue.submission.SubmissionProducer;
import com.server.contestControl.submissionServer.repository.SubmissionJudgeResultRepository;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.sse.SubmissionSsePublisher;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEvent;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceValidationTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionProducer submissionProducer;
    @Mock private JwtService jwtService;
    @Mock private ContestService contestService;
    @Mock private ProblemService problemService;
    @Mock private UserRepository userRepository;
    @Mock private SubmissionSsePublisher submissionSsePublisher;
    @Mock private SubmissionJudgeResultRepository judgeResultRepository;

    @InjectMocks
    private SubmissionService submissionService;

    @Mock private Authentication authentication;
    @Mock private SecurityContext securityContext;

    private static final Long ACTIVE_CONTEST_ID = 10L;
    private static final Long OTHER_CONTEST_ID = 99L;
    private static final Long PROBLEM_ID = 5L;

    @BeforeEach
    void setUp() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(authentication.getName()).thenReturn("team1");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ─── 1. Valid submission: problem belongs to active contest ─────────────────

    @Test
    void validSubmissionIsAcceptedWhenProblemBelongsToActiveContest() {
        Contest activeContest = contest(ACTIVE_CONTEST_ID);
        Problem problem = problem(PROBLEM_ID, activeContest);
        User user = user("team1");

        when(contestService.getContestEntity()).thenReturn(activeContest);
        when(problemService.getProblemEntity(PROBLEM_ID)).thenReturn(problem);
        when(userRepository.findByUsername("team1")).thenReturn(Optional.of(user));

        Submission savedSubmission = submission(1L, activeContest, problem, user, Verdict.PENDING);
        when(submissionRepository.save(any())).thenReturn(savedSubmission);
        when(submissionSsePublisher.buildEvent(any(), any())).thenReturn(dummyEvent());

        SubmissionRequest request = new SubmissionRequest(ACTIVE_CONTEST_ID, PROBLEM_ID, "java", "code");
        SubmissionResponse response = submissionService.submitCode(request);

        assertThat(response).isNotNull();
        verify(submissionProducer).sendSubmission(any());
    }

    // ─── 2. Problem belongs to different contest → rejected ────────────────────

    @Test
    void submissionRejectedWhenProblemBelongsToDifferentContest() {
        Contest activeContest = contest(ACTIVE_CONTEST_ID);
        Contest anotherContest = contest(OTHER_CONTEST_ID);
        Problem problem = problem(PROBLEM_ID, anotherContest); // belongs to other contest!
        User user = user("team1");

        when(contestService.getContestEntity()).thenReturn(activeContest);
        when(problemService.getProblemEntity(PROBLEM_ID)).thenReturn(problem);
        when(userRepository.findByUsername("team1")).thenReturn(Optional.of(user));

        SubmissionRequest request = new SubmissionRequest(null, PROBLEM_ID, "java", "code");

        assertThatThrownBy(() -> submissionService.submitCode(request))
                .isInstanceOf(InvalidSubmissionRequestException.class)
                .hasMessageContaining("Problem does not belong to the active contest");

        verify(submissionRepository, never()).save(any());
        verify(submissionProducer, never()).sendSubmission(any());
    }

    // ─── 3. request.contestId does not match active contest → rejected ─────────

    @Test
    void submissionRejectedWhenContestIdMismatch() {
        Contest activeContest = contest(ACTIVE_CONTEST_ID);
        Problem problem = problem(PROBLEM_ID, activeContest);
        User user = user("team1");

        when(contestService.getContestEntity()).thenReturn(activeContest);
        when(problemService.getProblemEntity(PROBLEM_ID)).thenReturn(problem);
        when(userRepository.findByUsername("team1")).thenReturn(Optional.of(user));

        // Client sends a different contestId than the active one
        SubmissionRequest request = new SubmissionRequest(OTHER_CONTEST_ID, PROBLEM_ID, "java", "code");

        assertThatThrownBy(() -> submissionService.submitCode(request))
                .isInstanceOf(InvalidSubmissionRequestException.class)
                .hasMessageContaining("Submission contestId does not match the active contest");

        verify(submissionRepository, never()).save(any());
        verify(submissionProducer, never()).sendSubmission(any());
    }

    // ─── 4. null contestId in request is tolerated (backward compat) ───────────

    @Test
    void submissionWithNullContestIdIsAccepted() {
        Contest activeContest = contest(ACTIVE_CONTEST_ID);
        Problem problem = problem(PROBLEM_ID, activeContest);
        User user = user("team1");

        when(contestService.getContestEntity()).thenReturn(activeContest);
        when(problemService.getProblemEntity(PROBLEM_ID)).thenReturn(problem);
        when(userRepository.findByUsername("team1")).thenReturn(Optional.of(user));

        Submission savedSubmission = submission(1L, activeContest, problem, user, Verdict.PENDING);
        when(submissionRepository.save(any())).thenReturn(savedSubmission);
        when(submissionSsePublisher.buildEvent(any(), any())).thenReturn(dummyEvent());

        SubmissionRequest request = new SubmissionRequest(null, PROBLEM_ID, "java", "code");
        SubmissionResponse response = submissionService.submitCode(request);

        assertThat(response).isNotNull();
        verify(submissionProducer).sendSubmission(any());
    }

    // ─── 5. RabbitMQ is published only for valid submissions ───────────────────

    @Test
    void rabbitMqIsNotPublishedForInvalidSubmission() {
        Contest activeContest = contest(ACTIVE_CONTEST_ID);
        Contest anotherContest = contest(OTHER_CONTEST_ID);
        Problem problem = problem(PROBLEM_ID, anotherContest);
        User user = user("team1");

        when(contestService.getContestEntity()).thenReturn(activeContest);
        when(problemService.getProblemEntity(PROBLEM_ID)).thenReturn(problem);
        when(userRepository.findByUsername("team1")).thenReturn(Optional.of(user));

        SubmissionRequest request = new SubmissionRequest(null, PROBLEM_ID, "java", "code");

        assertThatThrownBy(() -> submissionService.submitCode(request))
                .isInstanceOf(InvalidSubmissionRequestException.class);

        verify(submissionProducer, never()).sendSubmission(any());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    @Test
    void newSubmissionMessageIsPublishedOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        stubValidSubmission();

        SubmissionRequest request = new SubmissionRequest(ACTIVE_CONTEST_ID, PROBLEM_ID, "java", "code");
        submissionService.submitCode(request);

        verify(submissionProducer, never()).sendSubmission(any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(submissionProducer).sendSubmission(1L);
    }

    @Test
    void rollbackBeforeCommitDoesNotPublishSubmissionMessage() {
        TransactionSynchronizationManager.initSynchronization();
        stubValidSubmission();

        SubmissionRequest request = new SubmissionRequest(ACTIVE_CONTEST_ID, PROBLEM_ID, "java", "code");
        submissionService.submitCode(request);

        TransactionSynchronizationManager.clearSynchronization();

        verify(submissionProducer, never()).sendSubmission(any());
    }

    @Test
    void teamCanAccessOwnSubmissionById() {
        setRole("ROLE_TEAM");
        Contest contest = contest(ACTIVE_CONTEST_ID);
        Problem problem = problem(PROBLEM_ID, contest);
        User owner = user("team1");
        Submission submission = submission(7L, contest, problem, owner, Verdict.ACCEPTED);

        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(userRepository.findByUsername("team1")).thenReturn(Optional.of(owner));
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdOrderByTestCaseNumberAsc(7L, 0L))
                .thenReturn(List.of());

        SubmissionResponse response = submissionService.getSubmissionById(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.userId()).isEqualTo(owner.getId());
    }

    @Test
    void teamCannotAccessAnotherTeamsSubmissionById() {
        setRole("ROLE_TEAM");
        Contest contest = contest(ACTIVE_CONTEST_ID);
        Problem problem = problem(PROBLEM_ID, contest);
        User currentTeam = user("team1");
        User otherTeam = User.builder().id(2L).username("team2").build();
        Submission submission = submission(8L, contest, problem, otherTeam, Verdict.ACCEPTED);

        when(submissionRepository.findById(8L)).thenReturn(Optional.of(submission));
        when(userRepository.findByUsername("team1")).thenReturn(Optional.of(currentTeam));

        assertThatThrownBy(() -> submissionService.getSubmissionById(8L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Submission not found");

        verify(judgeResultRepository, never())
                .findBySubmission_IdAndJudgeRunIdOrderByTestCaseNumberAsc(any(), any());
    }

    @Test
    void adminCanAccessAnyTeamSubmissionById() {
        when(authentication.getName()).thenReturn("admin");
        setRole("ROLE_ADMIN");
        Contest contest = contest(ACTIVE_CONTEST_ID);
        Problem problem = problem(PROBLEM_ID, contest);
        User admin = User.builder().id(100L).username("admin").build();
        User otherTeam = User.builder().id(2L).username("team2").build();
        Submission submission = submission(9L, contest, problem, otherTeam, Verdict.ACCEPTED);

        when(submissionRepository.findById(9L)).thenReturn(Optional.of(submission));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(judgeResultRepository.findBySubmission_IdAndJudgeRunIdOrderByTestCaseNumberAsc(9L, 0L))
                .thenReturn(List.of());

        SubmissionResponse response = submissionService.getSubmissionById(9L);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.userId()).isEqualTo(otherTeam.getId());
    }

    private void stubValidSubmission() {
        Contest activeContest = contest(ACTIVE_CONTEST_ID);
        Problem problem = problem(PROBLEM_ID, activeContest);
        User user = user("team1");

        when(contestService.getContestEntity()).thenReturn(activeContest);
        when(problemService.getProblemEntity(PROBLEM_ID)).thenReturn(problem);
        when(userRepository.findByUsername("team1")).thenReturn(Optional.of(user));
        when(submissionRepository.save(any()))
                .thenReturn(submission(1L, activeContest, problem, user, Verdict.PENDING));
        when(submissionSsePublisher.buildEvent(any(), any())).thenReturn(dummyEvent());
    }

    private Contest contest(Long id) {
        return Contest.builder().id(id).build();
    }

    private Problem problem(Long id, Contest contest) {
        return Problem.builder().id(id).contest(contest).build();
    }

    private User user(String username) {
        return User.builder()
                .id(1L)
                .username(username)
                .build();
    }

    private Submission submission(Long id, Contest contest, Problem problem, User user, Verdict verdict) {
        return Submission.builder()
                .id(id)
                .contest(contest)
                .problem(problem)
                .user(user)
                .verdict(verdict)
                .code("code")
                .language("java")
                .judgeRunId(0L)
                .build();
    }

    private SubmissionStreamEvent dummyEvent() {
        return new SubmissionStreamEvent(
                SubmissionStreamEventType.CREATED,
                1L, 10L, 5L, 1L, "team1",
                Verdict.PENDING, 0L, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private void setRole(String authority) {
        doReturn(List.of(new SimpleGrantedAuthority(authority))).when(authentication).getAuthorities();
    }
}
