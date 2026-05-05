package com.server.contestControl.contestServer.sse.team;

import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.contestServer.sse.contest.ContestSseAdapter;
import com.server.contestControl.shared.sse.SsePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mirrors {@link ContestSseAdapter}, but pushes the same contest-update payload
 * to every connected team via {@link TeamSseRegistry}. Teams register under
 * their own id, so the admin-facing broadcastAll() never reaches them — this
 * adapter fans the event out across the team registry's targeted buckets.
 *
 * Wire format matches the admin stream (same event name and payload shape) so
 * the frontend useContestStream hook can be shared between audiences.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TeamContestSseAdapter {

    private static final String EVENT_NAME = "contest-update";

    private final SsePublisher ssePublisher;
    private final TeamSseRegistry teamSseRegistry;

    @TransactionalEventListener(fallbackExecution = true)
    public void onContestUpdated(ContestUpdatedEvent event) {
        log.debug("[TeamContestSseAdapter] Pushing '{}': reason={}, contestId={}",
                EVENT_NAME, event.reason(), event.snapshot().getId());

        ssePublisher.publishToAllTeams(
                EVENT_NAME,
                new ContestSseAdapter.ContestSsePayload(event.reason().name(), event.snapshot()),
                teamSseRegistry
        );
    }
}
