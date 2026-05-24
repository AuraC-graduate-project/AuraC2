package com.server.contestControl.submissionServer.service.validator;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ValidationMode;
import com.server.contestControl.submissionServer.dto.Judge0Response;
import com.server.contestControl.submissionServer.dto.Judge0SubmissionDTO;
import com.server.contestControl.submissionServer.enums.Verdict;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomValidatorServiceTest {

    @Test
    void validatorAcceptsAlternativeOutputThroughJudge0Sandbox() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        CustomValidatorService service = service(restTemplate);
        when(restTemplate.postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                org.mockito.ArgumentMatchers.any(Judge0SubmissionDTO.class),
                eq(Judge0Response.class)
        )).thenReturn(judge0Response(3, "Accepted", "ACCEPT\n"));

        CustomValidatorService.ValidatorResult result = service.validate(
                problem(),
                testCase(),
                "2 1\n"
        );

        assertThat(result.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(result.diagnostic()).isNull();

        ArgumentCaptor<Judge0SubmissionDTO> dtoCaptor = ArgumentCaptor.forClass(Judge0SubmissionDTO.class);
        verify(restTemplate).postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                dtoCaptor.capture(),
                eq(Judge0Response.class)
        );
        Judge0SubmissionDTO dto = dtoCaptor.getValue();
        assertThat(dto.getLanguageId()).isEqualTo(71);
        assertThat(decoded(dto.getSourceCode())).isEqualTo("checker source");
        assertThat(dto.getExpectedOutput()).isNull();
        assertThat(dto.getCallbackUrl()).isNull();
        assertThat(dto.getCpuTimeLimit()).isEqualTo(1.25);
        assertThat(dto.getMemoryLimit()).isEqualTo(65536);
        assertThat(decoded(dto.getStdin())).contains("3\n1 2\n", "4\n2 1\n");
    }

    @Test
    void validatorRejectsInvalidOutput() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        CustomValidatorService service = service(restTemplate);
        when(restTemplate.postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                org.mockito.ArgumentMatchers.any(Judge0SubmissionDTO.class),
                eq(Judge0Response.class)
        )).thenReturn(judge0Response(3, "Accepted", "REJECT\nnot equivalent"));

        CustomValidatorService.ValidatorResult result = service.validate(problem(), testCase(), "9 9");

        assertThat(result.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(result.diagnostic()).isEqualTo("Custom validator rejected output");
    }

    @Test
    void validatorCrashOrTimeoutMapsToInternalError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        CustomValidatorService service = service(restTemplate);
        when(restTemplate.postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                org.mockito.ArgumentMatchers.any(Judge0SubmissionDTO.class),
                eq(Judge0Response.class)
        )).thenReturn(judge0Response(5, "Time Limit Exceeded", null));

        CustomValidatorService.ValidatorResult result = service.validate(problem(), testCase(), "2 1");

        assertThat(result.verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(result.diagnostic()).contains("Custom validator execution failed");
    }

    @Test
    void invalidValidatorOutputMapsToInternalError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        CustomValidatorService service = service(restTemplate);
        when(restTemplate.postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                org.mockito.ArgumentMatchers.any(Judge0SubmissionDTO.class),
                eq(Judge0Response.class)
        )).thenReturn(judge0Response(3, "Accepted", "MAYBE\n"));

        CustomValidatorService.ValidatorResult result = service.validate(problem(), testCase(), "2 1");

        assertThat(result.verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(result.diagnostic()).isEqualTo("Custom validator produced invalid decision");
    }

    @Test
    void dispatchFailureMapsToInternalError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        CustomValidatorService service = service(restTemplate);
        when(restTemplate.postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                org.mockito.ArgumentMatchers.any(Judge0SubmissionDTO.class),
                eq(Judge0Response.class)
        )).thenThrow(new RestClientException("judge0 unavailable"));

        CustomValidatorService.ValidatorResult result = service.validate(problem(), testCase(), "2 1");

        assertThat(result.verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(result.diagnostic()).isEqualTo("Custom validator dispatch failed");
    }

    private CustomValidatorService service(RestTemplate restTemplate) {
        CustomValidatorService service = new CustomValidatorService(restTemplate);
        ReflectionTestUtils.setField(service, "judge0Url", "http://judge0/submissions?base64_encoded=true&wait=false");
        ReflectionTestUtils.setField(service, "validatorCpuTimeLimitSeconds", 1.25);
        ReflectionTestUtils.setField(service, "validatorMemoryLimitKilobytes", 65536);
        return service;
    }

    private Problem problem() {
        return Problem.builder()
                .id(10L)
                .validationMode(ValidationMode.CUSTOM_VALIDATOR)
                .validatorEnabled(true)
                .validatorLanguageId(71)
                .validatorSource("checker source")
                .validatorSourceHash("a".repeat(64))
                .build();
    }

    private TestCase testCase() {
        return TestCase.builder()
                .inputData("1 2")
                .expectedOutput("1 2")
                .build();
    }

    private Judge0Response judge0Response(int statusId, String description, String stdout) {
        Judge0Response response = new Judge0Response();
        Judge0Response.Status status = new Judge0Response.Status();
        status.setId(statusId);
        status.setDescription(description);
        response.setStatus(status);
        response.setStdout(stdout);
        return response;
    }

    private String decoded(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
