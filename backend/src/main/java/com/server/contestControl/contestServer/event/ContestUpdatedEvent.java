package com.server.contestControl.contestServer.event;

import com.server.contestControl.contestServer.dto.contest.ContestResponse;

public record ContestUpdatedEvent(Reason reason, ContestResponse snapshot) {

    public enum Reason {// listeners need to know what happened, not only the final state
        CREATED,
        MANUAL_START,
        MANUAL_PAUSE,
        MANUAL_RESUME,
        MANUAL_END,
        UPDATED,
        AUTO_START,
        AUTO_END
    }
}
