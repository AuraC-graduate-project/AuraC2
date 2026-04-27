package com.server.contestControl.contestServer.controller;

import com.server.contestControl.contestServer.service.ContestService;
import com.server.contestControl.contestServer.sse.AdminSseRegistry;
import com.server.contestControl.contestServer.sse.ContestStreamSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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

    private final AdminSseRegistry adminSseRegistry;
    private final ContestService contestService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        ContestStreamSnapshot snapshot = contestService.getStreamSnapshot();
        adminSseRegistry.safeSend(emitter, SseEmitter.event()
                .name("snapshot")
                .data(snapshot, MediaType.APPLICATION_JSON));

        adminSseRegistry.register(emitter);
        return emitter;
    }
}
