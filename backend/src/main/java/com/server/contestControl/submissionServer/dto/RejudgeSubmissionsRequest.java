package com.server.contestControl.submissionServer.dto;

import java.util.List;

public record RejudgeSubmissionsRequest(
        List<Long> submissionIds
) {
}
