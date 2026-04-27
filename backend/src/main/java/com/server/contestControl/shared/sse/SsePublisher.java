package com.server.contestControl.shared.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The single entry point for pushing SSE events from any feature.
 *
 * ── Contract ─────────────────────────────────────────────────────────────────
 *  Features call publish() and provide two things:
 *    eventName  — the SSE "event:" field the browser listens for
 *    payload    — any serialisable object (record, DTO, Map …)
 *
 *  That is the entire API surface. Features never touch SseEmitterRegistry
 *  or SseEmitter directly.
 *
 * ── Adding SSE to a new feature ──────────────────────────────────────────────
 *  1. Create a @Component adapter class in your feature's package.
 *  2. Inject SsePublisher.
 *  3. Listen to your domain event with @TransactionalEventListener (or
 *     @EventListener for non-transactional events).
 *  4. Call ssePublisher.publish("your-event-name", payload).
 *  5. Done. No changes to this file or any infrastructure class.
 *
 * ── Why this class exists separately ─────────────────────────────────────────
 *  SseEmitterRegistry is a connection store — it must not grow domain
 *  knowledge. SsePublisher is a thin façade that gives features a stable,
 *  named contract while hiding the send/error/remove mechanics underneath.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SsePublisher {

    private final SseEmitterRegistry registry;

    /**
     * Broadcasts an SSE event to every currently connected client.
     *
     * @param eventName  the SSE event type string (e.g. "contest-update",
     *                   "submission-verdict"). The frontend listens with
     *                   source.addEventListener(eventName, handler).
     * @param payload    any JSON-serialisable value — record, DTO, Map, etc.
     */
    public void publish(String eventName, Object payload) {
        int clients = registry.activeCount();
        if (clients == 0) return;

        log.debug("[SsePublisher] Broadcasting '{}' to {} client(s)", eventName, clients);
        registry.broadcastAll(
                SseEmitter.event()
                        .name(eventName)
                        .data(payload, MediaType.APPLICATION_JSON)
        );
    }

    public void publish(String eventName, Object payload, SseEmitter emitter) {
        log.debug("[SsePublisher] Sending '{}' to single client", eventName);
        registry.safeSend(
                emitter,
                SseEmitter.event()
                        .name(eventName)
                        .data(payload, MediaType.APPLICATION_JSON)
        );
    }
}
