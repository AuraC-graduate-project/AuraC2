package com.server.contestControl.contestServer.sse.contest;

import com.server.contestControl.shared.sse.SseEmitterRegistry;
import org.springframework.stereotype.Component;

/**
 * Registry for contest SSE clients (admins and teams).
 *
 * Renamed from AdminSseRegistry: the /api/contest/stream endpoint is consumed
 * by both admin dashboards and team workspaces (live transitions, countdown),
 * so an "admin"-named bean was misleading.
 *
 * Subclassing keeps this a distinct Spring bean from any future audience-specific
 * registry, so SseHeartbeatScheduler can collect them via {@code List<SseEmitterRegistry>}
 * and SsePublisher can target one audience at a time.
 */
@Component
public class ContestSseRegistry extends SseEmitterRegistry {
}
