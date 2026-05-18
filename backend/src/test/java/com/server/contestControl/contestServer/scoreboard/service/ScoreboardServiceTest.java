package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardMetadata;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardProblemCell;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardUpdatePayload;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreboardServiceTest {

    private final ScoreboardService service = new ScoreboardService(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    @Test
    void buildUpdatePayloadMarksFullSnapshotWhenNoPreviousSnapshotExists() {
        ScoreboardSnapshot current = snapshot(1L, row(1, 100L, "alpha", 1, 25));

        ScoreboardUpdatePayload payload = service.buildUpdatePayload(
                "scoreboard-update",
                "SUBMISSION_FINALIZED_ACCEPTED",
                null,
                current
        );

        assertThat(payload.fullSnapshot()).isTrue();
        assertThat(payload.previousVersion()).isZero();
        assertThat(payload.changedTeamIds()).containsExactly(100L);
        assertThat(payload.changedRows()).containsExactly(current.rows().getFirst());
        assertThat(payload.snapshot()).isEqualTo(current);
    }

    @Test
    void buildUpdatePayloadOnlyIncludesRowsThatChanged() {
        ScoreboardSnapshot previous = snapshot(
                7L,
                row(1, 100L, "alpha", 1, 25),
                row(2, 200L, "beta", 0, 0)
        );
        ScoreboardSnapshot current = snapshot(
                8L,
                row(1, 100L, "alpha", 1, 25),
                row(2, 200L, "beta", 1, 40)
        );

        ScoreboardUpdatePayload payload = service.buildUpdatePayload(
                "scoreboard-update",
                "SUBMISSION_FINALIZED_ACCEPTED",
                previous,
                current
        );

        assertThat(payload.fullSnapshot()).isFalse();
        assertThat(payload.previousVersion()).isEqualTo(7L);
        assertThat(payload.version()).isEqualTo(8L);
        assertThat(payload.changedTeamIds()).containsExactly(200L);
        assertThat(payload.changedRows()).containsExactly(current.rows().get(1));
        assertThat(payload.snapshot()).isNull();
    }

    private ScoreboardSnapshot snapshot(long version, ScoreboardRow... rows) {
        return new ScoreboardSnapshot(
                new ScoreboardMetadata(
                        1L,
                        "ICPC Local",
                        ScoreboardAudience.PUBLIC,
                        version,
                        Instant.parse("2026-05-15T10:00:00Z"),
                        "RUNNING",
                        "RUNNING",
                        false,
                        false,
                        null,
                        60,
                        20,
                        RevealStatus.NOT_STARTED,
                        0,
                        0,
                        List.of(new ScoreboardMetadata.ProblemColumn(10L, "A", "Warmup", "#2563EB"))
                ),
                List.of(rows)
        );
    }

    private ScoreboardRow row(int rank, Long teamId, String teamName, int solved, int penalty) {
        return new ScoreboardRow(
                rank,
                teamId,
                teamName,
                solved,
                penalty,
                List.of(new ScoreboardProblemCell(10L, "A", solved > 0, solved > 0 ? 1 : 0, 0, 0, solved > 0 ? penalty : null, solved > 0 ? penalty : null, solved > 0, false, false))
        );
    }
}
