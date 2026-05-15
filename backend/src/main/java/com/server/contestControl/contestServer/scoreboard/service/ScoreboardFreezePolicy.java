package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
import com.server.contestControl.contestServer.service.ContestLifecycleService;
import com.server.contestControl.submissionServer.entity.Submission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ScoreboardFreezePolicy {

    private final ContestLifecycleService contestLifecycleService;

    public Instant freezeTime(Contest contest, Instant now) {
        return contestLifecycleService.resolveEffectiveScoreboardFreezeTime(contest, now);
    }

    public boolean isFrozenForAudience(
            Contest contest,
            ScoreboardAudience audience,
            RevealStatus revealStatus,
            Instant now
    ) {
        if (audience == ScoreboardAudience.ADMIN) {
            return false;
        }

        Instant freezeTime = freezeTime(contest, now);
        if (freezeTime == null) {
            return false;
        }

        ContestStatus effectiveState = contestLifecycleService.resolveEffectiveState(contest, now);
        if (effectiveState == ContestStatus.RUNNING || effectiveState == ContestStatus.PAUSED) {
            return contestLifecycleService.isScoreboardFrozen(contest, now);
        }

        return effectiveState == ContestStatus.ENDED && revealStatus != RevealStatus.COMPLETED;
    }

    public boolean isBeforeFreeze(Submission submission, Instant freezeTime) {
        if (freezeTime == null || submission.getCreatedAt() == null) {
            return true;
        }

        Instant submittedAt = submission.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .toInstant();
        return submittedAt.isBefore(freezeTime);
    }
}
