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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * The main reason is scheduler effective transitions.
 * This file handles exact-time automatic transitions.
 * It schedules one-shot future tasks:
 *      UPCOMING contest → run task at startTime
 *      RUNNING contest  → run task at effectiveEndTime
 *
 * It wakes up at the right time and tells the existing syncAllEligibleContests():
 *      Check contests now. Any contest that should transition, transition it.
 *
 * The scheduler says:
 *      Time arrived.
 * The sync service says:
 *      Okay, I will check DB and auto-start/auto-end if needed.
 *
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContestTransitionScheduler {

    private final TaskScheduler             taskScheduler; // Schedules future tasks. SchedulerConfig.taskScheduler() provides the actual TaskScheduler bean
    private final ContestStatusSyncService  syncService;
    private final ContestRepository         contestRepository;
    private final ContestLifecycleService   lifecycleService;

    /** contestId → pending ScheduledFuture
     * contest 5 → task to start at 10:00 */
    private final Map<Long, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();// A regular HashMap is not thread-safe for concurrent access, ConcurrentHM makes individual map operations thread-safe


    /**
     * Cancel the old task for this contest.
     * Calculate the next automatic transition time.
     * Schedule one new task.
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

        // This schedules the task.
        // The scheduler stores:
        //      At 10:00, run:
        //      triggerSyncAndChain(7)
        // At exactly 10:00, one scheduler thread wakes up and executes the stored lambda:
        ScheduledFuture<?> nextTask = taskScheduler.schedule(() -> triggerSyncAndChain(id), targetTime); // The taskScheduler here is the bean from SchedulerConfig.
        if (nextTask != null) pendingTasks.put(id, nextTask); // Save it
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

    /**
     * Reacts after contest lifecycle changes by keeping the scheduled auto-transition
     * task in sync with the latest DB state. It schedules the next start/end task
     * for active lifecycle states, and cancels pending tasks when the clock is paused
     * or the contest has ended.
     */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
     * This runs when the scheduled time arrives.
     *
     * Delegates entirely to the existing syncAllEligibleContests() so the
     * REQUIRES_NEW transaction, row lock, status update, and SSE broadcast
     * all happen exactly as they do today.
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