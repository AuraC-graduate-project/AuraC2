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
        PromptContext context = promptContextBuilder.build(problemId, targetLanguage);
        PromptExportRequest effectiveRequest = effectiveRequest(request, context);
        promptVisibilityPolicy.validate(effectiveRequest, targetLanguage);

        List<String> readinessWarnings = promptContextBuilder.readinessWarnings(context, effectiveRequest);
        List<String> warnings = safetyWarnings(effectiveRequest);
        String promptText = promptTemplateRenderer.render(context, effectiveRequest, readinessWarnings);

        return new PromptExportResponse(
                effectiveRequest.getPromptType(),
                effectivePromptMode(effectiveRequest),
                effectiveRequest.getVisibilityMode(),
                SupportedLanguageResponse.from(targetLanguage),
                promptText,
                warnings,
                readinessWarnings
        );
    }

    private PromptExportRequest effectiveRequest(PromptExportRequest request, PromptContext context) {
        PromptMode promptMode = effectivePromptMode(request);
        return switch (promptMode) {
            case PUBLIC_SAFE -> publicSafeRequest(request);
            case RECOMMENDED_ADMIN -> recommendedAdminRequest(request, context);
            case CUSTOM_ADVANCED -> customAdvancedRequest(request);
        };
    }

    private PromptMode effectivePromptMode(PromptExportRequest request) {
        if (request.getPromptMode() != null) {
            return request.getPromptMode();
        }
        return request.getVisibilityMode() == PromptVisibilityMode.ADMIN_FULL_MODE
                ? PromptMode.CUSTOM_ADVANCED
                : PromptMode.PUBLIC_SAFE;
    }

    private PromptExportRequest publicSafeRequest(PromptExportRequest request) {
        PromptExportRequest effective = baseRequest(request, PromptMode.PUBLIC_SAFE, PromptVisibilityMode.SAFE_MODE);
        effective.setIncludePublicSamples(true);
        effective.setIncludeComparePolicy(true);
        effective.setIncludePublicNotes(true);
        effective.setIncludeAdminInternalNotes(false);
        effective.setIncludeReferenceSolution(false);
        effective.setIncludeProgramMetadata(false);
        effective.setIncludeProgramSources(false);
        effective.setIncludeAdditionalInstructions(true);
        effective.setConfirmSensitiveMaterial(false);
        return effective;
    }

    private PromptExportRequest recommendedAdminRequest(PromptExportRequest request, PromptContext context) {
        PromptExportRequest effective = baseRequest(request, PromptMode.RECOMMENDED_ADMIN, PromptVisibilityMode.ADMIN_FULL_MODE);
        effective.setIncludePublicSamples(true);
        effective.setIncludeComparePolicy(true);
        effective.setIncludePublicNotes(true);
        effective.setIncludeAdditionalInstructions(true);
        effective.setConfirmSensitiveMaterial(false);

        PromptType type = request.getPromptType();
        boolean includeReferenceSolution = switch (type) {
            case INPUT_GENERATOR, FULL_PROBLEM_ENGINEERING_BUNDLE -> context.activeReferenceSolution().isPresent();
            case CHECKER_OUTPUT_VALIDATOR -> context.problem().hasActiveCustomValidator()
                    && context.activeReferenceSolution().isPresent();
            case REFERENCE_SOLUTION, INPUT_VALIDATOR -> false;
        };
        boolean includeAdminNotes = switch (type) {
            case INPUT_GENERATOR, FULL_PROBLEM_ENGINEERING_BUNDLE -> hasText(context.problem().getAdminNotes());
            case REFERENCE_SOLUTION, INPUT_VALIDATOR, CHECKER_OUTPUT_VALIDATOR -> false;
        };

        effective.setIncludeReferenceSolution(includeReferenceSolution);
        effective.setIncludeAdminInternalNotes(includeAdminNotes);
        effective.setIncludeProgramMetadata(includeReferenceSolution || type == PromptType.CHECKER_OUTPUT_VALIDATOR
                || type == PromptType.FULL_PROBLEM_ENGINEERING_BUNDLE);
        effective.setIncludeProgramSources(false);
        return effective;
    }

    private PromptExportRequest customAdvancedRequest(PromptExportRequest request) {
        PromptExportRequest effective = baseRequest(
                request,
                PromptMode.CUSTOM_ADVANCED,
                request.getVisibilityMode() == null ? PromptVisibilityMode.SAFE_MODE : request.getVisibilityMode()
        );
        effective.setIncludePublicSamples(enabled(request.getIncludePublicSamples()));
        effective.setIncludeComparePolicy(enabled(request.getIncludeComparePolicy()));
        effective.setIncludePublicNotes(enabled(request.getIncludePublicNotes()));
        effective.setIncludeAdminInternalNotes(enabled(request.getIncludeAdminInternalNotes()));
        effective.setIncludeReferenceSolution(enabled(request.getIncludeReferenceSolution()));
        effective.setIncludeProgramMetadata(enabled(request.getIncludeProgramMetadata()));
        effective.setIncludeProgramSources(enabled(request.getIncludeProgramSources()));
        effective.setIncludeAdditionalInstructions(enabled(request.getIncludeAdditionalInstructions()));
        effective.setConfirmSensitiveMaterial(enabled(request.getConfirmSensitiveMaterial()));
        return effective;
    }

    private PromptExportRequest baseRequest(
            PromptExportRequest request,
            PromptMode promptMode,
            PromptVisibilityMode visibilityMode
    ) {
        PromptExportRequest effective = new PromptExportRequest();
        effective.setPromptType(request.getPromptType());
        effective.setPromptMode(promptMode);
        effective.setVisibilityMode(visibilityMode);
        effective.setTargetLanguage(request.getTargetLanguage());
        effective.setAdditionalInstructions(request.getAdditionalInstructions());
        return effective;
    }

    private List<String> safetyWarnings(PromptExportRequest request) {
        List<String> warnings = new ArrayList<>();
        warnings.add("AuraC2 only generates prompt text. It does not call AI services.");
        warnings.add("AI-generated code must be reviewed, compiled, tested, and verified inside AuraC2 before use.");
        if (request.getPromptMode() == PromptMode.RECOMMENDED_ADMIN) {
            warnings.add("Recommended Admin Prompt uses server-selected context, may include useful admin artifacts by prompt type, and excludes hidden tests and hidden expected outputs.");
        }
        if (request.getPromptMode() == PromptMode.PUBLIC_SAFE) {
            warnings.add("Public/Safe Prompt excludes admin notes, hidden tests, expected outputs, and private engineering artifacts.");
        }
        if (request.getPromptMode() == PromptMode.CUSTOM_ADVANCED
                && request.getVisibilityMode() == PromptVisibilityMode.ADMIN_FULL_MODE) {
            warnings.add("Custom Advanced ADMIN_FULL_MODE may include contest-sensitive material explicitly selected by the admin.");
        }
        return warnings;
    }

    private boolean enabled(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
