package com.server.contestControl.contestServer.prompt.dto;

import com.server.contestControl.contestServer.prompt.PromptType;
import com.server.contestControl.contestServer.prompt.PromptVisibilityMode;
import com.server.contestControl.submissionServer.language.SupportedLanguageResponse;

import java.util.List;

public record PromptExportResponse(
        PromptType promptType,
        PromptVisibilityMode visibilityMode,
        SupportedLanguageResponse targetLanguage,
        String promptText,
        List<String> warnings,
        List<String> readinessWarnings
) {
}
