package com.server.contestControl.contestServer.sse;

import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This file is the “send updates to all connected clients” part.
 *
 */

@Component
@Slf4j
public class ContestStreamBroadcaster {

    // keep a list of all connected SSE clients, each browser tab/user has its own emitter
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // This is lifecycle management for each connected client.
    public void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(t -> emitters.remove(emitter));
    }

    /**
     * The main live-update method. Whenever a contest is updated,
     * this method is called with the update details. It then broadcasts the update to all connected clients.
     *
     * @param event
     */
    @TransactionalEventListener(fallbackExecution = true) // Don’t run this method immediately, Wait until the transaction commits successfully. AFTER_COMMIT = after transaction is successfully finished and changes are permanently stored in DB
    public void onContestUpdated(ContestUpdatedEvent event) {
        StreamPayload payload = new StreamPayload(event.reason().name(), event.snapshot());
        for (SseEmitter emitter : emitters) {
            safeSend(emitter, SseEmitter.event()
                    .name("contest-update")
                    .data(payload, MediaType.APPLICATION_JSON));
        }
    }

    // Every 15 seconds, backend sends a comment event. This is a keep-alive mechanism to prevent idle connections from being closed by proxies or browsers.
    // Heartbeat says: connection is still alive, don’t kill it
    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            safeSend(emitter, SseEmitter.event().comment("keepalive"));
        }
    }

    // lock is per-emitter, so two clients never block each other
    public void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        synchronized (emitter) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                log.debug("Dropping SSE emitter after send failure: {}", e.getMessage());
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // already in error state — just remove it, nothing else to do
                }
                emitters.remove(emitter);
            }
        }
    }

    int activeEmitterCount() {
        return emitters.size();
    }

    public record StreamPayload(String reason, Object snapshot) { }
}
