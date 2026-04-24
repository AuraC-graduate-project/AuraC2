package com.server.contestControl.contestServer.scheduler;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.service.ContestLifecycleService;
import com.server.contestControl.contestServer.service.ContestStatusSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Parks a one-shot task at the exact Instant a contest should auto-transition.
 *
 * ── How it fits into the existing system ─────────────────────────────────────
 *
 *   ContestService calls reschedule() / cancelPending() after every mutation.
 *   When the task fires it calls the existing syncAllEligibleContests(), so
 *   all the existing REQUIRES_NEW transaction, locking, broadcast, and
 *   @TransactionalEventListener logic runs exactly as before — nothing in
 *   the sync pipeline is changed.
 *
 *   ContestStatusSyncScheduler (the 30s fallback) still runs untouched as
 *   a safety net for restarts, clock skew, or transient failures.
 *
 * ── Circular dependency ───────────────────────────────────────────────────────
 *
 *   ContestService → ContestTransitionScheduler
 *                         → ContestStatusSyncService
 *                               → ContestService   ← cycle
 *
 *   Broken by @Lazy on the injection site in ContestService (see that file).
 *   Spring injects a proxy there and resolves the real bean on first use,
 *   after the context is fully started. This is the pattern Spring 6's AOT
 *   docs recommend for unavoidable cycles.
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 *
 *   reschedule() and cancelPending() are synchronized. ConcurrentHashMap alone
 *   is not enough: two threads could interleave their remove + put steps and
 *   leak a ScheduledFuture that fires at the wrong time.
 *   The one-shot tasks themselves run on the pool thread and never hold the
 *   monitor, so there is no deadlock risk.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContestTransitionScheduler {

    private final TaskScheduler             taskScheduler;
    private final ContestStatusSyncService  syncService;
    private final ContestRepository         contestRepository;
    private final ContestLifecycleService   lifecycleService;

    /** contestId → pending ScheduledFuture
     * contest 5 → task to start at 10:00 */
    private final Map<Long, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();// A regular HashMap is not thread-safe for concurrent access, ConcurrentHM makes individual map operations thread-safe

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * If two threads interleave badly, you can still get bugs like:
     *      thread A removes old task
     *      thread B also schedules
     *      thread A schedules again
     *      wrong task ends up stored
     *      stale task leaks
     * So synchronized is there to protect the higher-level invariant:
     */
    public synchronized void reschedule(Contest contest) {
        Long id = contest.getId();
        // cancel old task if exists, to prevent multiple pending tasks for the same contest and to ensure that any change in the schedule (e.g. pause-aware end time) is respected
        cancelPending(id);

        // Calculate next time to auto-transition based on the current status and schedule a one-shot task for that exact time
        Instant targetTime = resolveNextTransitionInstant(contest);
        // If it PAUSED or ENDED
        if (targetTime == null) {
            log.debug("No auto-transition applicable for contest {} in status {}", id, contest.getStatus());
            return;
        }

        Instant now = Instant.now();
        // If time already passed
        if (!targetTime.isAfter(now)) {
            // Target already passed (e.g. server restarted mid-contest).
            // Schedule for now so it runs immediately but off the caller's thread.
            log.info("Contest {} transition time {} already past — scheduling immediate catch-up", id, targetTime);
            ScheduledFuture<?> f = taskScheduler.schedule(() -> triggerSyncAndChain(id), Instant.now());
            if (f != null) pendingTasks.put(id, f);
            return;
        }

        log.info("Exact-time auto-transition for contest {} at {} (T-{}s)",
                id, targetTime, Duration.between(now, targetTime).getSeconds());

        ScheduledFuture<?> f = taskScheduler.schedule(() -> triggerSyncAndChain(id), targetTime);
        if (f != null) pendingTasks.put(id, f);
    }

    /**
     * Cancel any pending task for this contest without rescheduling.
     *
     * Call after: manual pause (clock frozen), manual end, jury-override end.
     *
     * @param contestId the contest whose pending task should be dropped
     */
    public synchronized void cancelPending(Long contestId) {
        ScheduledFuture<?> existing = pendingTasks.remove(contestId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false); // false = don't interrupt if currently running
            log.debug("Cancelled pending auto-transition for contest {}", contestId);
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onContestUpdated(ContestUpdatedEvent event) {
        Long contestId = event.snapshot().getId();

        switch (event.reason()) {
            case CREATED, MANUAL_START, MANUAL_RESUME, AUTO_START ->
                    contestRepository.findById(contestId).ifPresent(this::reschedule);

            case MANUAL_PAUSE, MANUAL_END, AUTO_END ->
                    cancelPending(contestId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Startup recovery
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Re-registers transitions from the DB after a restart.
     * In-memory ScheduledFutures don't survive restarts — this rebuilds them.
     *
     * ApplicationReadyEvent fires after the full context (including lazy proxies)
     * is initialised and the embedded server is accepting requests.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() { // runs when app starts
        log.info("[ContestTransitionScheduler] Restoring scheduled transitions from DB...");
        contestRepository.findAll().forEach(contest -> {
            try {
                reschedule(contest);
            } catch (Exception e) {
                log.error("Failed to restore transition for contest {}: {}", contest.getId(), e.getMessage(), e);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs on a scheduler pool thread at the scheduled Instant.
     *
     * Delegates entirely to the existing syncAllEligibleContests() so the
     * REQUIRES_NEW transaction, row lock, status update, and SSE broadcast
     * all happen exactly as they do today.
     *
     * After a successful transition, fetches the freshly committed entity
     * and chains the next task (UPCOMING→RUNNING triggers scheduling of RUNNING→ENDED).
     */
    private void triggerSyncAndChain(Long contestId) {
        try {
            syncService.syncAllEligibleContests();

        } catch (Exception e) {
            log.error("Exact-time transition task failed for contest {}: {}", contestId, e.getMessage(), e);
        }
    }

    /**
     * Returns the Instant at which the contest should next auto-transition, or null.
     *
     *   UPCOMING → startTime                    (auto-start)
     *   RUNNING  → pause-aware effectiveEndTime (auto-end)
     *   PAUSED   → null  (clock frozen; re-evaluated on resume)
     *   ENDED    → null  (terminal)
     */
    private Instant resolveNextTransitionInstant(Contest contest) {
        return switch (contest.getStatus()) {
            case UPCOMING -> contest.getStartTime();
            case RUNNING  -> lifecycleService.resolveEffectiveEndTime(contest, Instant.now());
            default       -> null;
        };
    }
}