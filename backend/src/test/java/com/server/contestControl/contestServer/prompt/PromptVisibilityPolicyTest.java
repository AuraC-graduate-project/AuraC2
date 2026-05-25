package com.server.contestControl.contestServer.prompt;

import com.server.contestControl.contestServer.oracle.exception.OracleConfigurationException;
import com.server.contestControl.contestServer.prompt.dto.PromptExportRequest;
import com.server.contestControl.submissionServer.language.SupportedLanguage;
import com.server.contestControl.submissionServer.language.SupportedLanguageCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptVisibilityPolicyTest {

    private final PromptVisibilityPolicy policy = new PromptVisibilityPolicy();
    private final SupportedLanguage language = SupportedLanguageCatalog.findByValueOrAlias("cpp").orElseThrow();

    @Test
    void safeModeRejectsAdminInternalNotes() {
        PromptExportRequest request = baseRequest();
        request.setIncludeAdminInternalNotes(true);

        assertThatThrownBy(() -> policy.validate(request, language))
                .isInstanceOf(OracleConfigurationException.class)
                .hasMessageContaining("SAFE_MODE");
    }

    @Test
    void safeModeRejectsProgramSources() {
        PromptExportRequest request = baseRequest();
        request.setIncludeProgramSources(true);

        assertThatThrownBy(() -> policy.validate(request, language))
                .isInstanceOf(OracleConfigurationException.class)
                .hasMessageContaining("source snippets");
    }

    @Test
    void adminFullModeRequiresConfirmation() {
        PromptExportRequest request = baseRequest();
        request.setPromptMode(PromptMode.CUSTOM_ADVANCED);
        request.setVisibilityMode(PromptVisibilityMode.ADMIN_FULL_MODE);
        request.setConfirmSensitiveMaterial(false);

        assertThatThrownBy(() -> policy.validate(request, language))
                .isInstanceOf(OracleConfigurationException.class)
                .hasMessageContaining("confirmation");
    }

    @Test
    void recommendedAdminModeDoesNotRequireAdvancedConfirmation() {
        PromptExportRequest request = baseRequest();
        request.setPromptMode(PromptMode.RECOMMENDED_ADMIN);
        request.setVisibilityMode(PromptVisibilityMode.ADMIN_FULL_MODE);
        request.setIncludeAdminInternalNotes(true);
        request.setConfirmSensitiveMaterial(false);

        policy.validate(request, language);
    }

    private PromptExportRequest baseRequest() {
        PromptExportRequest request = new PromptExportRequest();
        request.setPromptType(PromptType.INPUT_GENERATOR);
        request.setVisibilityMode(PromptVisibilityMode.SAFE_MODE);
        request.setTargetLanguage("cpp");
        return request;
    }
}
