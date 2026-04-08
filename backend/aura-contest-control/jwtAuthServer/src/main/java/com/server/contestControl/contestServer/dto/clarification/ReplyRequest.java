package com.server.contestControl.contestServer.dto.clarification;

import com.server.contestControl.contestServer.enums.ClarificationType;
import com.server.contestControl.contestServer.enums.StandardReply;

public record ReplyRequest(
        StandardReply standardReply,
        String reply,
        ClarificationType replyType
) {
}
