package com.server.contestControl.contestServer.sse.clarification;

import com.server.contestControl.shared.sse.SseEmitterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ClarificationTeamSseRegistry extends SseEmitterRegistry {

    private final AtomicLong nextAudienceId = new AtomicLong(1);
    private final ConcurrentMap<ContestTeamKey, Long> audienceIdByTarget = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Set<Long>> contestAudienceIds = new ConcurrentHashMap<>();

    public void register(Long contestId, Long teamUserId, SseEmitter emitter) {
        super.register(resolveAudienceId(contestId, teamUserId), emitter);
    }

    public Long resolveAudienceId(Long contestId, Long teamUserId) {
        ContestTeamKey key = new ContestTeamKey(contestId, teamUserId);

        return audienceIdByTarget.computeIfAbsent(key, ignored -> {
            Long audienceId = nextAudienceId.getAndIncrement();
            contestAudienceIds
                    .computeIfAbsent(contestId, ignoredContestId -> ConcurrentHashMap.newKeySet())
                    .add(audienceId);
            return audienceId;
        });
    }

    public Set<Long> resolveContestAudienceIds(Long contestId) {
        Set<Long> audienceIds = contestAudienceIds.get(contestId);
        return audienceIds == null ? Collections.emptySet() : Set.copyOf(audienceIds);
    }

    private record ContestTeamKey(Long contestId, Long teamUserId) {
    }
}
