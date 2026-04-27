package com.server.contestControl.contestServer.sse;

import com.server.contestControl.shared.sse.SseEmitterRegistry;
import org.springframework.stereotype.Component;

/**
 * Registry for admin-facing SSE clients (contest dashboard).
 *
 * Subclassing makes this a distinct Spring bean from any other audience-specific
 * registry (e.g. a future TeamSseRegistry), so SseHeartbeatScheduler can collect
 * them via {@code List<SseEmitterRegistry>} and SsePublisher can target one
 * audience at a time.
 */
@Component
public class AdminSseRegistry extends SseEmitterRegistry {
}
