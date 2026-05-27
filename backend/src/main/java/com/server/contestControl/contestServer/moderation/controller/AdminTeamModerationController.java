package com.server.contestControl.contestServer.moderation.controller;

import com.server.contestControl.contestServer.moderation.dto.ContestTeamModerationResponse;
import com.server.contestControl.contestServer.moderation.dto.ModerationActionRequest;
import com.server.contestControl.contestServer.moderation.dto.ModerationAuditLogResponse;
import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;
import com.server.contestControl.contestServer.moderation.service.ContestModerationCsvExporter;
import com.server.contestControl.contestServer.moderation.service.ContestTeamModerationService;
import com.server.contestControl.contestServer.scoreboard.sse.ScoreboardSseAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/team-moderation")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTeamModerationController {

    private final ContestTeamModerationService moderationService;
    private final ContestModerationCsvExporter csvExporter;
    private final ScoreboardSseAdapter scoreboardSseAdapter;

    @GetMapping("/contests/{contestId}/teams")
    public List<ContestTeamModerationResponse> teams(@PathVariable Long contestId) {
        return moderationService.listForContest(contestId);
    }

    @PostMapping("/contests/{contestId}/teams/{teamId}/actions")
    public ContestTeamModerationResponse moderateTeam(
            @PathVariable Long contestId,
            @PathVariable Long teamId,
            @RequestBody ModerationActionRequest request,
            Authentication authentication
    ) {
        ContestTeamModerationResponse response = moderationService.applyAction(
                contestId,
                teamId,
                request,
                authentication.getName()
        );
        scoreboardSseAdapter.publishScoreboardUpdate(contestId, "TEAM_MODERATION_" + request.actionType().name());
        return response;
    }

    @GetMapping("/logs")
    public List<ModerationAuditLogResponse> logs(
            @RequestParam(required = false) Long contestId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) ModerationActionType actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return moderationService.auditLogs(contestId, teamId, adminId, actionType, from, to);
    }

    @GetMapping("/logs.csv")
    public ResponseEntity<byte[]> exportLogsCsv(
            @RequestParam(required = false) Long contestId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) ModerationActionType actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        List<ModerationAuditLogResponse> logs =
                moderationService.auditLogs(contestId, teamId, adminId, actionType, from, to);
        String filename = contestId == null
                ? "moderation-logs-" + LocalDate.now() + ".csv"
                : "contest-" + contestId + "-moderation-logs.csv";
        byte[] bytes = csvExporter.export(logs);

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString()
                )
                .body(bytes);
    }
}
