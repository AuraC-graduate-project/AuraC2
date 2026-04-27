package com.server.contestControl.shared.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the live collection of {@link SseEmitter}s — one per connected browser tab.
 *
 * Plain class (not @Component) — concrete subclasses are the Spring beans
 * (e.g. AdminSseRegistry). That lets a single class power multiple audience-specific
 * registries that {@link SseHeartbeatScheduler} can iterate uniformly.
 *
 * ── Two registration modes ───────────────────────────────────────────────────
 *  register(emitter)        — broadcast emitter, receives every broadcastAll() event
 *  register(id, emitter)    — targeted emitter, receives only publishTo(id, ...) events
 *
 * ── Thread safety ────────────────────────────────────────────────────────────
 *  CopyOnWriteArrayList makes add/remove safe while iterating.
 *  ConcurrentHashMap.computeIfAbsent atomically lazy-creates per-id buckets.
 *  safeSend() uses a per-emitter synchronized block so two threads can write
 *  to different clients in parallel without blocking each other.
 */
@Slf4j
public class SseEmitterRegistry {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Map<Long, List<SseEmitter>> targetedEmitters = new ConcurrentHashMap<>();

    /**
     * Registers a broadcast client — receives every event sent via broadcastAll().
     */
    public void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(t -> emitters.remove(emitter));
        log.debug("[SseEmitterRegistry] Broadcast client registered — broadcast active: {}", emitters.size());
    }

    /**
     * Registers a targeted client under {@code id} — receives only events sent
     * via publishTo(id, ...). Multiple emitters may share the same id.
     */
    public void register(Long id, SseEmitter emitter) {
        targetedEmitters.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeTargeted(id, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeTargeted(id, emitter);
        });
        emitter.onError(t -> removeTargeted(id, emitter));
        log.debug("[SseEmitterRegistry] Targeted client registered for id={}", id);
    }

    /**
     * Sends an SSE event to every broadcast-registered client.
     * Targeted clients (registered with an id) are not included.
     */
    public void broadcastAll(SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : emitters) {
            safeSend(emitter, event);
        }
    }

    /**
     * Sends an SSE event to every emitter registered under {@code id}.
     * No-op if no emitters are registered for that id.
     */
    public void publishTo(Long id, SseEmitter.SseEventBuilder event) {
        List<SseEmitter> list = targetedEmitters.get(id);
        if (list == null) return;
        list.forEach(emitter -> safeSend(emitter, event));
    }

    /**
     * Sends to a single emitter, removing it from all internal lists on any
     * I/O or state error. Synchronized per-emitter — parallel sends to
     * different clients never block each other.
     */
    public void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        synchronized (emitter) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                log.debug("[SseEmitterRegistry] Dropping dead emitter: {}", e.getMessage());
                try { emitter.complete(); } catch (Exception ignored) { }
                emitters.remove(emitter);
                targetedEmitters.values().forEach(list -> list.remove(emitter));
            }
        }
    }

    public int activeCount() {
        return emitters.size()
                + targetedEmitters.values().stream().mapToInt(List::size).sum();
    }

    private void removeTargeted(Long id, SseEmitter emitter) {
        List<SseEmitter> list = targetedEmitters.get(id);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) {
            targetedEmitters.remove(id, list);
        }
    }
}
