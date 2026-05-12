package com.server.contestControl.contestServer.sse.clarification;

import com.server.contestControl.contestServer.entity.Clarification;
import com.server.contestControl.contestServer.event.ClarificationCreatedEvent;
import com.server.contestControl.contestServer.event.ClarificationRepliedEvent;
import com.server.contestControl.contestServer.enums.ClarificationStatus;
import com.server.contestControl.contestServer.enums.ClarificationType;
import com.server.contestControl.contestServer.repository.ClarificationRepository;
import com.server.contestControl.shared.sse.SsePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClarificationSseAdapter {

    private static final String EVENT_CREATED = "clarification-created";
    private static final String EVENT_REPLIED = "clarification-replied";
    private static final String EVENT_PUBLIC_ANSWERED = "clarification-public-answered";

    private final ClarificationRepository clarificationRepository;
    private final SsePublisher ssePublisher;
    private final ClarificationSseRegistry clarificationSseRegistry;
    private final ClarificationTeamSseRegistry clarificationTeamSseRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClarificationCreated(ClarificationCreatedEvent event) {
        log.debug("[ClarificationSseAdapter] Handling CLARIFICATION_CREATED: id={}", event.clarificationId());

        Clarification clarification = clarificationRepository.findById(event.clarificationId())
                .orElse(null);

        if (clarification == null) {
            log.warn("[ClarificationSseAdapter] Clarification not found: {}", event.clarificationId());
            return;
        }

        ClarificationStreamEvent streamEvent = new ClarificationStreamEvent(
                ClarificationStreamEventType.CLARIFICATION_CREATED,
                clarification.getContest().getId(),
                clarification.getId(),
                clarification.getProblem() != null ? clarification.getProblem().getId() : null,
                clarification.getUser().getId(),
                clarification.getStatus(),
                clarification.getReplyType(),
                LocalDateTime.now()
        );

        // Send to admin stream for this contest
        ssePublisher.publishTo(
                clarification.getContest().getId(),
                EVENT_CREATED,
                streamEvent,
                clarificationSseRegistry
        );

        // Send to asking team
        Long teamAudienceId = clarificationTeamSseRegistry.resolveAudienceId(
                clarification.getContest().getId(),
                clarification.getUser().getId()
        );
        ssePublisher.publishTo(
                teamAudienceId,
                EVENT_CREATED,
                streamEvent,
                clarificationTeamSseRegistry
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClarificationReplied(ClarificationRepliedEvent event) {
        log.debug("[ClarificationSseAdapter] Handling CLARIFICATION_REPLIED: id={}", event.clarificationId());

        Clarification clarification = clarificationRepository.findById(event.clarificationId())
                .orElse(null);

        if (clarification == null) {
            log.warn("[ClarificationSseAdapter] Clarification not found: {}", event.clarificationId());
            return;
        }

        ClarificationStreamEvent streamEvent = new ClarificationStreamEvent(
                ClarificationStreamEventType.CLARIFICATION_REPLIED,
                clarification.getContest().getId(),
                clarification.getId(),
                clarification.getProblem() != null ? clarification.getProblem().getId() : null,
                clarification.getUser().getId(),
                clarification.getStatus(),
                clarification.getReplyType(),
                LocalDateTime.now()
        );

        // Always send to admin stream
        ssePublisher.publishTo(
                clarification.getContest().getId(),
                EVENT_REPLIED,
                streamEvent,
                clarificationSseRegistry
        );

        // Send to asking team
        Long teamAudienceId = clarificationTeamSseRegistry.resolveAudienceId(
                clarification.getContest().getId(),
                clarification.getUser().getId()
        );
        ssePublisher.publishTo(
                teamAudienceId,
                EVENT_REPLIED,
                streamEvent,
                clarificationTeamSseRegistry
        );

        // If PUBLIC, also broadcast to all teams in the contest
        if (clarification.getReplyType() == ClarificationType.PUBLIC
                && clarification.getStatus() == ClarificationStatus.ANSWERED) {

            ClarificationStreamEvent publicEvent = new ClarificationStreamEvent(
                    ClarificationStreamEventType.CLARIFICATION_PUBLIC_ANSWERED,
                    clarification.getContest().getId(),
                    clarification.getId(),
                    clarification.getProblem() != null ? clarification.getProblem().getId() : null,
                    clarification.getUser().getId(),
                    clarification.getStatus(),
                    clarification.getReplyType(),
                    LocalDateTime.now()
            );

            for (Long audienceId : clarificationTeamSseRegistry.resolveContestAudienceIds(clarification.getContest().getId())) {
                ssePublisher.publishTo(
                        audienceId,
                        EVENT_PUBLIC_ANSWERED,
                        publicEvent,
                        clarificationTeamSseRegistry
                );
            }
        }
    }
}
