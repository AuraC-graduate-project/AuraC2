package com.server.contestControl.contestServer.controller;

import com.server.contestControl.contestServer.service.ContestService;
import com.server.contestControl.contestServer.sse.contest.ContestSseRegistry;
import com.server.contestControl.contestServer.sse.contest.ContestStreamSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/contest")
@RequiredArgsConstructor
@Slf4j
public class ContestStreamController {

    // One connection is allowed to stay open for up to 30 minutes unless refreshed, completed, or broken.
    private static final long STREAM_TIMEOUT_MILLIS = 30L * 60_000L;

    private final ContestSseRegistry contestSseRegistry;
    private final ContestService contestService;

    // Stream is consumed by admin dashboard
    // (live transitions, countdown), so allow both roles. Authentication is
    // required — the previous permitAll on this path is no longer sufficient.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        ContestStreamSnapshot snapshot = contestService.getStreamSnapshot();

        // If the initial snapshot send fails, safeBroadcastSend has
        // already completed the emitter — registering it would add a dead
        // emitter to the live list and risk a heartbeat/broadcast reaching
        // a client that never received its snapshot. Short-circuit instead.
        boolean sent = contestSseRegistry.safeBroadcastSend(emitter, SseEmitter.event()
                .name("snapshot")
                .data(snapshot, MediaType.APPLICATION_JSON));
        if (!sent) return emitter;

        contestSseRegistry.register(emitter);
        return emitter;
    }
}
