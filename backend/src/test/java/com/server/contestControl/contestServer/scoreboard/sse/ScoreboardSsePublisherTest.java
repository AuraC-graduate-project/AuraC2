package com.server.contestControl.contestServer.scoreboard.sse;

import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardMetadata;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardUpdatePayload;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
import com.server.contestControl.shared.sse.SsePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScoreboardSsePublisherTest {

    @Mock
    private SsePublisher ssePublisher;

    @Mock
    private ScoreboardSseRegistry scoreboardSseRegistry;

    @Mock
    private AdminScoreboardSseRegistry adminScoreboardSseRegistry;

    @InjectMocks
    private ScoreboardSsePublisher scoreboardSsePublisher;

    @Test
    void publishPublicUsesPublicRegistryAndContestTarget() {
        ScoreboardUpdatePayload payload = payload(ScoreboardAudience.PUBLIC);

        scoreboardSsePublisher.publishPublic(1L, ScoreboardSsePublisher.SCOREBOARD_UPDATE, payload);

        verify(ssePublisher).publishTo(1L, ScoreboardSsePublisher.SCOREBOARD_UPDATE, payload, scoreboardSseRegistry);
    }

    @Test
    void publishAdminUsesAdminRegistryAndContestTarget() {
        ScoreboardUpdatePayload payload = payload(ScoreboardAudience.ADMIN);

        scoreboardSsePublisher.publishAdmin(1L, ScoreboardSsePublisher.SCOREBOARD_REVEAL_STEP, payload);

        verify(ssePublisher).publishTo(1L, ScoreboardSsePublisher.SCOREBOARD_REVEAL_STEP, payload, adminScoreboardSseRegistry);
    }

    private ScoreboardUpdatePayload payload(ScoreboardAudience audience) {
        ScoreboardMetadata metadata = new ScoreboardMetadata(
                1L,
                "ICPC Local",
                audience,
                4L,
                Instant.parse("2026-05-15T10:00:00Z"),
                "RUNNING",
                "RUNNING",
                audience == ScoreboardAudience.ADMIN,
                false,
                null,
                60,
                20,
                RevealStatus.NOT_STARTED,
                0L,
                0L,
                List.of()
        );
        return new ScoreboardUpdatePayload(
                ScoreboardSsePublisher.SCOREBOARD_UPDATE,
                "TEST",
                1L,
                4L,
                3L,
                false,
                List.of(),
                List.of(),
                metadata,
                new ScoreboardSnapshot(metadata, List.of())
        );
    }
}
