package com.server.contestControl.contestServer.prompt.service;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.oracle.repository.InputGeneratorRepository;
import com.server.contestControl.contestServer.oracle.repository.InputValidatorRepository;
import com.server.contestControl.contestServer.oracle.repository.ReferenceSolutionRepository;
import com.server.contestControl.contestServer.prompt.dto.PromptContext;
import com.server.contestControl.contestServer.prompt.dto.PromptExportRequest;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.submissionServer.language.SupportedLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PromptContextBuilder {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final ReferenceSolutionRepository referenceSolutionRepository;
    private final InputGeneratorRepository inputGeneratorRepository;
    private final InputValidatorRepository inputValidatorRepository;

    public PromptContext build(Long problemId, SupportedLanguage targetLanguage) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));

        return new PromptContext(
                problem,
                targetLanguage,
                testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(problemId),
                referenceSolutionRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(problemId),
                inputGeneratorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(problemId),
                inputValidatorRepository.findFirstByProblem_IdAndActiveTrueOrderByUpdatedAtDescIdDesc(problemId)
        );
    }

    public List<String> readinessWarnings(PromptContext context, PromptExportRequest request) {
        Problem problem = context.problem();
        List<String> warnings = new ArrayList<>();

        if (!hasText(problem.getTitle())) {
            warnings.add("Missing problem title.");
        }
        if (!hasText(effectiveStatement(problem))) {
            warnings.add("Missing statement/problem body.");
        }
        if (!hasText(problem.getInputFormat())) {
            warnings.add("Missing input format.");
        }
        if (!hasText(problem.getOutputFormat())) {
            warnings.add("Missing output format.");
        }
        if (!hasText(problem.getConstraintsText())) {
            warnings.add("Missing constraints.");
        }
        if (problem.getTimeLimit() == null || problem.getTimeLimit() <= 0) {
            warnings.add("Missing positive time limit.");
        }
        if (problem.getMemoryLimit() == null || problem.getMemoryLimit() <= 0) {
            warnings.add("Missing positive memory limit.");
        }
        if (Boolean.TRUE.equals(request.getIncludePublicSamples()) && context.publicSamples().isEmpty()) {
            warnings.add("No public sample test cases are available.");
        }
        if (!hasText(problem.getPublicNotes())) {
            warnings.add("Public notes are empty; add contestant-safe clarifications if the statement needs them.");
        }

        return warnings;
    }

    static String effectiveStatement(Problem problem) {
        return hasText(problem.getStatement()) ? problem.getStatement() : problem.getDescription();
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
