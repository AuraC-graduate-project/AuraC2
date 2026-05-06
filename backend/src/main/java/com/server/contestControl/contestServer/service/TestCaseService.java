package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.testcase.TestCaseRequest;
import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;
import com.server.contestControl.contestServer.dto.testcase.TestCaseUpdateRequest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.exceptions.TestCaseNotFoundException;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    public TestCaseResponse addTestCase(Long problemId, TestCaseRequest request) {

        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));

        TestCase testCase = TestCase.builder()
                .problem(problem)
                .inputData(request.getInputData())
                .expectedOutput(request.getExpectedOutput())
                .isPublic(request.isPublic())
                .build();

        testCaseRepository.save(testCase);

        return TestCaseResponse.fromEntity(testCase);
    }

    public TestCaseResponse updateTestCase(Long id, TestCaseUpdateRequest request) {
        TestCase testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new TestCaseNotFoundException(id));

        testCase.setInputData(request.getInputData());
        testCase.setExpectedOutput(request.getExpectedOutput());
        testCase.setPublic(request.isPublic());

        testCaseRepository.save(testCase);
        return TestCaseResponse.fromEntity(testCase);
    }

    public List<TestCaseResponse> getTestCases(Long problemId) {
        List<TestCase> testCases = testCaseRepository.findByProblemId(problemId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        return testCases.stream()
                .filter(testCase -> isAdmin || testCase.isPublic())
                .map(isAdmin ? TestCaseResponse::fromEntity : TestCaseResponse::fromPublicEntity)
                .toList();
    }

    @Transactional
    public void deleteTestCase(Long id) {
        TestCase testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new TestCaseNotFoundException(id));

        testCaseRepository.delete(testCase);
    }

}
