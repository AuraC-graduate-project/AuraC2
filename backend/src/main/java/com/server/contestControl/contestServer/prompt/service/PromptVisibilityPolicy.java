package com.server.contestControl.contestServer.prompt.service;

import com.server.contestControl.contestServer.oracle.exception.OracleConfigurationException;
import com.server.contestControl.contestServer.prompt.dto.PromptExportRequest;
import com.server.contestControl.contestServer.prompt.enums.PromptMode;
import com.server.contestControl.contestServer.prompt.enums.PromptType;
import com.server.contestControl.contestServer.prompt.enums.PromptVisibilityMode;
import com.server.contestControl.submissionServer.language.SupportedLanguage;
import org.springframework.stereotype.Component;

@Component
public class PromptVisibilityPolicy {

    public void validate(PromptExportRequest request, SupportedLanguage language) {
        if (request.getPromptMode() == PromptMode.CUSTOM_ADVANCED
                && request.getVisibilityMode() == PromptVisibilityMode.ADMIN_FULL_MODE
                && !enabled(request.getConfirmSensitiveMaterial())) {
            throw new OracleConfigurationException(
                    "Custom Advanced Prompt with admin-sensitive context requires explicit confirmation"
            );
        }

        if (request.getVisibilityMode() == PromptVisibilityMode.SAFE_MODE) {
            rejectSafeMode(request.getIncludeAdminInternalNotes(), "Admin internal notes");
            rejectSafeMode(request.getIncludeReferenceSolution(), "Reference solution source");
            rejectSafeMode(request.getIncludeProgramSources(), "Generator, validator, or checker source snippets");
        }

        validateLanguageCapability(request.getPromptType(), language);
    }

    private void rejectSafeMode(Boolean requested, String label) {
        if (enabled(requested)) {
            throw new OracleConfigurationException(label + " cannot be included in SAFE_MODE prompt exports");
        }
    }

    private void validateLanguageCapability(PromptType promptType, SupportedLanguage language) {
        boolean supported = switch (promptType) {
            case REFERENCE_SOLUTION -> language.supportsReferenceSolution();
            case INPUT_GENERATOR -> language.supportsGenerator();
            case INPUT_VALIDATOR -> language.supportsInputValidator();
            case CHECKER_OUTPUT_VALIDATOR -> language.supportsChecker();
            case FULL_PROBLEM_ENGINEERING_BUNDLE -> language.supportsReferenceSolution()
                    && language.supportsGenerator()
                    && language.supportsInputValidator();
        };

        if (!supported) {
            throw new OracleConfigurationException(
                    language.label() + " is not supported for " + promptType.name()
            );
        }
    }

    private boolean enabled(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
