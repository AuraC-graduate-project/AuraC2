package com.server.contestControl.contestServer.moderation.dto;

import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;

public record ModerationActionRequest(
        ModerationActionType actionType,
        String reason
) {
}
