package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCaseDuplicateService {

    private final TestCaseRepository testCaseRepository;

    public String normalizeInput(String inputData) {
        return inputData == null ? "" : inputData.trim();
    }

    public String normalizeOutput(String expectedOutput) {
        return expectedOutput == null ? "" : expectedOutput.trim();
    }

    public boolean exactDuplicateExists(Long problemId, String inputData, String expectedOutput) {
        String normalizedInput = normalizeInput(inputData);
        String normalizedOutput = normalizeOutput(expectedOutput);
        return testCaseRepository.existsByProblemIdAndInputDataAndExpectedOutput(
                problemId,
                normalizedInput,
                normalizedOutput
        );
    }

    public boolean exactDuplicateExistsExcludingId(Long problemId, String inputData, String expectedOutput, Long excludedId) {
        String normalizedInput = normalizeInput(inputData);
        String normalizedOutput = normalizeOutput(expectedOutput);
        return testCaseRepository.existsByProblemIdAndInputDataAndExpectedOutputAndIdNot(
                problemId,
                normalizedInput,
                normalizedOutput,
                excludedId
        );
    }

    public boolean inputDuplicateExistsForPromotion(Long problemId, String inputData) {
        String normalizedInput = normalizeInput(inputData);
        List<TestCase> officialCases = testCaseRepository.findByProblemIdForDuplicatePromotionCheck(problemId);
        return officialCases.stream()
                .anyMatch(testCase -> normalizeInput(testCase.getInputData()).equals(normalizedInput));
    }
}
