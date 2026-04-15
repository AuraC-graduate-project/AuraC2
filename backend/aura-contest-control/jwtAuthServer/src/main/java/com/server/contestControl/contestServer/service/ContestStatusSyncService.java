package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.repository.ContestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContestStatusSyncService {

    private final ContestRepository contestRepository;
    private final ContestStatusSyncExecutor syncExecutor; // ✅ injected proxy — calls go through AOP

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
        Optional<Long> contestId = contestRepository.findSyncCandidates(
                        List.of(ContestStatus.UPCOMING, ContestStatus.RUNNING)
                ).stream()
                .findFirst()
                .map(Contest::getId);

        if (contestId.isEmpty()) {
            log.debug("No contests requiring sync at {}", now);
            return 0;
        }

        try {
            // ✅ Call goes through Spring's proxy on syncExecutor — REQUIRES_NEW is applied
            return syncExecutor.syncContestStatus(contestId.get(), now).isPresent() ? 1 : 0;
        } catch (Exception e) {
            log.error("Failed to sync contest {}: {}", contestId.get(), e.getMessage(), e);
            return 0;
        }
    }
}