package com.server.contestControl.contestServer.prompt;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.oracle.entity.ReferenceSolution;
import com.server.contestControl.contestServer.prompt.dto.PromptExportRequest;
import com.server.contestControl.submissionServer.language.SupportedLanguage;
import com.server.contestControl.submissionServer.language.SupportedLanguageCatalog;
import com.server.contestControl.submissionServer.language.SupportedLanguageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptExportServiceTest {

    @Mock
    private SupportedLanguageService supportedLanguageService;

    @Mock
    private PromptVisibilityPolicy promptVisibilityPolicy;

    @Mock
    private PromptContextBuilder promptContextBuilder;

    @Mock
    private PromptTemplateRenderer promptTemplateRenderer;

    @Test
    void recommendedGeneratorPromptIncludesUsefulAdminContextWithoutProgramSources() {
        SupportedLanguage language = SupportedLanguageCatalog.findByValueOrAlias("cpp").orElseThrow();
        PromptExportService service = new PromptExportService(
                supportedLanguageService,
                promptVisibilityPolicy,
                promptContextBuilder,
                promptTemplateRenderer
        );
        PromptExportRequest request = new PromptExportRequest();
        request.setPromptType(PromptType.INPUT_GENERATOR);
        request.setPromptMode(PromptMode.RECOMMENDED_ADMIN);
        request.setTargetLanguage("cpp");
        request.setIncludeProgramSources(true);
        request.setIncludeAdminInternalNotes(false);
        Problem problem = Problem.builder()
                .id(1L)
                .contest(Contest.builder().id(1L).build())
                .title("Pairs")
                .adminNotes("Trap case guidance")
                .build();
        PromptContext context = new PromptContext(
                problem,
                language,
                List.of(),
                Optional.of(ReferenceSolution.builder().languageId(language.judge0LanguageId()).source("int main(){}").sourceHash("abc").build()),
                Optional.empty(),
                Optional.empty()
        );
        when(supportedLanguageService.requireByValue("cpp")).thenReturn(language);
        when(promptContextBuilder.build(1L, language)).thenReturn(context);
        when(promptContextBuilder.readinessWarnings(eq(context), any())).thenReturn(List.of());
        when(promptTemplateRenderer.render(eq(context), any(), eq(List.of()))).thenReturn("prompt");

        var response = service.preview(1L, request);

        ArgumentCaptor<PromptExportRequest> requestCaptor = ArgumentCaptor.forClass(PromptExportRequest.class);
        verify(promptTemplateRenderer).render(eq(context), requestCaptor.capture(), eq(List.of()));
        PromptExportRequest effective = requestCaptor.getValue();
        assertThat(response.promptMode()).isEqualTo(PromptMode.RECOMMENDED_ADMIN);
        assertThat(effective.getVisibilityMode()).isEqualTo(PromptVisibilityMode.ADMIN_FULL_MODE);
        assertThat(effective.getIncludeReferenceSolution()).isTrue();
        assertThat(effective.getIncludeAdminInternalNotes()).isTrue();
        assertThat(effective.getIncludeProgramSources()).isFalse();
    }
}
