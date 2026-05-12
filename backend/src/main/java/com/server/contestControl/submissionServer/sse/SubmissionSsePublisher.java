package com.server.contestControl.submissionServer.sse;

import com.server.contestControl.shared.sse.SsePublisher;
import com.server.contestControl.submissionServer.entity.Submission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publishes submission status events to both the team and admin SSE registries.
 *
 * <p>The caller is responsible for invoking this method while the {@link Submission}
 * entity is fully loaded (i.e. inside the active transaction or after eager-fetching
 * the required associations). Once the event data is captured in a
 * {@link SubmissionStreamEvent} record the method is safe to call after commit
 * (e.g. from a {@code TransactionSynchronization.afterCommit()} callback).</p>
 *
 * <p>Routing rules:
 * <ul>
 *   <li>Admin registry — broadcast to every connected admin.</li>
 *   <li>Team registry — targeted by {@code submission.getUser().getId()} so only
 *       the submitting team's open streams receive the event.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubmissionSsePublisher {

    private static final String SSE_EVENT_NAME = "submission-update";

    private final SsePublisher ssePublisher;
    private final SubmissionSseRegistry submissionSseRegistry;
    private final AdminSubmissionSseRegistry adminSubmissionSseRegistry;

    /**
     * Builds a {@link SubmissionStreamEvent} from the provided entity and dispatches
     * it to all connected SSE clients. Must be called while the entity associations
     * (user, contest, problem) are accessible.
     */
    public void publish(SubmissionStreamEventType eventType, Submission submission) {
        SubmissionStreamEvent event = buildEvent(eventType, submission);
        dispatch(event);
    }

    /**
     * Dispatches a pre-built event. Use this overload when you have captured
     * the event data before a transaction committed and want to fire it afterward.
     */
    public void dispatch(SubmissionStreamEvent event) {
        log.debug(
                "[SubmissionSsePublisher] Publishing {} for submissionId={} verdict={}",
                event.eventType(), event.submissionId(), event.verdict()
        );

        // Broadcast to all connected admins.
        ssePublisher.publish(SSE_EVENT_NAME, event, adminSubmissionSseRegistry);

        // Send only to the submitting team's connected streams.
        ssePublisher.publishTo(event.userId(), SSE_EVENT_NAME, event, submissionSseRegistry);
    }

    /**
     * Captures all needed fields from the entity into an immutable record.
     * Call this inside the transaction so lazy-loaded associations are available.
     */
    public SubmissionStreamEvent buildEvent(SubmissionStreamEventType eventType, Submission submission) {
        return new SubmissionStreamEvent(
                eventType,
                submission.getId(),
                submission.getContest().getId(),
                submission.getProblem().getId(),
                submission.getUser().getId(),
                submission.getUser().getUsername(),
                submission.getVerdict(),
                submission.getJudgeRunId(),
                submission.getExecutionTime(),
                submission.getMemoryUsage(),
                submission.getCreatedAt(),
                LocalDateTime.now()
        );
    }
}
