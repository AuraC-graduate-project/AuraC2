package com.server.contestControl.submissionServer.sse;

import com.server.contestControl.shared.sse.SseEmitterRegistry;
import org.springframework.stereotype.Component;

/**
 * SSE registry for admin submission streams.
 * Emitters are broadcast-registered so every connected admin receives
 * all submission updates across all teams and contests.
 */
@Component
public class AdminSubmissionSseRegistry extends SseEmitterRegistry {
}
