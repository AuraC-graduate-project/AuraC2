package com.server.contestControl.contestServer.prompt;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.enums.ValidationMode;
import com.server.contestControl.contestServer.prompt.dto.PromptExportRequest;
import com.server.contestControl.submissionServer.language.SupportedLanguage;
import com.server.contestControl.submissionServer.language.SupportedLanguageCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateRendererTest {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    @Test
    void rendersGeneratorPromptFromStructuredFieldsAndSelectedLanguage() {
        SupportedLanguage language = SupportedLanguageCatalog.findByValueOrAlias("python").orElseThrow();
        Problem problem = Problem.builder()
                .id(10L)
                .contest(Contest.builder().id(1L).build())
                .title("Pairs")
                .description("Legacy body")
                .statement("<p>Count valid pairs.</p>")
                .inputFormat("<p>n followed by n integers.</p>")
                .outputFormat("<p>Print one integer.</p>")
                .constraintsText("<p>1 <= n <= 200000</p>")
                .publicNotes("<p>Order matters.</p>")
                .adminNotes("Hidden overflow trap")
                .timeLimit(1000)
                .memoryLimit(256)
                .difficulty(Difficulty.MEDIUM)
                .comparePolicy(ComparePolicy.EXACT)
                .validationMode(ValidationMode.BUILTIN_COMPARE_POLICY)
                .validatorEnabled(false)
                .build();
        PromptContext context = new PromptContext(
                problem,
                language,
                List.of(TestCase.builder().inputData("3\n1 2 3\n").expectedOutput("3\n").isPublic(true).build()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        PromptExportRequest request = new PromptExportRequest();
        request.setPromptType(PromptType.INPUT_GENERATOR);
        request.setTargetLanguage("python");

        String prompt = renderer.render(context, request, List.of());

        assertThat(prompt).contains("Language: Python 3");
        assertThat(prompt).contains("Input generator file: generator.py");
        assertThat(prompt).contains("Read two tokens from stdin: seed and testNumber");
        assertThat(prompt).contains("Count valid pairs.");
        assertThat(prompt).contains("Sample 1 input");
        assertThat(prompt).doesNotContain("Hidden overflow trap");
    }
}
