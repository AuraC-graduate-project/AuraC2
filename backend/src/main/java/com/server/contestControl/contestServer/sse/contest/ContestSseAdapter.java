package com.server.contestControl.contestServer.sse.contest;

import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.shared.sse.SsePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Translates a committed {@link ContestUpdatedEvent} into a contest SSE push.
 *
 * Bridges the contest domain to the shared SSE infrastructure by routing the
 * event through {@link SsePublisher} into the {@link ContestSseRegistry}.
 * fallbackExecution = true lets the listener still fire when no transaction
 * is active (useful in tests and for non-transactional publishers).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContestSseAdapter {

    private static final String EVENT_NAME = "contest-update";

    private final SsePublisher ssePublisher;
    private final ContestSseRegistry contestSseRegistry;

    @TransactionalEventListener(fallbackExecution = true)
    public void onContestUpdated(ContestUpdatedEvent event) {
        log.debug("[ContestSseAdapter] Pushing '{}': reason={}, contestId={}",
                EVENT_NAME, event.reason(), event.snapshot().getId());

        ssePublisher.publish(
                EVENT_NAME,
                new ContestSsePayload(event.reason().name(), event.snapshot()),
                contestSseRegistry
        );
    }

    // Snapshot is always a ContestResponse — typing it explicitly makes
    // the wire-format contract obvious and lets Jackson keep full type info.
    public record ContestSsePayload(String reason, ContestResponse snapshot) { }
}
