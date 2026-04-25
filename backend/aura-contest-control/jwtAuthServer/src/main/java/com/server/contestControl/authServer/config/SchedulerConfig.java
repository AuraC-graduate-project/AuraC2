package com.server.contestControl.authServer.config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Provides the shared TaskScheduler used by:
 *  - @Scheduled jobs such as SSE heartbeat/fallback sync
 *  - exact-time one-shot contest transitions such as auto-start and auto-end
 *
 * A small thread pool allows scheduled jobs to overlap safely, and graceful
 * shutdown lets in-flight transition tasks finish before the JVM exits.
 */
@Configuration
@EnableScheduling
@Slf4j
public class SchedulerConfig {

    // SchedulerConfig creates taskScheduler
    //        ↓
    // ContestTransitionScheduler receives it
    //        ↓
    // ContestTransitionScheduler uses it to schedule auto-start/auto-end
    @Bean(name = "taskScheduler")
    // This is the method that builds the scheduler object and returns it.
    public ThreadPoolTaskScheduler taskScheduler() {
        // This object is responsible for: waiting until a future time, then running a task on a background thread.
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // Pool size 3:
        //  - allows @Scheduled jobs and exact-time one-shot tasks to run without blocking each other
        //  - gives room for brief overlap between heartbeat/fallback sync and auto transitions
        scheduler.setPoolSize(3);
        // Naming for debugging
        scheduler.setThreadNamePrefix("contest-sched-");
        scheduler.setErrorHandler(t ->
                log.error("[contest-scheduler] Unhandled error in scheduled task", t));
        //When application shuts down,
        //do not immediately kill running scheduled tasks.
        //Wait up to 30 seconds for them to finish.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler; // This returns the configured object to Spring.
    }
}