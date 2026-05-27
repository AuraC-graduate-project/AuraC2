package com.server.contestControl.contestServer.moderation.controller;

import com.server.contestControl.contestServer.moderation.dto.TeamContestAccessResponse;
import com.server.contestControl.contestServer.moderation.service.ContestTeamModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team/contests/{contestId}/access")
@PreAuthorize("hasRole('TEAM')")
@RequiredArgsConstructor
public class TeamContestAccessController {

    private final ContestTeamModerationService moderationService;

    @GetMapping
    public TeamContestAccessResponse access(
            @PathVariable Long contestId,
            Authentication authentication
    ) {
        return moderationService.teamAccess(contestId, authentication.getName());
    }
}
