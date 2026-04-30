package com.server.contestControl.shared.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Thin façade that wraps SSE event construction and dispatching.
 *
 * Stateless — registries are passed in by the caller so a single publisher
 * can serve every audience-specific registry (admin, team, …) without
 * needing one publisher bean per audience.
 *
 * ── Calling pattern ──────────────────────────────────────────────────────────
 *  publish(name, payload, registry)         — broadcast to every client of that registry
 *  publishTo(id, name, payload, registry)   — send only to clients registered under id
 */
@Component
@Slf4j
public class SsePublisher {

    /**
     * Broadcasts an SSE event to every broadcast-registered client of {@code registry}.
     *
     * @param eventName  the SSE event type string (e.g. "contest-update").
     * @param payload    any JSON-serialisable value — record, DTO, Map, etc.
     * @param registry   the audience-specific registry to broadcast through.
     */
    public void publish(String eventName, Object payload, SseEmitterRegistry registry) {
        registry.broadcastAll(
                SseEmitter.event()
                        .name(eventName)
                        .data(payload, MediaType.APPLICATION_JSON)
        );
    }

    /**
     * Sends an SSE event only to clients registered under {@code id} in
     * {@code registry}. Other clients of the same registry do not receive it.
     */
    public void publishTo(Long id, String eventName, Object payload, SseEmitterRegistry registry) {
        registry.publishTo(
                id,
                SseEmitter.event()
                        .name(eventName)
                        .data(payload, MediaType.APPLICATION_JSON)
        );
    }

    /**
     * Broadcasts an SSE event to every targeted emitter across all id buckets
     * of {@code registry}. Used for fan-out to audiences that register under
     * an id (e.g. all connected teams), where {@link #publish} would miss them
     * because it only reaches broadcast-registered emitters.
     */
    public void publishToAllTeams(String eventName, Object payload, SseEmitterRegistry registry) {
        registry.broadcastToAllTargeted(
                SseEmitter.event()
                        .name(eventName)
                        .data(payload, MediaType.APPLICATION_JSON)
        );
    }
}
