package com.server.contestControl.submissionServer.sse;

import com.server.contestControl.shared.sse.SseEmitterRegistry;
import org.springframework.stereotype.Component;

/**
 * SSE registry for team submission streams.
 * Emitters are registered targeted by team user ID so each team only
 * receives updates for their own submissions.
 */
@Component
public class SubmissionSseRegistry extends SseEmitterRegistry {
}
