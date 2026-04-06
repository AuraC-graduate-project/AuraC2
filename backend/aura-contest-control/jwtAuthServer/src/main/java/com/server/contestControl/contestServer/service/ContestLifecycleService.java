package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class ContestLifecycleService {

    public ContestStatus resolveEffectiveState(Contest contest, Instant now) {
        ContestStatus persistedStatus = contest.getStatus();

        System.out.println("====================================");
        System.out.println("[resolveEffectiveState] Contest ID: " + contest.getId());
        System.out.println("Now: " + now);
        System.out.println("Persisted Status: " + persistedStatus);


        if (persistedStatus == ContestStatus.ENDED) {
            System.out.println("→ Returning ENDED (persisted status)");
            return ContestStatus.ENDED;
        }

        if (persistedStatus == ContestStatus.PAUSED) {
            System.out.println("→ Returning PAUSED (persisted status)");
            return ContestStatus.PAUSED;
        }
        if (persistedStatus == ContestStatus.RUNNING) {
            Instant effectiveEndTime = resolveEffectiveEndTime(contest, now);
            if (effectiveEndTime != null && !now.isBefore(effectiveEndTime)) {
                return ContestStatus.ENDED;
            }
            return ContestStatus.RUNNING;
        }

        Instant startTime = contest.getStartTime();
        Instant effectiveEndTime = resolveEffectiveEndTime(contest, now);
        System.out.println("Start Time: " + startTime);
        System.out.println("Effective End Time: " + effectiveEndTime);

        if (startTime != null && now.isBefore(startTime)) {
            System.out.println("→ Returning UPCOMING (now is before startTime)");
            return ContestStatus.UPCOMING;
        }

        if (effectiveEndTime != null && !now.isBefore(effectiveEndTime)) {
            System.out.println("→ Returning ENDED (now >= effectiveEndTime)");
            return ContestStatus.ENDED;
        }

        System.out.println("→ Returning RUNNING (default case)");
        return ContestStatus.RUNNING;
    }

    /**
     * Placeholder for future pause-aware lifecycle math.
     * For now, effective end equals scheduled end.
     */
    public Instant resolveEffectiveEndTime(Contest contest, Instant now) {
        return contest.getEndTime();
    }

    public Instant resolveEffectiveScoreboardFreezeTime(Contest contest, Instant now) {
        return contest.getScoreboardFreezeTime();
    }

    public boolean isScoreboardFrozen(Contest contest, Instant now) {
        ContestStatus effectiveState = resolveEffectiveState(contest, now);
        if (effectiveState != ContestStatus.RUNNING) {
            return false;
        }

        Instant freezeTime = resolveEffectiveScoreboardFreezeTime(contest, now);
        Instant effectiveEndTime = resolveEffectiveEndTime(contest, now);
        if (freezeTime == null || effectiveEndTime == null) {
            return false;
        }

        return !now.isBefore(freezeTime) && now.isBefore(effectiveEndTime);
    }
}
