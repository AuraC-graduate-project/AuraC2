package com.server.contestControl.contestServer.controller;

import com.server.contestControl.contestServer.service.ContestService;
import com.server.contestControl.contestServer.sse.ContestStreamBroadcaster;
import com.server.contestControl.contestServer.sse.ContestStreamSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/contest")
@RequiredArgsConstructor
@Slf4j
public class ContestStreamController {

    // One connection is allowed to stay open for up to 30 minutes unless refreshed, completed, or broken.
    private static final long STREAM_TIMEOUT_MILLIS = 30L * 60_000L;

    private final ContestStreamBroadcaster broadcaster;
    private final ContestService contestService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)// This endpoint is an SSE endpoint, not a one-time JSON endpoint.
    public SseEmitter stream() {
        // This creates one SSE connection object for one browser client.
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        // The moment the page connects, backend immediately sends the initial current state.
        ContestStreamSnapshot snapshot = contestService.getStreamSnapshot();
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .name("snapshot")
                        .data(snapshot, MediaType.APPLICATION_JSON));
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }
        broadcaster.register(emitter);// store this client connection in the broadcaster so it can be notified of future updates
        return emitter;
    }
}
