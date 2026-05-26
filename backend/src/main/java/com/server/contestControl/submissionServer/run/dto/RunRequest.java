package com.server.contestControl.submissionServer.run.dto;

import java.util.List;

public record RunRequest(
        Integer languageId,
        String language,
        String sourceCode,
        String code,
        Boolean includePublicSamples,
        List<Long> customTestCaseIds,
        List<InlineCustomTestCaseRequest> inlineCustomTests
) {
}
