package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.contestServer.repository.ContestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


/**
 * Coordinates auto sync and publishes auto events.
 * It does not directly change the contest status.
 * It finds the candidate and delegates the actual update to ContestStatusSyncExecutor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContestStatusSyncService {

    private final ContestRepository contestRepository;
    private final ContestStatusSyncExecutor syncExecutor; // injected proxy; calls go through AOP
    private final ContestService contestService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Sync all eligible contests. Returns count of contests that were updated.
     *
     * @Transactional(readOnly = true) serves three purposes:
     *   1. Opens a proper persistence context for the duration of this method,
     *      keeping fetched entities managed (not detached) throughout the read phase.
     *   2. Signals to the JPA provider (Hibernate) that no dirty-checking or
     *      flush is needed — a meaningful performance hint on larger result sets.
     *   3. On some databases/drivers, enables read-only optimizations at the
     *      connection level (e.g., skipping redo log writes).
     *
     * Note: syncExecutor.syncContestStatus() uses REQUIRES_NEW, which suspends
     * this read-only transaction and opens a separate read-write one. The two
     * transactions are fully isolated — this is correct and intentional.
     */
    @Transactional(readOnly = true)
    public int syncAllEligibleContests() {
        Instant now = Instant.now();

        // UPCOMING contests are auto-started when their scheduledStart time passes.
        // RUNNING contests are auto-ended when their pause-aware effectiveEndTime passes.
        List<Long> contestIds = contestRepository.findSyncCandidates(
                        List.of(ContestStatus.UPCOMING, ContestStatus.RUNNING)
                ).stream()
                .map(Contest::getId)
                .toList();

        if (contestIds.isEmpty()) {
            log.debug("No contests requiring sync at {}", now);
            return 0;
        }

        int syncedCount = 0;
        for (Long contestId : contestIds) {
            try {
                // Call through Spring's proxy on syncExecutor so REQUIRES_NEW is applied.
                Optional<ContestStatus> transitionedTo = syncExecutor.syncContestStatus(contestId, now);
                if (transitionedTo.isEmpty()) {
                    continue;
                }
                publishAutoTransitionEvent(contestId, transitionedTo.get());
                syncedCount++;
            } catch (Exception e) {
                log.error("Failed to sync contest {}: {}", contestId, e.getMessage(), e);
            }
        }
        return syncedCount;
    }

    private void publishAutoTransitionEvent(Long contestId, ContestStatus newStatus) {
        Optional<ContestResponse> snapshot = contestService.buildResponseForId(contestId); // REQUIRES_NEW fresh read method : Because after auto transition, we need response from the latest committed DB state.
        if (snapshot.isEmpty()) {
            log.warn("Auto-transitioned contest {} disappeared before snapshot build; skipping SSE event", contestId);
            return;
        }
        ContestUpdatedEvent.Reason reason = switch (newStatus) {
            case RUNNING -> ContestUpdatedEvent.Reason.AUTO_START;
            case ENDED -> ContestUpdatedEvent.Reason.AUTO_END;
            default -> null;
        };
        if (reason == null) {
            log.debug("No SSE reason mapping for auto-transition to {}; skipping", newStatus);
            return;
        }

        eventPublisher.publishEvent(new ContestUpdatedEvent(reason, snapshot.get()));
    }
}
