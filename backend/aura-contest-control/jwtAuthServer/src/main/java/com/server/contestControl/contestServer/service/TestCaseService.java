package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.testcase.TestCaseRequest;
import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    public TestCaseResponse addTestCase(Long problemId, TestCaseRequest request) {

        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new RuntimeException("Problem not found"));

        TestCase testCase = TestCase.builder()
                .problem(problem)
                .inputData(request.getInputData())
                .expectedOutput(request.getExpectedOutput())
                .isPublic(request.isPublic())
                .build();

        testCaseRepository.save(testCase);

        return TestCaseResponse.builder()
                .id(testCase.getId())
                .inputData(testCase.getInputData())
                .expectedOutput(testCase.getExpectedOutput())
                .isPublic(testCase.isPublic())
                .build();
    }

    public List<TestCaseResponse> getTestCases(Long problemId) {
        List<TestCase> testCases = testCaseRepository.findByProblemId(problemId);

        return testCases.stream()
                .map(TestCaseResponse::fromEntity)
                .toList();
    }

}
