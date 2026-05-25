package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.testcase.PublicTestCaseResponse;
import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private TestCaseDuplicateService testCaseDuplicateService;

    @InjectMocks
    private TestCaseService testCaseService;

    @Test
    void publicListingUsesOnlyPublicSamplesAndDoesNotLeakPrivateData() {
        Problem problem = Problem.builder().id(10L).build();
        TestCase publicSample = TestCase.builder()
                .id(1L)
                .problem(problem)
                .inputData("sample-input")
                .expectedOutput("sample-output")
                .isPublic(true)
                .build();

        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(10L))
                .thenReturn(List.of(publicSample));

        List<PublicTestCaseResponse> responses = testCaseService.getPublicTestCases(10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().inputData()).isEqualTo("sample-input");
        assertThat(responses.getFirst().expectedOutput()).isEqualTo("sample-output");
        assertThat(responses.getFirst().isPublic()).isTrue();
        assertThat(responses).extracting(PublicTestCaseResponse::inputData)
                .doesNotContain("private-input");
        assertThat(responses).extracting(PublicTestCaseResponse::expectedOutput)
                .doesNotContain("private-output");
        verify(testCaseRepository).findByProblemIdAndIsPublicTrueOrderByIdAsc(10L);
        verify(testCaseRepository, never()).findByProblemIdOrderByIdAsc(10L);
    }

    @Test
    void adminListingReturnsPrivateAndPublicExpectedOutputs() {
        Problem problem = Problem.builder().id(10L).build();
        TestCase privateCase = TestCase.builder()
                .id(1L)
                .problem(problem)
                .inputData("private-input")
                .expectedOutput("private-output")
                .isPublic(false)
                .build();
        TestCase publicCase = TestCase.builder()
                .id(2L)
                .problem(problem)
                .inputData("sample-input")
                .expectedOutput("sample-output")
                .isPublic(true)
                .build();

        when(testCaseRepository.findByProblemIdOrderByIdAsc(10L))
                .thenReturn(List.of(privateCase, publicCase));

        List<TestCaseResponse> responses = testCaseService.getAdminTestCases(10L);

        assertThat(responses)
                .extracting(TestCaseResponse::expectedOutput)
                .containsExactly("private-output", "sample-output");
        verify(testCaseRepository).findByProblemIdOrderByIdAsc(10L);
        verify(testCaseRepository, never()).findByProblemIdAndIsPublicTrueOrderByIdAsc(10L);
    }
}
