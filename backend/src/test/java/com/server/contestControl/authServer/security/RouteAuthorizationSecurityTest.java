package com.server.contestControl.authServer.security;

import com.server.contestControl.authServer.config.SecurityConfiguration;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.filter.JwtAuthFilter;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import com.server.contestControl.authServer.service.user.UserService;
import com.server.contestControl.contestServer.controller.AdminController;
import com.server.contestControl.contestServer.controller.ClarificationController;
import com.server.contestControl.contestServer.controller.ContestController;
import com.server.contestControl.contestServer.controller.ProblemController;
import com.server.contestControl.contestServer.controller.TestCaseController;
import com.server.contestControl.contestServer.dto.clarification.ClarificationResponse;
import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.dto.problem.ProblemResponse;
import com.server.contestControl.contestServer.dto.testcase.PublicTestCaseResponse;
import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;
import com.server.contestControl.contestServer.enums.ClarificationStatus;
import com.server.contestControl.contestServer.exceptions.ProblemDeletionConflictException;
import com.server.contestControl.contestServer.moderation.service.ContestTeamModerationService;
import com.server.contestControl.contestServer.moderation.controller.AdminTeamModerationController;
import com.server.contestControl.contestServer.moderation.service.ContestModerationCsvExporter;
import com.server.contestControl.contestServer.oracle.controller.OracleAdminController;
import com.server.contestControl.contestServer.oracle.dto.CounterexampleResponse;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestBatchResponse;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestPromotionResponse;
import com.server.contestControl.contestServer.oracle.dto.OracleProgramResponse;
import com.server.contestControl.contestServer.oracle.service.OracleService;
import com.server.contestControl.contestServer.runlab.controller.AdminRunLabController;
import com.server.contestControl.contestServer.runlab.dto.AdminRunLabResponse;
import com.server.contestControl.contestServer.runlab.service.AdminRunLabService;
import com.server.contestControl.contestServer.scoreboard.controller.AdminScoreboardController;
import com.server.contestControl.contestServer.scoreboard.controller.ScoreboardController;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRevealResponse;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.service.ScoreboardRevealService;
import com.server.contestControl.contestServer.scoreboard.service.ScoreboardService;
import com.server.contestControl.contestServer.scoreboard.sse.ScoreboardSseAdapter;
import com.server.contestControl.contestServer.service.ClarificationService;
import com.server.contestControl.contestServer.service.ContestService;
import com.server.contestControl.contestServer.service.ProblemService;
import com.server.contestControl.contestServer.service.TestCaseService;
import com.server.contestControl.submissionServer.controller.RejudgeController;
import com.server.contestControl.submissionServer.controller.SubmissionController;
import com.server.contestControl.submissionServer.dto.RejudgeResponse;
import com.server.contestControl.submissionServer.dto.SubmissionResponse;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.service.rejudge.RejudgeService;
import com.server.contestControl.submissionServer.service.submission.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(controllers = {
        AdminController.class,
        ContestController.class,
        ProblemController.class,
        TestCaseController.class,
        SubmissionController.class,
        RejudgeController.class,
        ScoreboardController.class,
        AdminScoreboardController.class,
        ClarificationController.class,
        OracleAdminController.class,
        AdminTeamModerationController.class,
        AdminRunLabController.class
})
@Import({SecurityConfiguration.class, JwtAuthFilter.class})
class RouteAuthorizationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private UserService userService;
    @MockBean private UserRepository userRepository;
    @MockBean private ContestService contestService;
    @MockBean private ProblemService problemService;
    @MockBean private TestCaseService testCaseService;
    @MockBean private ContestTeamModerationService moderationService;
    @MockBean private ContestModerationCsvExporter moderationCsvExporter;
    @MockBean private SubmissionService submissionService;
    @MockBean private RejudgeService rejudgeService;
    @MockBean private ScoreboardService scoreboardService;
    @MockBean private ScoreboardRevealService revealService;
    @MockBean private ScoreboardSseAdapter scoreboardSseAdapter;
    @MockBean private ClarificationService clarificationService;
    @MockBean private OracleService oracleService;
    @MockBean private AdminRunLabService adminRunLabService;

    @BeforeEach
    void setUp() {
        when(contestService.createContest(any())).thenReturn(contestResponse());
        when(contestService.updateContestDetails(eq(1L), any())).thenReturn(contestResponse());
        when(problemService.createProblem(any())).thenReturn(problemResponse());
        when(problemService.updateProblem(eq(1L), any())).thenReturn(problemResponse());
        when(problemService.getProblem(eq(1L), anyBoolean())).thenReturn(problemResponse());
        when(problemService.getAllProblems(eq(1L), anyBoolean())).thenReturn(List.of(problemResponse()));
        when(testCaseService.addTestCase(eq(1L), any())).thenReturn(testCaseResponse());
        when(testCaseService.updateTestCase(eq(1L), any())).thenReturn(testCaseResponse());
        when(testCaseService.getAdminTestCases(1L)).thenReturn(List.of(testCaseResponse()));
        when(testCaseService.getPublicTestCases(1L)).thenReturn(List.of(publicTestCaseResponse()));
        when(rejudgeService.rejudgeProblem(1L)).thenReturn(rejudgeResponse());
        when(submissionService.getAllSubmissionsForUser(any())).thenReturn(List.of(submissionResponse()));
        when(scoreboardService.getPublicSnapshot(1L)).thenReturn(new ScoreboardSnapshot(null, List.of()));
        when(scoreboardService.getAdminSnapshot(1L)).thenReturn(new ScoreboardSnapshot(null, List.of()));
        when(revealService.start(1L)).thenReturn(revealResponse());
        when(clarificationService.submitClarification(any(), any())).thenReturn(clarificationResponse());
        when(clarificationService.replyClarification(eq(1L), any(), any())).thenReturn(clarificationResponse());
        when(clarificationService.fetchPublicClarifications(1L)).thenReturn(List.of(clarificationResponse()));
        when(userService.getAllUsers()).thenReturn(List.of());
        when(oracleService.configureReferenceSolution(eq(1L), any(), eq("admin"))).thenReturn(oracleProgramResponse());
        when(oracleService.configureInputGenerator(eq(1L), any(), eq("admin"))).thenReturn(oracleProgramResponse());
        when(oracleService.configureInputValidator(eq(1L), any(), eq("admin"))).thenReturn(oracleProgramResponse());
        when(oracleService.createGeneratedTestBatch(eq(1L), any(), eq("admin"))).thenReturn(generatedBatchResponse());
        when(oracleService.counterexamples(1L)).thenReturn(List.of(counterexampleResponse()));
        when(oracleService.promoteGeneratedTestCase(1L)).thenReturn(generatedPromotionResponse());
        when(oracleService.promoteGeneratedTestCases(any())).thenReturn(generatedPromotionResponse());
        when(oracleService.promoteAllValidGeneratedTestCases(1L)).thenReturn(generatedPromotionResponse());
        when(oracleService.promoteCounterexample(1L)).thenReturn(testCaseResponse());
        when(moderationService.listForContest(1L)).thenReturn(List.of());
        when(moderationService.auditLogs(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(moderationCsvExporter.export(any())).thenReturn("timestamp,contest,team\n".getBytes());
        when(adminRunLabService.run(any(), eq("admin"))).thenReturn(adminRunLabResponse());
    }

    @Test
    void contestMutationsAreAdminOnly() throws Exception {
        mockMvc.perform(post("/api/contest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contestRequestJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/contest/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contestUpdateRequestJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/contest/1/start"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/contest/1/pause"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/contest/1/resume"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/contest/1/end"))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(post("/api/contest")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contestRequestJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/contest/1")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contestUpdateRequestJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/contest/1/start")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/contest/1/pause")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/contest/1/resume")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/contest/1/end")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(post("/api/contest")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contestRequestJson()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/contest/1")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contestUpdateRequestJson()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/contest/1/start")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/contest/1/pause")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/contest/1/resume")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/contest/1/end")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void contestDeleteRouteRequiresAdminBeforeMvcHandlerResolution() throws Exception {
        mockMvc.perform(delete("/api/contest/1"))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(delete("/api/contest/1")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void problemMutationsAreAdminOnly() throws Exception {
        mockMvc.perform(post("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemRequestJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/problems/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemUpdateRequestJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/problems/1"))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(post("/api/problems")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemRequestJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/problems/1")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemUpdateRequestJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/problems/1")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(post("/api/problems")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemRequestJson()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/problems/1")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemUpdateRequestJson()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/problems/1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void teamProblemResponsesDoNotExposeValidatorSource() throws Exception {
        mockBearerUser("team-token", "team", Role.TEAM);

        mockMvc.perform(get("/api/problems/1")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationMode").value("CUSTOM_VALIDATOR"))
                .andExpect(jsonPath("$.validatorSourceHash").value("a".repeat(64)))
                .andExpect(jsonPath("$.validatorSource").doesNotExist());

        mockMvc.perform(get("/api/problems/contest/1")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].validatorSource").doesNotExist());
    }

    @Test
    void problemDeleteConflictReturnsCleanConflictForAdmin() throws Exception {
        doThrow(new ProblemDeletionConflictException(1L, "submissions or judging history exist"))
                .when(problemService).deleteProblem(1L);

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(delete("/api/problems/1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ProblemDeletionConflictException"))
                .andExpect(jsonPath("$.message").value("Problem with ID '1' cannot be deleted: submissions or judging history exist"));
    }

    @Test
    void testCaseMutationsAreAdminOnly() throws Exception {
        mockMvc.perform(post("/api/testcases/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testCaseRequestJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/testcases/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testCaseRequestJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/testcases/1"))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(post("/api/testcases/1")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testCaseRequestJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/testcases/1")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testCaseRequestJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/testcases/1")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(post("/api/testcases/1")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testCaseRequestJson()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/testcases/1")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testCaseRequestJson()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/testcases/1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testCaseListingSeparatesAdminHiddenDataFromTeamSamples() throws Exception {
        mockMvc.perform(get("/api/testcases/problem/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/testcases/public/problem/1"))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(get("/api/testcases/problem/1")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/testcases/99")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/testcases/public/problem/1")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inputData").value("1 2"))
                .andExpect(jsonPath("$[0].expectedOutput").value("3"))
                .andExpect(jsonPath("$[0].isPublic").value(true));

        verify(testCaseService).getPublicTestCases(1L);

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(get("/api/testcases/problem/1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expectedOutput").value("3"));

        verify(testCaseService).getAdminTestCases(1L);
    }

    @Test
    void rejudgeEndpointsAreAdminOnly() throws Exception {
        mockMvc.perform(post("/api/admin/rejudge/problem/1"))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(post("/api/admin/rejudge/problem/1")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(post("/api/admin/rejudge/problem/1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void oracleGeneratedDataAndCounterexamplesAreAdminOnly() throws Exception {
        mockMvc.perform(post("/api/admin/oracle/problems/1/reference-solution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oracleProgramRequestJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/oracle/problems/1/generated-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generatedBatchRequestJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/oracle/problems/1/counterexamples"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/oracle/generated-test-cases/1/promote"))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(post("/api/admin/oracle/problems/1/reference-solution")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oracleProgramRequestJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/oracle/problems/1/counterexamples")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/oracle/generated-batches/1/promote-valid")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(post("/api/admin/oracle/problems/1/reference-solution")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oracleProgramRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceHash").value("a".repeat(64)));
        mockMvc.perform(post("/api/admin/oracle/problems/1/generated-batches")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generatedBatchRequestJson()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/oracle/problems/1/counterexamples")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].generatedInput").value("4\n"));
        mockMvc.perform(post("/api/admin/oracle/generated-test-cases/1/promote")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/oracle/generated-test-cases/promote-selected")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"generatedTestCaseIds":[1]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/oracle/generated-batches/1/promote-valid")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void submissionEndpointsRequireAuthenticationAndTeamsCanReadOwnHistory() throws Exception {
        mockMvc.perform(get("/api/submissions/my/all"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionRequestJson()))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(get("/api/submissions/my/all")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isOk());

        verify(submissionService).getAllSubmissionsForUser(any());
    }

    @Test
    void adminUserManagementIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void teamModerationRoutesAreAdminOnly() throws Exception {
        mockMvc.perform(get("/api/admin/team-moderation/contests/1/teams"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/team-moderation/logs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/team-moderation/logs.csv"))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(get("/api/admin/team-moderation/contests/1/teams")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/team-moderation/logs")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/team-moderation/logs.csv")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(get("/api/admin/team-moderation/contests/1/teams")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/team-moderation/logs")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/team-moderation/logs.csv")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void runLabRouteIsAdminOnly() throws Exception {
        mockMvc.perform(post("/api/admin/run-lab/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runLabRequestJson()))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(post("/api/admin/run-lab/run")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runLabRequestJson()))
                .andExpect(status().isForbidden());

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(post("/api/admin/run-lab/run")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runLabRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void scoreboardPublicAndAdminRoutesKeepSeparateAuthorization() throws Exception {
        mockMvc.perform(get("/api/scoreboard/contests/1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/scoreboard/contests/1/reveal/start"))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(post("/api/admin/scoreboard/contests/1/reveal/start")
                        .header("Authorization", "Bearer team-token"))
                .andExpect(status().isForbidden());

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(get("/api/admin/scoreboard/contests/1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/scoreboard/contests/1/reveal/start")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void clarificationRoutesMatchPublicTeamAndAdminPolicy() throws Exception {
        mockMvc.perform(get("/api/clarifications/public/1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/clarifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clarificationRequestJson()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/clarifications/admin/1/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyRequestJson()))
                .andExpect(status().isUnauthorized());

        mockBearerUser("team-token", "team", Role.TEAM);
        mockMvc.perform(post("/api/clarifications")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clarificationRequestJson()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/clarifications/admin/1/reply")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyRequestJson()))
                .andExpect(status().isForbidden());

        mockBearerUser("admin-token", "admin", Role.ADMIN);
        mockMvc.perform(put("/api/clarifications/admin/1/reply")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyRequestJson()))
                .andExpect(status().isOk());
    }

    private void mockBearerUser(String token, String username, Role role) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password("password")
                .authorities("ROLE_" + role.name())
                .build();

        User user = User.builder()
                .id(role == Role.ADMIN ? 1L : 2L)
                .username(username)
                .role(role)
                .build();

        when(jwtService.extractUsername(token, TokenType.ACCESS)).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails, TokenType.ACCESS)).thenReturn(true);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
    }

    private ContestResponse contestResponse() {
        return ContestResponse.builder().id(1L).title("Contest").status("UPCOMING").build();
    }

    private ProblemResponse problemResponse() {
        return ProblemResponse.builder()
                .id(1L)
                .contestId(1L)
                .title("Problem A")
                .description("Solve it")
                .timeLimit(1000)
                .memoryLimit(128)
                .difficulty("EASY")
                .validationMode("CUSTOM_VALIDATOR")
                .validatorEnabled(true)
                .validatorLanguageId(71)
                .validatorSourceHash("a".repeat(64))
                .build();
    }

    private TestCaseResponse testCaseResponse() {
        return new TestCaseResponse(1L, "1 2", "3", true);
    }

    private PublicTestCaseResponse publicTestCaseResponse() {
        return new PublicTestCaseResponse(1L, 1L, "1 2", "3", true);
    }

    private RejudgeResponse rejudgeResponse() {
        return new RejudgeResponse("PROBLEM", 1L, 1, 1, 1, 0, List.of(1L), List.of(), List.of());
    }

    private SubmissionResponse submissionResponse() {
        return new SubmissionResponse(1L, 1L, 1L, "Problem A", 2L, "java", "code", null, Instant.now(), null, null, 0L, List.of());
    }

    private ScoreboardRevealResponse revealResponse() {
        return new ScoreboardRevealResponse(1L, RevealStatus.IN_PROGRESS, 1, 0, null, null, Instant.now(), Instant.now(), null);
    }

    private ClarificationResponse clarificationResponse() {
        return new ClarificationResponse(1L, 1L, null, null, "Question?", null, null, ClarificationStatus.PENDING, null, LocalDateTime.now(), null, "team");
    }

    private OracleProgramResponse oracleProgramResponse() {
        return new OracleProgramResponse(1L, 1L, 54, "a".repeat(64), true, null, Instant.now(), Instant.now());
    }

    private GeneratedTestBatchResponse generatedBatchResponse() {
        return new GeneratedTestBatchResponse(
                1L,
                1L,
                123L,
                "a".repeat(64),
                "b".repeat(64),
                "COMPLETED",
                1,
                1,
                0,
                1,
                null,
                Instant.now(),
                Instant.now(),
                List.of()
        );
    }

    private GeneratedTestPromotionResponse generatedPromotionResponse() {
        return new GeneratedTestPromotionResponse(
                1,
                1,
                0,
                0,
                0,
                List.of(testCaseResponse()),
                List.of(),
                "1 promoted."
        );
    }

    private CounterexampleResponse counterexampleResponse() {
        return new CounterexampleResponse(
                1L,
                1L,
                1L,
                1L,
                0L,
                "4\n",
                "YES\n",
                "NO\n",
                null,
                "NORMALIZED_TEXT",
                "BUILTIN_COMPARE_POLICY",
                "Output mismatch",
                false,
                null,
                Instant.now()
        );
    }

    private AdminRunLabResponse adminRunLabResponse() {
        return new AdminRunLabResponse(false, Verdict.ACCEPTED, 3, "Accepted", "4\n", null, null, 12, 1024);
    }

    private String contestRequestJson() {
        return """
                {
                  "title": "Contest",
                  "description": "Practice",
                  "startTime": "2030-01-01T10:00:00Z",
                  "durationMinutes": 120,
                  "scoreboardFreezeMinutes": 30,
                  "penaltyMinutes": 20
                }
                """;
    }

    private String contestUpdateRequestJson() {
        return contestRequestJson();
    }

    private String problemRequestJson() {
        return """
                {
                  "contestId": 1,
                  "title": "Problem A",
                  "description": "Solve it",
                  "timeLimit": 1000,
                  "memoryLimit": 128,
                  "difficulty": "EASY"
                }
                """;
    }

    private String problemUpdateRequestJson() {
        return """
                {
                  "title": "Problem A",
                  "description": "Solve it better",
                  "timeLimit": 1000,
                  "memoryLimit": 128,
                  "difficulty": "EASY"
                }
                """;
    }

    private String testCaseRequestJson() {
        return """
                {
                  "inputData": "1 2",
                  "expectedOutput": "3",
                  "isPublic": true
                }
                """;
    }

    private String submissionRequestJson() {
        return """
                {
                  "contestId": 1,
                  "problemId": 1,
                  "language": "java",
                  "code": "class Main {}"
                }
                """;
    }

    private String runLabRequestJson() {
        return """
                {
                  "contestId": 1,
                  "problemId": 1,
                  "languageId": 54,
                  "sourceCode": "print(4)",
                  "customInput": "3 1"
                }
                """;
    }

    private String clarificationRequestJson() {
        return """
                {
                  "contestId": 1,
                  "problemId": null,
                  "question": "Can you clarify the sample?"
                }
                """;
    }

    private String replyRequestJson() {
        return """
                {
                  "standardReply": "NO_COMMENT",
                  "reply": null,
                  "replyType": "PUBLIC"
                }
                """;
    }

    private String oracleProgramRequestJson() {
        return """
                {
                  "languageId": 54,
                  "source": "int main(){}",
                  "active": true
                }
                """;
    }

    private String generatedBatchRequestJson() {
        return """
                {
                  "testCount": 1,
                  "seed": 123,
                  "submissionId": 1
                }
                """;
    }
}
