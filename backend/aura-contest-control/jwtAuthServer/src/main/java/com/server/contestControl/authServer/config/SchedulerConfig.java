package com.server.contestControl.authServer.config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Registers a single ThreadPoolTaskScheduler bean named "taskScheduler".
 *
 *
 * Pool size 3:
 *   - 1 thread  for the @Scheduled fallback
 *   - 1 thread  for a parked auto-start one-shot task
 *   - 1 thread  for brief overlap when auto-start fires and chains auto-end
 */
@Configuration
@EnableScheduling
@Slf4j
public class SchedulerConfig {

    @Bean(name = "taskScheduler")
    // This is the method that builds the scheduler object and returns it.
    public ThreadPoolTaskScheduler taskScheduler() {
        // This object is responsible for: waiting until a future time, then running a task on a background thread.
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // Allow up to 3 scheduler threads to run concurrently, which is enough for our current needs and allows some breathing room for future schedulers or brief overlaps.
        scheduler.setPoolSize(3);
        // Naming for debugging
        scheduler.setThreadNamePrefix("contest-sched-");
        scheduler.setErrorHandler(t ->
                log.error("[contest-scheduler] Unhandled error in scheduled task", t));
        // Wait for in-flight tasks to finish before JVM exits (prevents a partial DB write on restart)
        // When the app is shutting down,
        // this setting tells the scheduler to wait for any currently running scheduled tasks to complete before it shuts down.
        // This is important to prevent abrupt termination of tasks that might be in the middle of critical operations,
        // such as database updates or external API calls. By waiting for tasks to complete,
        // we can ensure a cleaner shutdown process and reduce the risk of data corruption or inconsistent states.
        // The awaitTerminationSeconds setting specifies how long the scheduler should wait for tasks to finish before it forces a shutdown,
        // providing a grace period for tasks to complete their work.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}