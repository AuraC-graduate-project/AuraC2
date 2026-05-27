package com.server.contestControl.submissionServer.service.judge;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.ValidationMode;
import com.server.contestControl.submissionServer.dto.Judge0SubmissionDTO;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.service.callback.Judge0CallbackSignatureService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Judge0ServiceTest {

    @Test
    void callbackUrlIncludesSignatureBoundToSubmissionRunAndTestCase() {
        Judge0CallbackSignatureService signatureService = mock(Judge0CallbackSignatureService.class);
        Judge0Service judge0Service = new Judge0Service(
                signatureService,
                mock(RestTemplate.class),
                new Judge0ExpectedOutputPolicy()
        );
        ReflectionTestUtils.setField(
                judge0Service,
                "callbackUrl",
                "http://localhost:8080/api/callback/judge0"
        );

        Submission submission = Submission.builder()
                .id(1L)
                .judgeRunId(7L)
                .build();

        when(signatureService.sign(1L, 7L, 2)).thenReturn("abc123");

        String callbackUrl = judge0Service.buildSignedCallbackUrl(submission, 2);

        assertThat(callbackUrl)
                .isEqualTo("http://localhost:8080/api/callback/judge0/1/7/2?signature=abc123");
    }

    @Test
    void judge0RequestIncludesProblemTimeAndMemoryLimits() {
        Judge0CallbackSignatureService signatureService = mock(Judge0CallbackSignatureService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        Judge0Service judge0Service = new Judge0Service(
                signatureService,
                restTemplate,
                new Judge0ExpectedOutputPolicy()
        );
        ReflectionTestUtils.setField(judge0Service, "judge0Url", "http://judge0/submissions?wait=false");
        ReflectionTestUtils.setField(judge0Service, "callbackUrl", "http://localhost:8080/api/callback/judge0");

        Problem problem = Problem.builder()
                .id(10L)
                .timeLimit(1500)
                .memoryLimit(256)
                .build();
        Submission submission = Submission.builder()
                .id(1L)
                .problem(problem)
                .judgeRunId(7L)
                .code("class Main {}")
                .build();
        TestCase testCase = TestCase.builder()
                .inputData("1")
                .expectedOutput("1")
                .build();

        when(signatureService.sign(1L, 7L, 1)).thenReturn("sig");

        judge0Service.sendSingleTest(submission, testCase, 1, 62);

        ArgumentCaptor<Judge0SubmissionDTO> dtoCaptor = ArgumentCaptor.forClass(Judge0SubmissionDTO.class);
        verify(restTemplate).postForObject(
                eq("http://judge0/submissions?wait=false&base64_encoded=true"),
                dtoCaptor.capture(),
                eq(Object.class)
        );
        Judge0SubmissionDTO dto = dtoCaptor.getValue();
        assertThat(decoded(dto.getSourceCode())).isEqualTo("class Main {}");
        assertThat(decoded(dto.getStdin())).isEqualTo("1");
        assertThat(decoded(dto.getExpectedOutput())).isEqualTo("1");
        assertThat(dto.getCpuTimeLimit()).isEqualTo(1.5);
        assertThat(dto.getMemoryLimit()).isEqualTo(262144);
    }

    @Test
    void nonExactComparePolicyOmitsExpectedOutputForBackendComparison() {
        Judge0CallbackSignatureService signatureService = mock(Judge0CallbackSignatureService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        Judge0Service judge0Service = new Judge0Service(
                signatureService,
                restTemplate,
                new Judge0ExpectedOutputPolicy()
        );
        ReflectionTestUtils.setField(judge0Service, "judge0Url", "http://judge0/submissions?wait=false");
        ReflectionTestUtils.setField(judge0Service, "callbackUrl", "http://localhost:8080/api/callback/judge0");

        Problem problem = Problem.builder()
                .id(10L)
                .timeLimit(1000)
                .memoryLimit(128)
                .comparePolicy(ComparePolicy.TOKEN_NORMALIZED)
                .build();
        Submission submission = Submission.builder()
                .id(1L)
                .problem(problem)
                .judgeRunId(7L)
                .code("class Main {}")
                .build();
        TestCase testCase = TestCase.builder()
                .inputData("1")
                .expectedOutput("secret hidden output")
                .build();

        when(signatureService.sign(1L, 7L, 1)).thenReturn("sig");

        judge0Service.sendSingleTest(submission, testCase, 1, 62);

        ArgumentCaptor<Judge0SubmissionDTO> dtoCaptor = ArgumentCaptor.forClass(Judge0SubmissionDTO.class);
        verify(restTemplate).postForObject(
                eq("http://judge0/submissions?wait=false&base64_encoded=true"),
                dtoCaptor.capture(),
                eq(Object.class)
        );
        assertThat(dtoCaptor.getValue().getExpectedOutput()).isNull();
    }

    @Test
    void activeCustomValidatorOmitsExpectedOutputEvenWhenComparePolicyIsExact() {
        Judge0CallbackSignatureService signatureService = mock(Judge0CallbackSignatureService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        Judge0Service judge0Service = new Judge0Service(
                signatureService,
                restTemplate,
                new Judge0ExpectedOutputPolicy()
        );
        ReflectionTestUtils.setField(judge0Service, "judge0Url", "http://judge0/submissions?wait=false");
        ReflectionTestUtils.setField(judge0Service, "callbackUrl", "http://localhost:8080/api/callback/judge0");

        Problem problem = Problem.builder()
                .id(10L)
                .timeLimit(1000)
                .memoryLimit(128)
                .comparePolicy(ComparePolicy.EXACT)
                .validationMode(ValidationMode.CUSTOM_VALIDATOR)
                .validatorEnabled(true)
                .validatorLanguageId(71)
                .validatorSource("checker")
                .build();
        Submission submission = Submission.builder()
                .id(1L)
                .problem(problem)
                .judgeRunId(7L)
                .code("class Main {}")
                .build();
        TestCase testCase = TestCase.builder()
                .inputData("1")
                .expectedOutput("secret hidden output")
                .build();

        when(signatureService.sign(1L, 7L, 1)).thenReturn("sig");

        judge0Service.sendSingleTest(submission, testCase, 1, 62);

        ArgumentCaptor<Judge0SubmissionDTO> dtoCaptor = ArgumentCaptor.forClass(Judge0SubmissionDTO.class);
        verify(restTemplate).postForObject(
                eq("http://judge0/submissions?wait=false&base64_encoded=true"),
                dtoCaptor.capture(),
                eq(Object.class)
        );
        assertThat(dtoCaptor.getValue().getExpectedOutput()).isNull();
    }

    @Test
    void nullOrNonPositiveProblemLimitsAreOmittedFromJudge0Request() {
        Judge0CallbackSignatureService signatureService = mock(Judge0CallbackSignatureService.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        Judge0Service judge0Service = new Judge0Service(
                signatureService,
                restTemplate,
                new Judge0ExpectedOutputPolicy()
        );
        ReflectionTestUtils.setField(judge0Service, "judge0Url", "http://judge0/submissions?wait=false");
        ReflectionTestUtils.setField(judge0Service, "callbackUrl", "http://localhost:8080/api/callback/judge0");

        Problem problem = Problem.builder()
                .id(10L)
                .timeLimit(0)
                .memoryLimit(-1)
                .build();
        Submission submission = Submission.builder()
                .id(1L)
                .problem(problem)
                .judgeRunId(7L)
                .code("class Main {}")
                .build();
        TestCase testCase = TestCase.builder()
                .inputData("1")
                .expectedOutput("1")
                .build();

        when(signatureService.sign(1L, 7L, 1)).thenReturn("sig");

        judge0Service.sendSingleTest(submission, testCase, 1, 62);

        ArgumentCaptor<Judge0SubmissionDTO> dtoCaptor = ArgumentCaptor.forClass(Judge0SubmissionDTO.class);
        verify(restTemplate).postForObject(
                eq("http://judge0/submissions?wait=false&base64_encoded=true"),
                dtoCaptor.capture(),
                eq(Object.class)
        );
        Judge0SubmissionDTO dto = dtoCaptor.getValue();
        assertThat(dto.getCpuTimeLimit()).isNull();
        assertThat(dto.getMemoryLimit()).isNull();
    }

    private String decoded(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
