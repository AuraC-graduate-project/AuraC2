package com.server.contestControl.contestServer.sse;

import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.shared.sse.SsePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Translates a committed {@link ContestUpdatedEvent} into an SSE push.
 *
 * ── Responsibility ────────────────────────────────────────────────────────────
 *  This is the ONLY class that knows both (a) the ContestUpdatedEvent domain
 *  type and (b) the SSE infrastructure. It is the bridge — intentionally thin.
 *
 * ── Why @TransactionalEventListener ──────────────────────────────────────────
 *  Default phase is AFTER_COMMIT. Clients only receive updates after the DB
 *  write is durable — they never see a state that might still roll back.
 *  fallbackExecution = true lets it fire even without an active transaction
 *  (useful in tests and for events published by non-transactional code).
 *
 * ── Template for new features ────────────────────────────────────────────────
 *  To add SSE to a new feature (e.g. submissions), create a parallel class:
 *
 *      @Component
 *      @RequiredArgsConstructor
 *      public class SubmissionSseAdapter {
 *
 *          private final SsePublisher ssePublisher;
 *
 *          @TransactionalEventListener(fallbackExecution = true)
 *          public void onSubmissionJudged(SubmissionJudgedEvent event) {
 *              ssePublisher.publish("submission-verdict", event.submission());
 *          }
 *      }
 *
 *  That is the entire change required. No modifications to SsePublisher,
 *  SseEmitterRegistry, SseHeartbeatScheduler, or any other existing file.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContestSseAdapter {

    /** The SSE event name the frontend registers: source.addEventListener("contest-update", …) */
    private static final String EVENT_NAME = "contest-update";

    private final SsePublisher ssePublisher;

    @TransactionalEventListener(fallbackExecution = true)
    public void onContestUpdated(ContestUpdatedEvent event) {
        log.debug("[ContestSseAdapter] Pushing '{}': reason={}, contestId={}",
                EVENT_NAME, event.reason(), event.snapshot().getId());

        ssePublisher.publish(EVENT_NAME, new ContestSsePayload(
                event.reason().name(),
                event.snapshot()
        ));
    }

    /**
     * The JSON shape the frontend's useContestStream.ts expects as ContestStreamUpdate:
     * { reason: string, snapshot: ContestResponse }
     */
    public record ContestSsePayload(String reason, Object snapshot) { }
}

