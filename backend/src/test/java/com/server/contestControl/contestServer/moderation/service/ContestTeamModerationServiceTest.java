package com.server.contestControl.contestServer.moderation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.moderation.dto.ModerationActionRequest;
import com.server.contestControl.contestServer.moderation.entity.ContestModerationAuditLog;
import com.server.contestControl.contestServer.moderation.entity.ContestTeamModeration;
import com.server.contestControl.contestServer.moderation.enums.ContestTeamStatus;
import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;
import com.server.contestControl.contestServer.moderation.exception.ContestTeamModerationException;
import com.server.contestControl.contestServer.moderation.repository.ContestModerationAuditLogRepository;
import com.server.contestControl.contestServer.moderation.repository.ContestTeamModerationRepository;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.submissionServer.exceptions.InvalidSubmissionRequestException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContestTeamModerationServiceTest {

    @Mock private ContestRepository contestRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private UserRepository userRepository;
    @Mock private ContestTeamModerationRepository moderationRepository;
    @Mock private ContestModerationAuditLogRepository auditLogRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ContestTeamModerationService service;

    @Test
    void hideFromScoreboardRequiresReason() {
        assertThatThrownBy(() -> service.applyAction(
                10L,
                1L,
                new ModerationActionRequest(ModerationActionType.HIDE_FROM_SCOREBOARD, " "),
                "admin"
        ))
                .isInstanceOf(ContestTeamModerationException.class)
                .hasMessageContaining("Reason is required");
    }

    @Test
    void disqualifyTeamSetsContestScopedStateAndCreatesAuditLog() {
        Contest contest = contest();
        User team = team();
        User admin = admin();
        when(contestRepository.findById(10L)).thenReturn(Optional.of(contest));
        when(userRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(moderationRepository.findByContest_IdAndTeam_Id(10L, 1L)).thenReturn(Optional.empty());
        when(moderationRepository.save(any())).thenAnswer(invocation -> {
            ContestTeamModeration moderation = invocation.getArgument(0);
            moderation.setId(55L);
            return moderation;
        });

        var response = service.applyAction(
                10L,
                1L,
                new ModerationActionRequest(ModerationActionType.DISQUALIFY_TEAM, "Rule violation"),
                "admin"
        );

        assertThat(response.status()).isEqualTo(ContestTeamStatus.DISQUALIFIED);
        assertThat(response.hiddenFromScoreboard()).isTrue();
        assertThat(response.submitEnabled()).isFalse();
        assertThat(response.runEnabled()).isFalse();

        ArgumentCaptor<ContestModerationAuditLog> auditCaptor =
                ArgumentCaptor.forClass(ContestModerationAuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        ContestModerationAuditLog audit = auditCaptor.getValue();
        assertThat(audit.getActionType()).isEqualTo(ModerationActionType.DISQUALIFY_TEAM);
        assertThat(audit.getReason()).isEqualTo("Rule violation");
        assertThat(audit.getOldValueJson()).contains("\"status\":\"ACTIVE\"");
        assertThat(audit.getNewValueJson()).contains("\"status\":\"DISQUALIFIED\"");
        assertThat(audit.getNewValueJson()).contains("\"runEnabled\":false");
    }

    @Test
    void restoreTeamReenablesExpectedModerationState() {
        Contest contest = contest();
        User team = team();
        User admin = admin();
        ContestTeamModeration moderation = ContestTeamModeration.builder()
                .contest(contest)
                .team(team)
                .status(ContestTeamStatus.DISQUALIFIED)
                .hiddenFromScoreboard(true)
                .submitEnabled(false)
                .runEnabled(false)
                .reason("old")
                .build();

        when(contestRepository.findById(10L)).thenReturn(Optional.of(contest));
        when(userRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(moderationRepository.findByContest_IdAndTeam_Id(10L, 1L)).thenReturn(Optional.of(moderation));
        when(moderationRepository.save(moderation)).thenReturn(moderation);

        var response = service.applyAction(
                10L,
                1L,
                new ModerationActionRequest(ModerationActionType.RESTORE_TEAM, "Appeal accepted"),
                "admin"
        );

        assertThat(response.status()).isEqualTo(ContestTeamStatus.ACTIVE);
        assertThat(response.hiddenFromScoreboard()).isFalse();
        assertThat(response.submitEnabled()).isTrue();
        assertThat(response.runEnabled()).isTrue();
    }

    @Test
    void submitAndRunGuardsRejectDisabledOrDisqualifiedTeams() {
        Contest contest = contest();
        User team = team();
        ContestTeamModeration submitDisabled = ContestTeamModeration.builder()
                .contest(contest)
                .team(team)
                .status(ContestTeamStatus.ACTIVE)
                .submitEnabled(false)
                .runEnabled(true)
                .build();
        when(moderationRepository.findByContest_IdAndTeam_Id(10L, 1L))
                .thenReturn(Optional.of(submitDisabled));

        assertThatThrownBy(() -> service.assertSubmitAllowed(contest, team))
                .isInstanceOf(InvalidSubmissionRequestException.class)
                .hasMessageContaining("Submissions are disabled");

        ContestTeamModeration disqualified = ContestTeamModeration.builder()
                .contest(contest)
                .team(team)
                .status(ContestTeamStatus.DISQUALIFIED)
                .submitEnabled(true)
                .runEnabled(true)
                .build();
        when(moderationRepository.findByContest_IdAndTeam_Id(10L, 1L))
                .thenReturn(Optional.of(disqualified));

        assertThatThrownBy(() -> service.assertRunAllowed(contest, team))
                .isInstanceOf(RunRequestException.class)
                .hasMessageContaining("disqualified");
    }

    private Contest contest() {
        return Contest.builder().id(10L).title("Finals").build();
    }

    private User team() {
        return User.builder().id(1L).username("team1").role(Role.TEAM).build();
    }

    private User admin() {
        return User.builder().id(99L).username("admin").role(Role.ADMIN).build();
    }
}
