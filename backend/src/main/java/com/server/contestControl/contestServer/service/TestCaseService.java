package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.testcase.TestCaseRequest;
import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;
import com.server.contestControl.contestServer.dto.testcase.TestCaseUpdateRequest;
import com.server.contestControl.contestServer.dto.testcase.PublicTestCaseResponse;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.exceptions.DuplicateTestCaseException;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.exceptions.TestCaseNotFoundException;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    @Transactional
    public TestCaseResponse addTestCase(Long problemId, TestCaseRequest request) {

        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));

        String normalizedInput = request.getInputData().trim();
        String normalizedOutput = request.getExpectedOutput().trim();

        if (testCaseRepository.existsByProblemIdAndInputDataAndExpectedOutput(problemId, normalizedInput, normalizedOutput)) {
            throw new DuplicateTestCaseException();
        }

        TestCase testCase = TestCase.builder()
                .problem(problem)
                .inputData(normalizedInput)
                .expectedOutput(normalizedOutput)
                .isPublic(request.isPublic())
                .build();

        testCaseRepository.save(testCase);

        return TestCaseResponse.fromEntity(testCase);
    }

    @Transactional
    public TestCaseResponse updateTestCase(Long id, TestCaseUpdateRequest request) {
        TestCase testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new TestCaseNotFoundException(id));

        String normalizedInput = request.getInputData().trim();
        String normalizedOutput = request.getExpectedOutput().trim();

        if (testCaseRepository.existsByProblemIdAndInputDataAndExpectedOutputAndIdNot(
                testCase.getProblem().getId(), normalizedInput, normalizedOutput, id)) {
            throw new DuplicateTestCaseException();
        }

        testCase.setInputData(normalizedInput);
        testCase.setExpectedOutput(normalizedOutput);
        testCase.setPublic(request.isPublic());

        testCaseRepository.save(testCase);
        return TestCaseResponse.fromEntity(testCase);
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> getAdminTestCases(Long problemId) {
        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByIdAsc(problemId);
        return testCases.stream()
                .map(TestCaseResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicTestCaseResponse> getPublicTestCases(Long problemId) {
        return testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(problemId)
                .stream()
                .map(PublicTestCaseResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void deleteTestCase(Long id) {
        TestCase testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new TestCaseNotFoundException(id));

        testCaseRepository.delete(testCase);
    }

}
