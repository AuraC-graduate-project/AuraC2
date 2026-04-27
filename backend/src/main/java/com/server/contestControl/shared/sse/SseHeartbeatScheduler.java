package com.server.contestControl.shared.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Sends an SSE comment (":keepalive") to every connected client every 15 seconds.
 *
 * SSE comment lines are invisible to the browser application — they exist only
 * to produce traffic that prevents proxies, load balancers, and browsers from
 * treating an idle connection as dead and closing it.
 *
 * Extracted into its own bean so that SseEmitterRegistry stays a pure store
 * (no scheduling annotations) and SsePublisher stays a pure push API
 * (no scheduling annotations).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseHeartbeatScheduler {

    private final SseEmitterRegistry registry;

    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        int count = registry.activeCount();
        if (count == 0) return;
        log.debug("[SseHeartbeatScheduler] Keepalive → {} client(s)", count);
        registry.broadcastAll(SseEmitter.event().comment("keepalive"));
    }
}

