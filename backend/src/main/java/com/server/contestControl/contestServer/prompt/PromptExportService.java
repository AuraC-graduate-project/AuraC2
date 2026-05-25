package com.server.contestControl.contestServer.prompt;

import com.server.contestControl.contestServer.prompt.dto.PromptExportRequest;
import com.server.contestControl.contestServer.prompt.dto.PromptExportResponse;
import com.server.contestControl.submissionServer.language.SupportedLanguage;
import com.server.contestControl.submissionServer.language.SupportedLanguageResponse;
import com.server.contestControl.submissionServer.language.SupportedLanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromptExportService {

    private final SupportedLanguageService supportedLanguageService;
    private final PromptVisibilityPolicy promptVisibilityPolicy;
    private final PromptContextBuilder promptContextBuilder;
    private final PromptTemplateRenderer promptTemplateRenderer;

    @Transactional(readOnly = true)
    public PromptExportResponse preview(Long problemId, PromptExportRequest request) {
        SupportedLanguage targetLanguage = supportedLanguageService.requireByValue(request.getTargetLanguage());
        promptVisibilityPolicy.validate(request, targetLanguage);

        PromptContext context = promptContextBuilder.build(problemId, targetLanguage);
        List<String> readinessWarnings = promptContextBuilder.readinessWarnings(context, request);
        List<String> warnings = safetyWarnings(request);
        String promptText = promptTemplateRenderer.render(context, request, readinessWarnings);

        return new PromptExportResponse(
                request.getPromptType(),
                request.getVisibilityMode(),
                SupportedLanguageResponse.from(targetLanguage),
                promptText,
                warnings,
                readinessWarnings
        );
    }

    private List<String> safetyWarnings(PromptExportRequest request) {
        List<String> warnings = new ArrayList<>();
        warnings.add("AuraC2 only generates prompt text. It does not call AI services.");
        warnings.add("AI-generated code must be reviewed, compiled, tested, and verified inside AuraC2 before use.");
        if (request.getVisibilityMode() == PromptVisibilityMode.ADMIN_FULL_MODE) {
            warnings.add("ADMIN_FULL_MODE may include contest-sensitive material selected by the admin.");
        }
        return warnings;
    }
}
