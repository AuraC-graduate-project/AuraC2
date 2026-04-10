package com.server.contestControl.contestServer.scheduler;

import com.server.contestControl.contestServer.service.ContestStatusSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that periodically synchronizes the persisted contest status
 * with the computed effective state.
 *
 * This scheduler acts as a fallback mechanism when an admin does not manually
 * update the contest status at the appropriate time.
 *
 * Scheduling behavior:
 * - Uses fixed delay, meaning the next run starts after the previous run completes
 *   and the configured delay has passed.
 * - Uses an initial delay to avoid triggering immediately during application startup.
 *
 * Configuration:
 * - contest.sync.enabled: Enables the scheduler when set to true (default: false)
 * - contest.sync.delay-ms: Delay between runs in milliseconds, measured after the previous run finishes (default: 30000)
 * - contest.sync.initial-delay-ms: Delay before the first scheduler run after application startup (default: 10000)
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "contest.sync.enabled", havingValue = "true", matchIfMissing = false)
public class ContestStatusSyncScheduler {

    private final ContestStatusSyncService syncService;

    /**
     * Scheduled task that runs with a configurable fixed delay.
     * Default delay is 30 seconds (30000 ms), with an initial startup delay
     * of 10 seconds (10000 ms).
     */
    @Scheduled(
            fixedDelayString = "${contest.sync.delay-ms:30000}",
            initialDelayString = "${contest.sync.initial-delay-ms:10000}"
    )
    public void syncContestStatuses() {
        log.debug("Contest status sync scheduler triggered");
        System.out.println("=== SCHEDULER TRIGGERED ===");
        
        try {
            int syncedCount = syncService.syncAllEligibleContests();
            
            if (syncedCount > 0) {
                log.info("Contest status sync completed: {} contest(s) updated", syncedCount);
            }
        } catch (Exception e) {
            log.info("Contest status sync scheduler encountered an error: {}", e.getMessage(), e);
        }
    }
}
