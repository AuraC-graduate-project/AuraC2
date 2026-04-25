package com.server.contestControl.contestServer.dto.clarification;

import com.server.contestControl.contestServer.entity.Clarification;
import com.server.contestControl.contestServer.enums.ClarificationStatus;
import com.server.contestControl.contestServer.enums.ClarificationType;
import com.server.contestControl.contestServer.enums.StandardReply;

import java.time.LocalDateTime;

public record ClarificationResponse(
        Long id,
        Long contestId,
        Long problemId,
        String problemTitle,
        String question,
        StandardReply standardReply,
        String reply,
        ClarificationStatus status,
        ClarificationType replyType,
        LocalDateTime createdAt,
        LocalDateTime repliedAt,
        String username
) {
    public static ClarificationResponse fromEntity(Clarification c) {
        return new ClarificationResponse(
                c.getId(),
                c.getContest().getId(),
                c.getProblem() != null ? c.getProblem().getId() : null,
                c.getProblem() != null ? c.getProblem().getTitle() : null,
                c.getQuestion(),
                c.getStandardReply(),
                c.getReply(),
                c.getStatus(),
                c.getReplyType(),
                c.getCreatedAt(),
                c.getRepliedAt(),
                c.getUser().getUsername()
        );
    }
}
