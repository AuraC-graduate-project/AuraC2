package com.server.contestControl.shared.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the live collection of {@link SseEmitter}s — one per connected browser tab.
 *
 * ── This class knows nothing about ───────────────────────────────────────────
 *  - What events exist (ContestUpdatedEvent, SubmissionJudgedEvent, …)
 *  - What payload shape any event has
 *  - When to broadcast or what to put in a frame
 *
 *  Those decisions belong to the feature adapters and SsePublisher.
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 *  CopyOnWriteArrayList makes add/remove safe while broadcastAll iterates.
 *  safeSend() uses a per-emitter synchronized block so two threads can write
 *  to different clients in parallel without blocking each other.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Registers a new client connection and attaches self-cleaning lifecycle
     * callbacks so the emitter removes itself on completion, timeout, or error.
     */
    public void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(t -> emitters.remove(emitter));
        log.debug("[SseEmitterRegistry] Client registered — active: {}", emitters.size());
    }

    /**
     * Sends an SSE event to every connected client.
     * Clients that fail mid-send are silently removed.
     */
    public void broadcastAll(SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : emitters) {
            safeSend(emitter, event);
        }
    }

    /**
     * Sends to a single emitter, removing it on any I/O or state error.
     * Synchronized per-emitter — parallel sends to different clients never block each other.
     */
    public void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        synchronized (emitter) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                log.debug("[SseEmitterRegistry] Dropping dead emitter: {}", e.getMessage());
                try { emitter.complete(); } catch (Exception ignored) { }
                emitters.remove(emitter);
            }
        }
    }

    public int activeCount() {
        return emitters.size();
    }
}