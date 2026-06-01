package com.server.contestControl.contestServer.prompt.dto;

import com.server.contestControl.contestServer.prompt.enums.PromptType;
import com.server.contestControl.contestServer.prompt.enums.PromptMode;
import com.server.contestControl.contestServer.prompt.enums.PromptVisibilityMode;
import com.server.contestControl.submissionServer.language.SupportedLanguageResponse;

import java.util.List;

public record PromptExportResponse(
        PromptType promptType,
        PromptMode promptMode,
        PromptVisibilityMode visibilityMode,
        SupportedLanguageResponse targetLanguage,
        String promptText,
        List<String> warnings,
        List<String> readinessWarnings
) {
}
