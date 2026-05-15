package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ScoreboardVersionService {

    private final Map<Key, AtomicLong> versions = new ConcurrentHashMap<>();

    public long current(Long contestId, ScoreboardAudience audience) {
        return versions.computeIfAbsent(new Key(contestId, audience), ignored -> new AtomicLong(0L)).get();
    }

    public long next(Long contestId, ScoreboardAudience audience) {
        return versions.computeIfAbsent(new Key(contestId, audience), ignored -> new AtomicLong(0L)).incrementAndGet();
    }

    private record Key(Long contestId, ScoreboardAudience audience) {
    }
}
