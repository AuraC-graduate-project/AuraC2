package com.server.contestControl.shared.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Sends an SSE comment (":keepalive") to every connected client every 15 seconds.
 *
 * SSE comment lines are invisible to the browser application — they exist only
 * to produce traffic that prevents proxies, load balancers, and browsers from
 * treating an idle connection as dead and closing it.
 *
 * Spring auto-collects every SseEmitterRegistry bean (e.g. AdminSseRegistry)
 * into the injected list, so adding a new audience requires no change here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseHeartbeatScheduler {

    private final List<SseEmitterRegistry> registries;

    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        registries.forEach(r -> r.broadcastAll(SseEmitter.event().comment("keepalive")));
    }
}
