package com.server.contestControl.contestServer.scoreboard.sse;

import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardUpdatePayload;
import com.server.contestControl.shared.sse.SsePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScoreboardSsePublisher {

    public static final String SCOREBOARD_UPDATE = "scoreboard-update";
    public static final String SCOREBOARD_FREEZE = "scoreboard-freeze";
    public static final String SCOREBOARD_REVEAL_STEP = "scoreboard-reveal-step";

    private final SsePublisher ssePublisher;
    private final ScoreboardSseRegistry scoreboardSseRegistry;
    private final AdminScoreboardSseRegistry adminScoreboardSseRegistry;

    public void publishPublic(Long contestId, String eventName, ScoreboardUpdatePayload payload) {
        ssePublisher.publishTo(contestId, eventName, payload, scoreboardSseRegistry);
    }

    public void publishAdmin(Long contestId, String eventName, ScoreboardUpdatePayload payload) {
        ssePublisher.publishTo(contestId, eventName, payload, adminScoreboardSseRegistry);
    }
}
