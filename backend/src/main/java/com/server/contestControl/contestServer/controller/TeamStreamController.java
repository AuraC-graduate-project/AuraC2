package com.server.contestControl.contestServer.controller;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.contestServer.service.ContestService;
import com.server.contestControl.contestServer.sse.contest.ContestStreamSnapshot;
import com.server.contestControl.contestServer.sse.team.TeamSseRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint dedicated to team clients. Each emitter registers under the
 * team's user id, which lets the same connection receive both broadcasts
 * (contest-update, clarifications) and team-targeted events (submission
 * verdicts) without the team having to manage multiple streams.
 */
@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
@Slf4j
public class TeamStreamController {

    // Match ContestStreamController's 30-minute idle timeout — same proxy
    // and browser timeout pressures apply.
    private static final long STREAM_TIMEOUT_MILLIS = 30L * 60_000L;

    private final TeamSseRegistry teamSseRegistry;
    private final ContestService contestService;

    @PreAuthorize("hasRole('TEAM')")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal User user) {
        Long teamId = user.getId();

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        ContestStreamSnapshot snapshot = contestService.getStreamSnapshot();

        // Same short-circuit as ContestStreamController: if the initial send
        // fails, the emitter is already completed; registering it would add a
        // dead emitter to the targeted bucket and risk pings/broadcasts going
        // to a client that never received its snapshot.
        boolean sent = teamSseRegistry.safeTargetedSend(teamId, emitter, SseEmitter.event()
                .name("snapshot")
                .data(snapshot, MediaType.APPLICATION_JSON));
        if (!sent) return emitter;

        teamSseRegistry.register(teamId, emitter);
        return emitter;
    }
}
