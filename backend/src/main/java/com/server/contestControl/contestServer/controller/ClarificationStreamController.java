package com.server.contestControl.contestServer.controller;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.contestServer.sse.clarification.ClarificationSseRegistry;
import com.server.contestControl.contestServer.sse.team.TeamSseRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/clarifications")
@RequiredArgsConstructor
@Slf4j
public class ClarificationStreamController {

    private static final long STREAM_TIMEOUT_MILLIS = 30L * 60_000L;

    private final ClarificationSseRegistry clarificationSseRegistry;
    private final TeamSseRegistry teamSseRegistry;

    @PreAuthorize("hasRole('TEAM')")
    @GetMapping(value = "/my/stream/{contestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter teamStream(
            @PathVariable Long contestId,
            @AuthenticationPrincipal User user) {

        Long teamId = user.getId();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        // Register team for this contest (use composite key if needed)
        String registrationKey = "clarif_team_" + contestId + "_" + teamId;
        teamSseRegistry.register(teamId, emitter);

        log.debug("[ClarificationStreamController] Team {} connected to clarification stream for contest {}", 
                  teamId, contestId);

        return emitter;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/admin/stream/{contestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminStream(
            @PathVariable Long contestId,
            @AuthenticationPrincipal User admin) {

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        // Register admin for this contest (use contestId as key)
        clarificationSseRegistry.register(contestId, emitter);

        log.debug("[ClarificationStreamController] Admin {} connected to clarification stream for contest {}", 
                  admin.getId(), contestId);

        return emitter;
    }
}
