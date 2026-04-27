package com.server.contestControl.contestServer.sse;

import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.shared.sse.SsePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Translates a committed {@link ContestUpdatedEvent} into an admin-facing SSE push.
 *
 * Bridges the contest domain to the shared SSE infrastructure by routing the
 * event through {@link SsePublisher} into the {@link AdminSseRegistry}.
 * fallbackExecution = true lets the listener still fire when no transaction
 * is active (useful in tests and for non-transactional publishers).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContestSseAdapter {

    private static final String EVENT_NAME = "contest-update";

    private final SsePublisher ssePublisher;
    private final AdminSseRegistry adminSseRegistry;

    @TransactionalEventListener(fallbackExecution = true)
    public void onContestUpdated(ContestUpdatedEvent event) {
        log.debug("[ContestSseAdapter] Pushing '{}': reason={}, contestId={}",
                EVENT_NAME, event.reason(), event.snapshot().getId());

        ssePublisher.publish(
                EVENT_NAME,
                new ContestSsePayload(event.reason().name(), event.snapshot()),
                adminSseRegistry
        );
    }

    public record ContestSsePayload(String reason, Object snapshot) { }
}
