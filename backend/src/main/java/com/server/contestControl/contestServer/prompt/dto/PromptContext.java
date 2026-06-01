package com.server.contestControl.contestServer.prompt.dto;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.oracle.entity.InputGenerator;
import com.server.contestControl.contestServer.oracle.entity.InputValidator;
import com.server.contestControl.contestServer.oracle.entity.ReferenceSolution;
import com.server.contestControl.submissionServer.language.SupportedLanguage;

import java.util.List;
import java.util.Optional;

public record PromptContext(
        Problem problem,
        SupportedLanguage targetLanguage,
        List<TestCase> publicSamples,
        Optional<ReferenceSolution> activeReferenceSolution,
        Optional<InputGenerator> activeInputGenerator,
        Optional<InputValidator> activeInputValidator
) {
}
