package com.server.contestControl.submissionServer.run.dto;

import java.util.List;

public record RunResponse(
        String compileStatus,
        String compileOutput,
        boolean scoring,
        int publicSampleCount,
        int customTestCount,
        List<RunCaseResult> results
) {
}
