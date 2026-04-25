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
 * Sending real-time updates to all connected frontend clients.
 *
 */

@Component
@Slf4j
public class ContestStreamBroadcaster {

    // keep a list of all connected SSE clients, each browser tab/user has its own emitter
    // Normal ArrayList is not safe if one thread is looping while another thread removes an item.
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // This is lifecycle management for each connected client.
    // Add this browser connection to the list of clients.
    // Also define cleanup behavior.
    public void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter)); // When this SSE connection is finished, remove it from the active clients list.
        emitter.onTimeout(() -> {//
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(t -> emitters.remove(emitter));// When 30-minute timeout happens, complete and remove it.
    }

    /**
     * The main live-update method. Whenever a contest is updated,
     * this method is called with the update details. It then broadcasts the update to all connected clients.
     *
     * This listens to ContestUpdatedEvent object,
     *
     * Listen to events related to transactions.
     * Usually run after transaction commit.
     * If no transaction exists, still run because fallbackExecution = true.
     */
    @TransactionalEventListener(fallbackExecution = true)
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

    // lock is per-emitter, so two clients never block each other,
    // Safely sends one SSE event to one client.
    public void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        synchronized (emitter) { // For this one client connection, only one send happens at a time.
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                log.debug("Dropping SSE emitter after send failure: {}", e.getMessage()); //
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
