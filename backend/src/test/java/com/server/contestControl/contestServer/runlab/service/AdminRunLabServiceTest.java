package com.server.contestControl.contestServer.runlab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.moderation.entity.ContestModerationAuditLog;
import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;
import com.server.contestControl.contestServer.moderation.repository.ContestModerationAuditLogRepository;
import com.server.contestControl.contestServer.oracle.service.OracleJudge0ExecutionService;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.runlab.dto.AdminRunLabRequest;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.language.SupportedLanguageService;
import com.server.contestControl.submissionServer.run.exception.RunRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRunLabServiceTest {

    @Mock private ContestRepository contestRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private UserRepository userRepository;
    @Mock private SupportedLanguageService supportedLanguageService;
    @Mock private OracleJudge0ExecutionService judge0ExecutionService;
    @Mock private ContestModerationAuditLogRepository auditLogRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AdminRunLabService service;

    @Test
    void adminCanRunCustomInputWithoutCreatingScoringSubmissionAndAuditDoesNotStoreSource() {
        Contest contest = Contest.builder().id(1L).title("Contest").build();
        Problem problem = Problem.builder()
                .id(2L)
                .contest(contest)
                .title("A+B")
                .timeLimit(1000)
                .memoryLimit(128)
                .build();
        User admin = User.builder().id(9L).username("admin").role(Role.ADMIN).build();

        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(problemRepository.findByIdWithContest(2L)).thenReturn(Optional.of(problem));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(judge0ExecutionService.run(eq("print(4)\n"), eq(71), eq("3 1\n"), eq(1.0), eq(131072)))
                .thenReturn(new OracleJudge0ExecutionService.SandboxExecutionResult(
                        Verdict.ACCEPTED,
                        3,
                        "Accepted",
                        "4\n",
                        "",
                        8,
                        2048,
                        null
                ));

        var response = service.run(
                new AdminRunLabRequest(1L, 2L, 71, "print(4)\r\n", "3 1\r\n"),
                "admin"
        );

        assertThat(response.scoring()).isFalse();
        assertThat(response.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(response.stdout()).isEqualTo("4\n");

        ArgumentCaptor<ContestModerationAuditLog> auditCaptor =
                ArgumentCaptor.forClass(ContestModerationAuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        ContestModerationAuditLog audit = auditCaptor.getValue();
        assertThat(audit.getActionType()).isEqualTo(ModerationActionType.ADMIN_RUN_LAB_EXECUTION);
        assertThat(audit.getTeam()).isNull();
        assertThat(audit.getProblem()).isEqualTo(problem);
        assertThat(audit.getLanguageId()).isEqualTo(71);
        assertThat(audit.getExecutionMode()).isEqualTo("CUSTOM_INPUT");
        assertThat(audit.getSourceHash()).hasSize(64);
        assertThat(audit.getNewValueJson()).contains("\"executionMode\":\"CUSTOM_INPUT\"");
        assertThat(audit.getNewValueJson()).doesNotContain("print(4)");
        assertThat(audit.getOldValueJson()).isEqualTo("{}");
    }

    @Test
    void emptySourceIsRejectedBeforeJudgeDispatch() {
        assertThatThrownBy(() -> service.run(
                new AdminRunLabRequest(1L, 2L, 71, " ", ""),
                "admin"
        ))
                .isInstanceOf(RunRequestException.class)
                .hasMessageContaining("Source code cannot be empty");
    }
}
