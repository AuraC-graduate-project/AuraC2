package com.server.contestControl.contestServer.runlab.dto;

import com.server.contestControl.submissionServer.enums.Verdict;

public record AdminRunLabResponse(
        boolean scoring,
        Verdict verdict,
        Integer statusId,
        String statusDescription,
        String stdout,
        String stderr,
        String compileOutput,
        Integer runtimeMillis,
        Integer memoryKb
) {
}
