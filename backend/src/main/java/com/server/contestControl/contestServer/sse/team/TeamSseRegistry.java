package com.server.contestControl.contestServer.sse.team;

import com.server.contestControl.shared.sse.SseEmitterRegistry;
import org.springframework.stereotype.Component;

/**
 * Registry for team SSE clients connected to /api/team/stream.
 *
 * Teams register under their own team id (targeted mode), which lets us both
 * broadcast to every connected team (contest-update, clarifications) via
 * {@link SseEmitterRegistry#broadcastToAllTargeted} and target a specific team
 * (submission verdicts) via {@link SseEmitterRegistry#publishTo} — all from
 * the same registry and the same connection.
 *
 * Marker subclass so it is a distinct Spring bean from ContestSseRegistry;
 * SseHeartbeatScheduler picks it up automatically via List&lt;SseEmitterRegistry&gt;.
 */
@Component
public class TeamSseRegistry extends SseEmitterRegistry {
}
