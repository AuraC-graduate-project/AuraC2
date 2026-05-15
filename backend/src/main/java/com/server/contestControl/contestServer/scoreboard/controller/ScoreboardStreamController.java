package com.server.contestControl.contestServer.scoreboard.controller;

import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.service.ScoreboardService;
import com.server.contestControl.contestServer.scoreboard.sse.ScoreboardSseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/scoreboard")
@RequiredArgsConstructor
public class ScoreboardStreamController {

    private static final long STREAM_TIMEOUT_MILLIS = 30L * 60_000L;

    private final ScoreboardSseRegistry scoreboardSseRegistry;
    private final ScoreboardService scoreboardService;

    @GetMapping(value = "/contests/{contestId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long contestId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        ScoreboardSnapshot snapshot = scoreboardService.getPublicSnapshot(contestId);
        boolean sent = scoreboardSseRegistry.safeTargetedSend(
                contestId,
                emitter,
                SseEmitter.event().name("snapshot").data(snapshot, MediaType.APPLICATION_JSON)
        );
        if (!sent) return emitter;

        scoreboardSseRegistry.register(contestId, emitter);
        return emitter;
    }
}
