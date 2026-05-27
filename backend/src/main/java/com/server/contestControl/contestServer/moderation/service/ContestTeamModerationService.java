package com.server.contestControl.contestServer.moderation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.exception.api.UserNotFoundException;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.exception.ContestNotFoundException;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.moderation.dto.ContestTeamModerationResponse;
import com.server.contestControl.contestServer.moderation.dto.ModerationActionRequest;
import com.server.contestControl.contestServer.moderation.dto.ModerationAuditLogResponse;
import com.server.contestControl.contestServer.moderation.dto.TeamContestAccessResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContestTeamModerationService {

    private static final Set<ModerationActionType> REASON_REQUIRED_ACTIONS = EnumSet.of(
            ModerationActionType.HIDE_FROM_SCOREBOARD,
            ModerationActionType.DISQUALIFY_TEAM,
            ModerationActionType.DISABLE_SUBMIT,
            ModerationActionType.DISABLE_RUN
    );

    private final ContestRepository contestRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final ContestTeamModerationRepository moderationRepository;
    private final ContestModerationAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public List<ContestTeamModerationResponse> listForContest(Long contestId) {
        Contest contest = contest(contestId);
        List<User> teams = userRepository.findAllByRoleOrderByUsernameAscIdAsc(Role.TEAM);
        if (teams.isEmpty()) {
            return List.of();
        }
        Map<Long, ContestTeamModeration> moderationByTeamId = moderationRepository
                .findByContestIdAndTeamIdIn(contestId, teams.stream().map(User::getId).toList())
                .stream()
                .collect(Collectors.toMap(item -> item.getTeam().getId(), Function.identity()));

        return teams.stream()
                .map(team -> {
                    ContestTeamModeration moderation = moderationByTeamId.get(team.getId());
                    return moderation == null
                            ? ContestTeamModerationResponse.activeDefault(contest, team)
                            : ContestTeamModerationResponse.from(moderation);
                })
                .toList();
    }

    @Transactional
    public ContestTeamModerationResponse applyAction(
            Long contestId,
            Long teamId,
            ModerationActionRequest request,
            String adminUsername
    ) {
        if (request == null || request.actionType() == null) {
            throw new ContestTeamModerationException("Moderation action is required.");
        }
        if (request.actionType() == ModerationActionType.ADMIN_RUN_LAB_EXECUTION) {
            throw new ContestTeamModerationException("Run Lab audit entries cannot be applied as team moderation actions.");
        }

        String reason = normalizeReason(request.reason());
        if (REASON_REQUIRED_ACTIONS.contains(request.actionType()) && reason == null) {
            throw new ContestTeamModerationException("Reason is required for this moderation action.");
        }

        Contest contest = contest(contestId);
        User team = team(teamId);
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException(adminUsername));

        ContestTeamModeration moderation = moderationRepository.findByContest_IdAndTeam_Id(contestId, teamId)
                .orElseGet(() -> ContestTeamModeration.builder()
                        .contest(contest)
                        .team(team)
                        .status(ContestTeamStatus.ACTIVE)
                        .hiddenFromScoreboard(false)
                        .submitEnabled(true)
                        .runEnabled(true)
                        .build());

        String oldValue = snapshotJson(moderation);
        mutate(moderation, request.actionType(), reason);
        moderation.setUpdatedByAdmin(admin);
        moderation.setReason(reason);
        ContestTeamModeration saved = moderationRepository.save(moderation);
        String newValue = snapshotJson(saved);

        auditLogRepository.save(ContestModerationAuditLog.builder()
                .contest(contest)
                .team(team)
                .admin(admin)
                .actionType(request.actionType())
                .reason(reason)
                .oldValueJson(oldValue)
                .newValueJson(newValue)
                .build());

        return ContestTeamModerationResponse.from(saved);
    }

    public List<ModerationAuditLogResponse> auditLogs(
            Long contestId,
            Long teamId,
            Long adminId,
            ModerationActionType actionType,
            LocalDateTime fromTime,
            LocalDateTime toTime
    ) {
        return auditLogRepository.search(contestId, teamId, adminId, actionType, fromTime, toTime)
                .stream()
                .map(ModerationAuditLogResponse::from)
                .toList();
    }

    public Set<Long> scoreboardSuppressedTeamIds(Long contestId) {
        if (contestId == null) {
            return Set.of();
        }
        return moderationRepository.findScoreboardSuppressedTeamIds(
                contestId,
                ContestTeamStatus.DISQUALIFIED
        );
    }

    public TeamContestAccessResponse teamAccess(Long contestId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        Contest contest = contest(contestId);
        ContestTeamModeration moderation = moderationRepository
                .findByContest_IdAndTeam_Id(contest.getId(), user.getId())
                .orElse(null);
        ContestTeamStatus status = statusOf(moderation);
        boolean disqualified = status == ContestTeamStatus.DISQUALIFIED;
        boolean submitEnabled = submitEnabledOf(moderation);
        boolean runEnabled = runEnabledOf(moderation);
        return new TeamContestAccessResponse(
                contest.getId(),
                status,
                hiddenFromScoreboardOf(moderation),
                submitEnabled,
                runEnabled,
                !disqualified,
                disqualified ? "You are disqualified from this contest." : null
        );
    }

    public void assertWorkspaceVisible(Long contestId, String username) {
        TeamContestAccessResponse access = teamAccess(contestId, username);
        if (!access.workspaceVisible()) {
            throw new ContestTeamModerationException(
                    "Your team is disqualified from this contest.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    public void assertProblemWorkspaceVisible(Long problemId, String username) {
        Problem problem = problemRepository.findByIdWithContest(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));
        assertWorkspaceVisible(problem.getContest().getId(), username);
    }

    public void assertSubmitAllowed(Contest contest, User user) {
        if (contest == null || user == null || user.getRole() != Role.TEAM) {
            return;
        }
        ContestTeamModeration moderation = moderationRepository
                .findByContest_IdAndTeam_Id(contest.getId(), user.getId())
                .orElse(null);
        if (statusOf(moderation) == ContestTeamStatus.DISQUALIFIED) {
            throw new InvalidSubmissionRequestException("Your team is disqualified from this contest.");
        }
        if (!submitEnabledOf(moderation)) {
            throw new InvalidSubmissionRequestException("Submissions are disabled for your team.");
        }
    }

    public void assertRunAllowed(Contest contest, User user) {
        if (contest == null || user == null || user.getRole() != Role.TEAM) {
            return;
        }
        ContestTeamModeration moderation = moderationRepository
                .findByContest_IdAndTeam_Id(contest.getId(), user.getId())
                .orElse(null);
        if (statusOf(moderation) == ContestTeamStatus.DISQUALIFIED) {
            throw new RunRequestException("Your team is disqualified from this contest.");
        }
        if (!runEnabledOf(moderation)) {
            throw new RunRequestException("Run is disabled for your team.");
        }
    }

    public boolean isTeamSuppressedFromScoreboard(Long contestId, Long teamId) {
        if (contestId == null || teamId == null) {
            return false;
        }
        ContestTeamModeration moderation = moderationRepository
                .findByContest_IdAndTeam_Id(contestId, teamId)
                .orElse(null);
        return statusOf(moderation) == ContestTeamStatus.DISQUALIFIED
                || hiddenFromScoreboardOf(moderation);
    }

    private void mutate(ContestTeamModeration moderation, ModerationActionType actionType, String reason) {
        switch (actionType) {
            case HIDE_FROM_SCOREBOARD -> moderation.setHiddenFromScoreboard(true);
            case SHOW_ON_SCOREBOARD -> moderation.setHiddenFromScoreboard(false);
            case DISQUALIFY_TEAM -> {
                moderation.setStatus(ContestTeamStatus.DISQUALIFIED);
                moderation.setHiddenFromScoreboard(true);
                moderation.setSubmitEnabled(false);
                moderation.setRunEnabled(false);
            }
            case RESTORE_TEAM -> {
                moderation.setStatus(ContestTeamStatus.ACTIVE);
                moderation.setHiddenFromScoreboard(false);
                moderation.setSubmitEnabled(true);
                moderation.setRunEnabled(true);
            }
            case DISABLE_SUBMIT -> moderation.setSubmitEnabled(false);
            case ENABLE_SUBMIT -> moderation.setSubmitEnabled(true);
            case DISABLE_RUN -> moderation.setRunEnabled(false);
            case ENABLE_RUN -> moderation.setRunEnabled(true);
            case ADMIN_RUN_LAB_EXECUTION ->
                    throw new ContestTeamModerationException("Run Lab audit entries cannot mutate team moderation.");
        }
    }

    private String snapshotJson(ContestTeamModeration moderation) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", statusOf(moderation).name());
        snapshot.put("hiddenFromScoreboard", hiddenFromScoreboardOf(moderation));
        snapshot.put("submitEnabled", submitEnabledOf(moderation));
        snapshot.put("runEnabled", runEnabledOf(moderation));
        snapshot.put("reason", moderation == null ? null : moderation.getReason());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new ContestTeamModerationException("Could not record moderation audit values.");
        }
    }

    private Contest contest(Long contestId) {
        return contestRepository.findById(contestId)
                .orElseThrow(() -> new ContestNotFoundException(contestId));
    }

    private User team(Long teamId) {
        User user = userRepository.findById(teamId)
                .orElseThrow(() -> new UserNotFoundException(String.valueOf(teamId)));
        if (user.getRole() != Role.TEAM) {
            throw new ContestTeamModerationException("Only team accounts can be moderated.");
        }
        return user;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }

    private ContestTeamStatus statusOf(ContestTeamModeration moderation) {
        return moderation == null || moderation.getStatus() == null
                ? ContestTeamStatus.ACTIVE
                : moderation.getStatus();
    }

    private boolean hiddenFromScoreboardOf(ContestTeamModeration moderation) {
        return moderation != null && Boolean.TRUE.equals(moderation.getHiddenFromScoreboard());
    }

    private boolean submitEnabledOf(ContestTeamModeration moderation) {
        return moderation == null || !Boolean.FALSE.equals(moderation.getSubmitEnabled());
    }

    private boolean runEnabledOf(ContestTeamModeration moderation) {
        return moderation == null || !Boolean.FALSE.equals(moderation.getRunEnabled());
    }
}
