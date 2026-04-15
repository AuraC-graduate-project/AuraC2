package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.repository.ContestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Executes a single contest status sync operation within its own isolated transaction.
 *
 * Extracted into a separate bean so that Spring's AOP proxy intercepts the call,
 * ensuring REQUIRES_NEW propagation is actually applied.
 * (Self-invocation within the same bean bypasses the proxy entirely.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContestStatusSyncExecutor {

    private final ContestRepository contestRepository;
    private final ContestLifecycleService contestLifecycleService;

    /**
     * Synchronize a single contest's persisted status with its effective state.
     * Runs in its own brand-new transaction, isolated from the caller's transaction.
     *
     * @param contestId The contest ID to sync
     * @param now       The current time (passed in for testability)
     * @return The new status if a transition was made, empty otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ContestStatus> syncContestStatus(Long contestId, Instant now) {
        // ✅ Acquires SELECT ... FOR UPDATE — concurrent callers block here
        Contest contest = contestRepository.findByIdWithLock(contestId).orElse(null);

        if (contest == null) {
            log.debug("Contest {} no longer exists, skipping sync", contestId);
            return Optional.empty();
        }

        ContestStatus persistedStatus = contest.getStatus();
        ContestStatus effectiveState = contestLifecycleService.resolveEffectiveState(contest, now);

        if (persistedStatus == effectiveState) {
            log.debug("Contest {} already in sync: status={}", contest.getId(), persistedStatus);
            return Optional.empty();
        }

        if (!isAllowedAutoTransition(persistedStatus, effectiveState)) {
            log.info("Contest {} requires manual intervention: {} -> {} is not auto-allowed",
                    contest.getId(), persistedStatus, effectiveState);
            return Optional.empty();
        }

        if (Boolean.TRUE.equals(contest.getStatusLocked())) {
            log.info("Contest {} was locked during sync, skipping", contest.getId());
            return Optional.empty();
        }

        // Bookkeeping for auto-start: stamp actualStartTime exactly as a manual Start would.
        if (persistedStatus == ContestStatus.UPCOMING && effectiveState == ContestStatus.RUNNING) {
            Instant scheduledStart = contest.getStartTime(); // set the time as the user input not when schedule runs, otherwise it will be different from the start time shown on the UI and cause confusion
            contest.setActualStartTime(scheduledStart != null ? scheduledStart : now);
            log.info(
                    "Auto-start stamped actualStartTime | contestId={} | startTime={} | actualStartTime={}",
                    contest.getId(),
                    contest.getStartTime(),
                    contest.getActualStartTime()
            );
        }

        contest.setStatus(effectiveState);
        contestRepository.save(contest);

        log.info("Auto-synced contest {} status: {} -> {} (effective state based on timing)",
                contest.getId(), persistedStatus, effectiveState);

        return Optional.of(effectiveState);
    }

    private boolean isAllowedAutoTransition(ContestStatus from, ContestStatus to) {
        return switch (from) {
            case UPCOMING -> to == ContestStatus.RUNNING;  // auto-start when scheduled time passes
            case RUNNING -> to == ContestStatus.ENDED;     // auto-end when effective end time passes
            default -> false;
        };
    }
}