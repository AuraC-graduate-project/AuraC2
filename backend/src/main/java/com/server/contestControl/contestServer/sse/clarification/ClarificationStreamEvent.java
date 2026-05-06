package com.server.contestControl.contestServer.sse.clarification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.server.contestControl.contestServer.enums.ClarificationType;
import com.server.contestControl.contestServer.enums.ClarificationStatus;

import java.time.LocalDateTime;

public record ClarificationStreamEvent(
        @JsonProperty("type") ClarificationStreamEventType type,
        @JsonProperty("contestId") Long contestId,
        @JsonProperty("clarificationId") Long clarificationId,
        @JsonProperty("problemId") Long problemId,
        @JsonProperty("teamUserId") Long teamUserId,
        @JsonProperty("status") ClarificationStatus status,
        @JsonProperty("replyType") ClarificationType replyType,
        @JsonProperty("occurredAt") LocalDateTime occurredAt
) {}
