package com.server.contestControl.contestServer.scoreboard.sse;

import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardMetadata;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardUpdatePayload;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
import com.server.contestControl.contestServer.scoreboard.service.ScoreboardService;
import com.server.contestControl.contestServer.scoreboard.service.ScoreboardVersionService;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.event.SubmissionFinalizedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreboardSseAdapterTest {

    @Mock
    private ScoreboardService scoreboardService;

    @Mock
    private ScoreboardVersionService versionService;

    @Mock
    private ScoreboardSsePublisher publisher;

    @InjectMocks
    private ScoreboardSseAdapter adapter;

    @Test
    void contestUpdatePublishesVersionedAdminAndPublicScoreboardUpdates() {
        ScoreboardSnapshot adminSnapshot = snapshot(ScoreboardAudience.ADMIN, 1L, false);
        ScoreboardSnapshot publicSnapshot = snapshot(ScoreboardAudience.PUBLIC, 1L, false);
        ScoreboardUpdatePayload adminPayload = payload(ScoreboardAudience.ADMIN, "scoreboard-update");
        ScoreboardUpdatePayload publicPayload = payload(ScoreboardAudience.PUBLIC, "scoreboard-update");

        when(versionService.next(1L, ScoreboardAudience.ADMIN)).thenReturn(1L);
        when(versionService.next(1L, ScoreboardAudience.PUBLIC)).thenReturn(1L);
        when(scoreboardService.getSnapshot(1L, ScoreboardAudience.ADMIN, 1L)).thenReturn(adminSnapshot);
        when(scoreboardService.getSnapshot(1L, ScoreboardAudience.PUBLIC, 1L)).thenReturn(publicSnapshot);
        when(scoreboardService.buildUpdatePayload(
                eq(ScoreboardSsePublisher.SCOREBOARD_UPDATE),
                eq("CONTEST_AUTO_START"),
                eq(null),
                eq(adminSnapshot)
        )).thenReturn(adminPayload);
        when(scoreboardService.buildUpdatePayload(
                eq(ScoreboardSsePublisher.SCOREBOARD_UPDATE),
                eq("CONTEST_AUTO_START"),
                eq(null),
                eq(publicSnapshot)
        )).thenReturn(publicPayload);

        adapter.onContestUpdated(new ContestUpdatedEvent(
                ContestUpdatedEvent.Reason.AUTO_START,
                ContestResponse.builder().id(1L).build()
        ));

        verify(publisher).publishAdmin(1L, ScoreboardSsePublisher.SCOREBOARD_UPDATE, adminPayload);
        verify(publisher).publishPublic(1L, ScoreboardSsePublisher.SCOREBOARD_UPDATE, publicPayload);
    }

    @Test
    void frozenPublicSubmissionUpdatePublishesFreezeEventForPublicAndLiveUpdateForAdmin() {
        ScoreboardSnapshot adminSnapshot = snapshot(ScoreboardAudience.ADMIN, 2L, false);
        ScoreboardSnapshot publicFrozenSnapshot = snapshot(ScoreboardAudience.PUBLIC, 2L, true);
        ScoreboardUpdatePayload adminPayload = payload(ScoreboardAudience.ADMIN, "scoreboard-update");
        ScoreboardUpdatePayload publicPayload = payload(ScoreboardAudience.PUBLIC, "scoreboard-freeze");

        when(versionService.next(1L, ScoreboardAudience.ADMIN)).thenReturn(2L);
        when(versionService.next(1L, ScoreboardAudience.PUBLIC)).thenReturn(2L);
        when(scoreboardService.getSnapshot(1L, ScoreboardAudience.ADMIN, 2L)).thenReturn(adminSnapshot);
        when(scoreboardService.getSnapshot(1L, ScoreboardAudience.PUBLIC, 2L)).thenReturn(publicFrozenSnapshot);
        when(scoreboardService.buildUpdatePayload(
                eq(ScoreboardSsePublisher.SCOREBOARD_UPDATE),
                eq("SUBMISSION_FINALIZED_ACCEPTED"),
                eq(null),
                eq(adminSnapshot)
        )).thenReturn(adminPayload);
        when(scoreboardService.buildUpdatePayload(
                eq(ScoreboardSsePublisher.SCOREBOARD_FREEZE),
                eq("SUBMISSION_FINALIZED_ACCEPTED"),
                eq(null),
                eq(publicFrozenSnapshot)
        )).thenReturn(publicPayload);

        adapter.onSubmissionFinalized(new SubmissionFinalizedEvent(
                99L,
                1L,
                10L,
                100L,
                Verdict.ACCEPTED
        ));

        verify(publisher).publishAdmin(1L, ScoreboardSsePublisher.SCOREBOARD_UPDATE, adminPayload);
        verify(publisher).publishPublic(1L, ScoreboardSsePublisher.SCOREBOARD_FREEZE, publicPayload);
    }

    private ScoreboardSnapshot snapshot(ScoreboardAudience audience, long version, boolean frozen) {
        ScoreboardMetadata metadata = new ScoreboardMetadata(
                1L,
                "ICPC Local",
                audience,
                version,
                Instant.parse("2026-05-15T10:00:00Z"),
                "RUNNING",
                "RUNNING",
                audience == ScoreboardAudience.ADMIN,
                frozen,
                frozen ? Instant.parse("2026-05-15T10:00:00Z") : null,
                60,
                20,
                RevealStatus.NOT_STARTED,
                0L,
                frozen ? 1L : 0L,
                List.of()
        );
        return new ScoreboardSnapshot(metadata, List.of());
    }

    private ScoreboardUpdatePayload payload(ScoreboardAudience audience, String eventName) {
        ScoreboardSnapshot snapshot = snapshot(audience, 1L, eventName.equals("scoreboard-freeze"));
        return new ScoreboardUpdatePayload(
                eventName,
                "TEST",
                1L,
                snapshot.metadata().version(),
                0L,
                true,
                List.of(),
                List.of(),
                snapshot.metadata(),
                snapshot
        );
    }
}
