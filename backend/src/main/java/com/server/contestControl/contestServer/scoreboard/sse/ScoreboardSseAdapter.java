package com.server.contestControl.contestServer.scoreboard.sse;

import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardUpdatePayload;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
import com.server.contestControl.contestServer.scoreboard.service.ScoreboardService;
import com.server.contestControl.contestServer.scoreboard.service.ScoreboardVersionService;
import com.server.contestControl.submissionServer.event.SubmissionFinalizedEvent;
import com.server.contestControl.submissionServer.event.SubmissionRejudgeQueuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScoreboardSseAdapter {

    private final ScoreboardService scoreboardService;
    private final ScoreboardVersionService versionService;
    private final ScoreboardSsePublisher publisher;
    private final Map<String, ScoreboardSnapshot> lastSnapshots = new ConcurrentHashMap<>();

    @TransactionalEventListener(fallbackExecution = true)
    public void onSubmissionFinalized(SubmissionFinalizedEvent event) {
        publishScoreboardUpdate(event.contestId(), "SUBMISSION_FINALIZED_" + event.verdict().name());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onSubmissionRejudgeQueued(SubmissionRejudgeQueuedEvent event) {
        publishScoreboardUpdate(event.contestId(), "REJUDGE_QUEUED");
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onContestUpdated(ContestUpdatedEvent event) {
        publishScoreboardUpdate(event.snapshot().getId(), "CONTEST_" + event.reason().name());
    }

    public void publishScoreboardUpdate(Long contestId, String reason) {
        publish(contestId, ScoreboardAudience.ADMIN, ScoreboardSsePublisher.SCOREBOARD_UPDATE, reason);
        publish(contestId, ScoreboardAudience.PUBLIC, null, reason);
    }

    public void publishRevealStep(Long contestId, String reason) {
        publish(contestId, ScoreboardAudience.PUBLIC, ScoreboardSsePublisher.SCOREBOARD_REVEAL_STEP, reason);
        publish(contestId, ScoreboardAudience.ADMIN, ScoreboardSsePublisher.SCOREBOARD_REVEAL_STEP, reason);
    }

    public void publishFreeze(Long contestId, String reason) {
        publish(contestId, ScoreboardAudience.PUBLIC, ScoreboardSsePublisher.SCOREBOARD_FREEZE, reason);
        publish(contestId, ScoreboardAudience.ADMIN, ScoreboardSsePublisher.SCOREBOARD_UPDATE, reason);
    }

    private void publish(Long contestId, ScoreboardAudience audience, String forcedEventName, String reason) {
        long version = versionService.next(contestId, audience);
        ScoreboardSnapshot current = scoreboardService.getSnapshot(contestId, audience, version);
        String key = contestId + ":" + audience.name();
        ScoreboardSnapshot previous = lastSnapshots.put(key, current);

        String eventName = forcedEventName;
        if (eventName == null) {
            eventName = current.metadata().scoreboardFrozen()
                    ? ScoreboardSsePublisher.SCOREBOARD_FREEZE
                    : ScoreboardSsePublisher.SCOREBOARD_UPDATE;
        }

        ScoreboardUpdatePayload payload = scoreboardService.buildUpdatePayload(
                eventName,
                reason,
                previous,
                current
        );

        log.debug(
                "Publishing scoreboard event. contestId={} audience={} eventName={} reason={} version={}",
                contestId,
                audience,
                eventName,
                reason,
                version
        );

        if (audience == ScoreboardAudience.ADMIN) {
            publisher.publishAdmin(contestId, eventName, payload);
        } else {
            publisher.publishPublic(contestId, eventName, payload);
        }
    }
}
