package com.server.contestControl.contestServer.prompt;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.ValidationMode;
import com.server.contestControl.contestServer.oracle.entity.InputGenerator;
import com.server.contestControl.contestServer.oracle.entity.InputValidator;
import com.server.contestControl.contestServer.oracle.entity.ReferenceSolution;
import com.server.contestControl.contestServer.prompt.dto.PromptExportRequest;
import com.server.contestControl.submissionServer.language.SupportedLanguage;
import com.server.contestControl.submissionServer.language.SupportedLanguageCatalog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PromptTemplateRenderer {

    public String render(PromptContext context, PromptExportRequest request, List<String> readinessWarnings) {
        StringBuilder out = new StringBuilder();
        Problem problem = context.problem();
        SupportedLanguage language = context.targetLanguage();

        appendTitle(out, "Role and Task");
        out.append(roleAndTask(request.getPromptType())).append("\n\n");

        appendTitle(out, "AuraC2 Context");
        out.append("AuraC2 is a university programming contest-control system. ")
                .append("This export is deterministic prompt text only. AuraC2 does not call AI services here.\n")
                .append("Any code produced outside AuraC2 must be reviewed, compiled, tested, and verified inside AuraC2 before it becomes official.\n")
                .append("- Prompt mode: ").append(promptModeLabel(request.getPromptMode())).append("\n")
                .append("- Visibility boundary: ").append(visibilityLabel(request.getVisibilityMode())).append("\n\n");

        appendTitle(out, "Selected Target Language");
        out.append("- Language: ").append(language.label()).append("\n")
                .append("- Judge0 language ID: ").append(language.judge0LanguageId()).append("\n")
                .append("- Entry point: ").append(language.entryPoint()).append("\n")
                .append("- Runtime notes: ").append(language.runtimeNotes()).append("\n")
                .append("- Verification notes: ").append(language.verificationNotes()).append("\n\n");

        appendTitle(out, "Problem Data");
        out.append("- Title: ").append(defaultText(problem.getTitle(), "(missing title)")).append("\n")
                .append("- Time limit: ").append(problem.getTimeLimit() == null ? "(missing)" : problem.getTimeLimit() + " ms").append("\n")
                .append("- Memory limit: ").append(problem.getMemoryLimit() == null ? "(missing)" : problem.getMemoryLimit() + " MB").append("\n");
        if (!readinessWarnings.isEmpty()) {
            out.append("- AuraC2 readiness warnings: ").append(String.join("; ", readinessWarnings)).append("\n");
        }
        out.append("\n");

        appendBodySection(out, "Statement", PromptContextBuilder.effectiveStatement(problem));
        appendBodySection(out, "Input Format", problem.getInputFormat());
        appendBodySection(out, "Output Format", problem.getOutputFormat());
        appendBodySection(out, "Constraints", problem.getConstraintsText());

        if (Boolean.TRUE.equals(request.getIncludePublicNotes())) {
            appendBodySection(out, "Public Notes", problem.getPublicNotes());
        }

        if (request.getVisibilityMode() == PromptVisibilityMode.ADMIN_FULL_MODE
                && Boolean.TRUE.equals(request.getIncludeAdminInternalNotes())) {
            appendTitle(out, "Admin Internal Notes");
            out.append("Sensitive admin-only notes follow. Do not expose them to contestants.\n\n")
                    .append(defaultText(toPlainText(problem.getAdminNotes()), "(no admin internal notes configured)"))
                    .append("\n\n");
        }

        if (Boolean.TRUE.equals(request.getIncludePublicSamples())) {
            appendPublicSamples(out, context.publicSamples());
        }

        if (Boolean.TRUE.equals(request.getIncludeComparePolicy())) {
            appendComparePolicy(out, problem);
        }

        appendTitle(out, "AuraC2 File Contract");
        out.append(fileContract(request.getPromptType(), language)).append("\n\n");

        appendTitle(out, "Language-Specific Execution Requirements");
        out.append(language.runtimeNotes()).append("\n")
                .append(language.verificationNotes()).append("\n")
                .append("The artifact must be executable through AuraC2/Judge0 for language ID ")
                .append(language.judge0LanguageId()).append(".\n\n");

        appendProgramContext(out, context, request);

        if (Boolean.TRUE.equals(request.getIncludeAdditionalInstructions()) && hasText(request.getAdditionalInstructions())) {
            appendTitle(out, "Admin Additional Instructions");
            out.append(toPlainText(request.getAdditionalInstructions())).append("\n\n");
        }

        appendTitle(out, "Required Files to Produce");
        out.append(requiredFiles(request.getPromptType(), language)).append("\n\n");

        appendTitle(out, "Verification Checklist");
        out.append("- Code must compile in the selected language.\n")
                .append("- Code must be deterministic.\n")
                .append("- Generated inputs must pass the input validator before promotion.\n")
                .append("- Reference solution output must match official expected outputs.\n")
                .append("- Hidden tests and hidden expected outputs must not be exposed.\n")
                .append("- AI-generated code is not trusted until AuraC2 verifies it.\n")
                .append("- The artifact must match the selected language: ").append(language.label()).append(".\n")
                .append("- The artifact must follow AuraC2/Judge0 execution conventions for this language.\n");
        appendArtifactSpecificChecklist(out, request.getPromptType(), language);
        out.append("\n");

        appendTitle(out, "Strict Output Rules");
        out.append("- Produce only the requested files and concise implementation notes.\n")
                .append("- Do not invent hidden tests, official diagnostics, API keys, or external services.\n")
                .append("- Do not include debug logs on stdout for executable artifacts.\n")
                .append("- Do not assume C++17 unless the selected language is C++17.\n")
                .append("- Keep all generated code reviewable and suitable for AuraC2 verification.\n");

        if (request.getVisibilityMode() == PromptVisibilityMode.ADMIN_FULL_MODE) {
            out.append("\nSensitive-material warning: this admin-context export may contain contest-sensitive material selected by an admin. Handle it as confidential.\n");
        }

        return out.toString();
    }

    private String roleAndTask(PromptType type) {
        return switch (type) {
            case REFERENCE_SOLUTION -> "You are assisting a contest setter. Produce a correct reference solution for the problem in the selected target language.";
            case INPUT_GENERATOR -> "You are assisting a contest setter. Produce an input generator for the problem in the selected target language.";
            case INPUT_VALIDATOR -> "You are assisting a contest setter. Produce a strict input validator for the problem in the selected target language.";
            case CHECKER_OUTPUT_VALIDATOR -> "You are assisting a contest setter. First decide whether a custom checker is needed, then produce one only when the compare policy is insufficient.";
            case FULL_PROBLEM_ENGINEERING_BUNDLE -> "You are assisting a contest setter. Produce a full problem engineering bundle in the selected target language.";
        };
    }

    private String fileContract(PromptType type, SupportedLanguage language) {
        StringBuilder out = new StringBuilder();
        switch (type) {
            case REFERENCE_SOLUTION -> appendReferenceContract(out, language);
            case INPUT_GENERATOR -> appendGeneratorContract(out, language);
            case INPUT_VALIDATOR -> appendValidatorContract(out, language);
            case CHECKER_OUTPUT_VALIDATOR -> appendCheckerContract(out, language);
            case FULL_PROBLEM_ENGINEERING_BUNDLE -> {
                appendReferenceContract(out, language);
                out.append("\n");
                appendGeneratorContract(out, language);
                out.append("\n");
                appendValidatorContract(out, language);
                out.append("\n");
                appendCheckerContract(out, language);
            }
        }
        return out.toString();
    }

    private void appendReferenceContract(StringBuilder out, SupportedLanguage language) {
        out.append("- Reference solution file: ").append(language.referenceFileName()).append("\n")
                .append("- Read official input from stdin and write official output to stdout.\n")
                .append("- Do not print debug output.\n")
                .append("- Be deterministic and correct for all stated constraints.\n");
    }

    private void appendGeneratorContract(StringBuilder out, SupportedLanguage language) {
        out.append("- Input generator file: ").append(language.generatorFileName()).append("\n")
                .append("- Read two tokens from stdin: seed and testNumber. AuraC2 currently sends them on separate lines.\n")
                .append("- Generate exactly one valid input case and print only that input to stdout.\n")
                .append("- Be deterministic for the same seed and testNumber.\n")
                .append("- Cover boundary cases, random cases, edge cases, and stress-like categories by testNumber.\n")
                .append("- Do not use testlib unless an admin explicitly asks and AuraC2 supports it.\n");
        appendCpp17GeneratorGuardrails(out, language);
    }

    private void appendValidatorContract(StringBuilder out, SupportedLanguage language) {
        out.append("- Input validator file: ").append(language.validatorFileName()).append("\n")
                .append("- Strictly validate ranges, counts, structure, EOF, extra tokens, and problem-specific rules.\n")
                .append("- Print exactly one line to stdout: VALID or INVALID.\n")
                .append("- Return exit code 0 even for INVALID because AuraC2 reads the decision from stdout.\n")
                .append("- Send optional diagnostics to stderr only.\n")
                .append("- Compile and run the validator on known valid and invalid inputs before using it in AuraC2.\n");
        appendCpp17ValidatorGuardrails(out, language);
    }

    private void appendCheckerContract(StringBuilder out, SupportedLanguage language) {
        out.append("- Checker/output validator file: ").append(language.checkerFileName()).append("\n")
                .append("- First write checker_need_decision.md explaining whether a custom checker is actually needed.\n")
                .append("- If exact, normalized, token-normalized, or float-tolerance comparison is sufficient, say no custom checker is needed and do not produce checker code.\n")
                .append("- Produce ").append(language.checkerFileName()).append(" only when the compare policy cannot express the accepted outputs.\n")
                .append("- AuraC2 custom checker stdin has three UTF-8 byte-length-prefixed sections: official input, expected output, contestant output.\n")
                .append("- For each section, read the byte length line, then that many bytes of payload, then the trailing newline.\n")
                .append("- Print ACCEPT or REJECT as the first non-empty stdout line. Do not print debug logs to stdout.\n");
    }

    private String requiredFiles(PromptType type, SupportedLanguage language) {
        return switch (type) {
            case REFERENCE_SOLUTION -> "- " + language.referenceFileName()
                    + "\n- algorithm_explanation.md\n- proof_idea.md\n- complexity.md";
            case INPUT_GENERATOR -> "- " + language.generatorFileName()
                    + "\n- generator_notes.md\n- edge_case_plan.md";
            case INPUT_VALIDATOR -> "- " + language.validatorFileName()
                    + "\n- constraint_coverage_checklist.md"
                    + "\n- validator_self_test_notes.md";
            case CHECKER_OUTPUT_VALIDATOR -> "- checker_need_decision.md\n- "
                    + language.checkerFileName()
                    + " only if a custom checker is needed";
            case FULL_PROBLEM_ENGINEERING_BUNDLE -> "- " + language.referenceFileName()
                    + "\n- " + language.generatorFileName()
                    + "\n- " + language.validatorFileName()
                    + "\n- checker_need_decision.md"
                    + "\n- " + language.checkerFileName() + " only if a custom checker is needed"
                    + "\n- algorithm_explanation.md"
                    + "\n- proof_idea.md"
                    + "\n- complexity.md"
                    + "\n- edge_case_plan.md"
                    + "\n- constraint_coverage_checklist.md"
                    + "\n- validator_self_test_notes.md";
        };
    }

    private void appendArtifactSpecificChecklist(StringBuilder out, PromptType type, SupportedLanguage language) {
        if (type == PromptType.INPUT_VALIDATOR || type == PromptType.FULL_PROBLEM_ENGINEERING_BUNDLE) {
            out.append("- Compile the input validator before using it; compilation errors make it unusable in AuraC2.\n")
                    .append("- Run the validator on valid samples and deliberately invalid cases.\n")
                    .append("- Confirm validator stdout is exactly VALID or INVALID with no debug logs.\n")
                    .append("- Check EOF, extra non-whitespace tokens, missing tokens, malformed tokens, and out-of-range values.\n");
        }
        if ((type == PromptType.INPUT_GENERATOR || type == PromptType.FULL_PROBLEM_ENGINEERING_BUNDLE) && isCpp17(language)) {
            out.append("- Compile the C++17 generator before use and confirm it prints only generated input to stdout.\n");
        }
    }

    private void appendCpp17GeneratorGuardrails(StringBuilder out, SupportedLanguage language) {
        if (!isCpp17(language)) {
            return;
        }
        out.append("- C++17-specific guardrails: compile under C++17, use deterministic RNG safely, and avoid ambiguous parser-sensitive constructs.\n")
                .append("- Keep stdout clean: print only the generated input, never explanations or debug logs.\n");
    }

    private void appendCpp17ValidatorGuardrails(StringBuilder out, SupportedLanguage language) {
        if (!isCpp17(language)) {
            return;
        }
        out.append("- C++17-specific guardrails: compile under C++17 and avoid C++ Most Vexing Parse patterns.\n")
                .append("- Prefer brace initialization when constructing strings from iterators.\n")
                .append("- If reading all stdin into a string, use a safe pattern such as:\n")
                .append("```cpp\n")
                .append("const string input{\n")
                .append("    istreambuf_iterator<char>(cin),\n")
                .append("    istreambuf_iterator<char>()\n")
                .append("};\n")
                .append("```\n")
                .append("- Or use ostringstream:\n")
                .append("```cpp\n")
                .append("ostringstream ss;\n")
                .append("ss << cin.rdbuf();\n")
                .append("string input = ss.str();\n")
                .append("```\n")
                .append("- Do not use ambiguous parenthesized declarations for iterator-based string construction, such as string input((istreambuf_iterator<char>(cin)), istreambuf_iterator<char>()).\n");
    }

    private void appendProgramContext(StringBuilder out, PromptContext context, PromptExportRequest request) {
        if (!Boolean.TRUE.equals(request.getIncludeProgramMetadata()) && !Boolean.TRUE.equals(request.getIncludeProgramSources())) {
            return;
        }

        appendTitle(out, "Existing AuraC2 Engineering Artifacts");
        appendProgramMetadata(out, "Active reference solution", context.activeReferenceSolution().map(ProgramInfo::from));
        appendProgramMetadata(out, "Active input generator", context.activeInputGenerator().map(ProgramInfo::from));
        appendProgramMetadata(out, "Active input validator", context.activeInputValidator().map(ProgramInfo::from));

        Problem problem = context.problem();
        if (problem.hasActiveCustomValidator()) {
            out.append("- Active custom output validator: language ")
                    .append(languageLabel(problem.getValidatorLanguageId()))
                    .append(", source hash ")
                    .append(defaultText(problem.getValidatorSourceHash(), "(missing hash)"))
                    .append("\n");
        } else {
            out.append("- Active custom output validator: none\n");
        }

        if (request.getVisibilityMode() == PromptVisibilityMode.ADMIN_FULL_MODE
                && Boolean.TRUE.equals(request.getIncludeReferenceSolution())) {
            appendSourceBlock(out, "Reference solution source", context.activeReferenceSolution().map(ReferenceSolution::getSource));
        }
        if (request.getVisibilityMode() == PromptVisibilityMode.ADMIN_FULL_MODE
                && Boolean.TRUE.equals(request.getIncludeProgramSources())) {
            appendSourceBlock(out, "Input generator source", context.activeInputGenerator().map(InputGenerator::getSource));
            appendSourceBlock(out, "Input validator source", context.activeInputValidator().map(InputValidator::getSource));
            appendSourceBlock(out, "Custom output validator source", Optional.ofNullable(problem.getValidatorSource()));
        }
        out.append("\n");
    }

    private void appendProgramMetadata(StringBuilder out, String label, Optional<ProgramInfo> program) {
        if (program.isEmpty()) {
            out.append("- ").append(label).append(": none\n");
            return;
        }
        ProgramInfo info = program.get();
        out.append("- ").append(label)
                .append(": language ").append(languageLabel(info.languageId()))
                .append(", source hash ").append(info.sourceHash());
        if (info.defaultTestCount() != null) {
            out.append(", default tests ").append(info.defaultTestCount());
        }
        out.append("\n");
    }

    private void appendSourceBlock(StringBuilder out, String label, Optional<String> source) {
        out.append("\n").append(label).append(":\n");
        if (source.isEmpty() || !hasText(source.get())) {
            out.append("(not configured)\n");
            return;
        }
        out.append("```text\n").append(source.get()).append("\n```\n");
    }

    private void appendPublicSamples(StringBuilder out, List<TestCase> samples) {
        appendTitle(out, "Public Samples");
        if (samples.isEmpty()) {
            out.append("(No public samples are configured.)\n\n");
            return;
        }
        for (int i = 0; i < samples.size(); i++) {
            TestCase sample = samples.get(i);
            out.append("Sample ").append(i + 1).append(" input:\n")
                    .append("```text\n").append(defaultText(sample.getInputData(), "")).append("\n```\n")
                    .append("Sample ").append(i + 1).append(" output:\n")
                    .append("```text\n").append(defaultText(sample.getExpectedOutput(), "")).append("\n```\n\n");
        }
    }

    private void appendComparePolicy(StringBuilder out, Problem problem) {
        appendTitle(out, "Compare Policy / Checker Rules");
        ComparePolicy comparePolicy = problem.getComparePolicy() == null ? ComparePolicy.EXACT : problem.getComparePolicy();
        ValidationMode validationMode = problem.getValidationMode() == null
                ? ValidationMode.BUILTIN_COMPARE_POLICY
                : problem.getValidationMode();
        out.append("- Compare policy: ").append(comparePolicy.name()).append("\n")
                .append("- Validation mode: ").append(validationMode.name()).append("\n");
        if (comparePolicy == ComparePolicy.FLOAT_TOLERANCE) {
            out.append("- Absolute epsilon: ").append(problem.getFloatAbsoluteEpsilon()).append("\n")
                    .append("- Relative epsilon: ").append(problem.getFloatRelativeEpsilon()).append("\n");
        }
        if (problem.hasActiveCustomValidator()) {
            out.append("- AuraC2 has an active custom output validator. Follow its stdin/stdout checker contract exactly.\n");
        } else if (comparePolicy == ComparePolicy.EXACT) {
            out.append("- Built-in exact comparison is usually sufficient unless the statement allows multiple valid outputs.\n");
        } else if (comparePolicy == ComparePolicy.NORMALIZED_TEXT || comparePolicy == ComparePolicy.TOKEN_NORMALIZED) {
            out.append("- Built-in normalized comparison is usually sufficient unless output semantics require deeper validation.\n");
        } else if (comparePolicy == ComparePolicy.FLOAT_TOLERANCE) {
            out.append("- Built-in float tolerance may be sufficient when every output token is numeric and tolerance rules match the statement.\n");
        } else {
            out.append("- No active custom output validator is configured.\n");
        }
        out.append("\n");
    }

    private void appendBodySection(StringBuilder out, String title, String value) {
        appendTitle(out, title);
        out.append(defaultText(toPlainText(value), "(missing)")).append("\n\n");
    }

    private void appendTitle(StringBuilder out, String title) {
        out.append("## ").append(title).append("\n");
    }

    private String languageLabel(Integer languageId) {
        return SupportedLanguageCatalog.findByJudge0LanguageId(languageId)
                .map(language -> language.label() + " (Judge0 " + language.judge0LanguageId() + ")")
                .orElse("Judge0 " + defaultText(languageId == null ? null : String.valueOf(languageId), "unknown"));
    }

    private boolean isCpp17(SupportedLanguage language) {
        return language != null
                && ("cpp".equalsIgnoreCase(language.value())
                || "C++17".equalsIgnoreCase(language.label())
                || language.aliases().stream().anyMatch(alias -> "c++17".equalsIgnoreCase(alias) || "cpp17".equalsIgnoreCase(alias)));
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private String promptModeLabel(PromptMode promptMode) {
        if (promptMode == null) {
            return "Unspecified";
        }
        return switch (promptMode) {
            case RECOMMENDED_ADMIN -> "Recommended Admin Prompt";
            case PUBLIC_SAFE -> "Public/Safe Prompt";
            case CUSTOM_ADVANCED -> "Custom Advanced Prompt";
        };
    }

    private String visibilityLabel(PromptVisibilityMode visibilityMode) {
        if (visibilityMode == null) {
            return "Unspecified";
        }
        return switch (visibilityMode) {
            case SAFE_MODE -> "Public/Safe";
            case ADMIN_FULL_MODE -> "Admin-sensitive context";
        };
    }

    private String toPlainText(String value) {
        if (!hasText(value)) {
            return "";
        }
        String text = value
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n\n")
                .replaceAll("(?i)</h[1-6]\\s*>", "\n\n")
                .replaceAll("(?i)<li\\s*>", "- ")
                .replaceAll("(?i)</li\\s*>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ProgramInfo(Integer languageId, String sourceHash, Integer defaultTestCount) {
        static ProgramInfo from(ReferenceSolution solution) {
            return new ProgramInfo(solution.getLanguageId(), solution.getSourceHash(), null);
        }

        static ProgramInfo from(InputGenerator generator) {
            return new ProgramInfo(generator.getLanguageId(), generator.getSourceHash(), generator.getDefaultTestCount());
        }

        static ProgramInfo from(InputValidator validator) {
            return new ProgramInfo(validator.getLanguageId(), validator.getSourceHash(), null);
        }
    }
}
