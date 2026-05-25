package com.server.contestControl.contestServer.prompt.dto;

import com.server.contestControl.contestServer.prompt.PromptType;
import com.server.contestControl.contestServer.prompt.PromptVisibilityMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PromptExportRequest {
    @NotNull
    private PromptType promptType;

    @NotNull
    private PromptVisibilityMode visibilityMode = PromptVisibilityMode.SAFE_MODE;

    @NotBlank
    private String targetLanguage;

    private Boolean includePublicSamples = true;

    private Boolean includeComparePolicy = true;

    private Boolean includePublicNotes = true;

    private Boolean includeAdminInternalNotes = false;

    private Boolean includeReferenceSolution = false;

    private Boolean includeProgramMetadata = false;

    private Boolean includeProgramSources = false;

    private Boolean includeAdditionalInstructions = true;

    private Boolean confirmSensitiveMaterial = false;

    private String additionalInstructions;
}
