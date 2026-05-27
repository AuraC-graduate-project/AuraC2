package com.server.contestControl.contestServer.oracle.service;

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

class OracleJudge0ExecutionServiceTest {

    @Test
    void oracleExecutionUsesJudge0WaitTrueAndDecodesStdout() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        OracleJudge0ExecutionService service = service(restTemplate);
        when(restTemplate.postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                org.mockito.ArgumentMatchers.any(Judge0SubmissionDTO.class),
                eq(Judge0Response.class)
        )).thenReturn(response(3, "Accepted", Base64.getEncoder().encodeToString("YES\n".getBytes())));

        OracleJudge0ExecutionService.SandboxExecutionResult result =
                service.run("source", 54, "4\n");

        assertThat(result.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(result.stdout()).isEqualTo("YES\n");

        ArgumentCaptor<Judge0SubmissionDTO> dtoCaptor = ArgumentCaptor.forClass(Judge0SubmissionDTO.class);
        verify(restTemplate).postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                dtoCaptor.capture(),
                eq(Judge0Response.class)
        );
        assertThat(dtoCaptor.getValue().getLanguageId()).isEqualTo(54);
        assertThat(decoded(dtoCaptor.getValue().getSourceCode())).isEqualTo("source");
        assertThat(decoded(dtoCaptor.getValue().getStdin())).isEqualTo("4\n");
        assertThat(dtoCaptor.getValue().getExpectedOutput()).isNull();
        assertThat(dtoCaptor.getValue().getCallbackUrl()).isNull();
        assertThat(dtoCaptor.getValue().getCpuTimeLimit()).isEqualTo(1.5);
        assertThat(dtoCaptor.getValue().getMemoryLimit()).isEqualTo(65536);
    }

    @Test
    void oracleExecutionCanSendExpectedOutputForSynchronousExactComparison() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        OracleJudge0ExecutionService service = service(restTemplate);
        when(restTemplate.postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                org.mockito.ArgumentMatchers.any(Judge0SubmissionDTO.class),
                eq(Judge0Response.class)
        )).thenReturn(response(3, "Accepted", Base64.getEncoder().encodeToString("4\n".getBytes())));

        OracleJudge0ExecutionService.SandboxExecutionResult result =
                service.run("source", 54, "3 1", "4", 1.5, 65536);

        assertThat(result.verdict()).isEqualTo(Verdict.ACCEPTED);

        ArgumentCaptor<Judge0SubmissionDTO> dtoCaptor = ArgumentCaptor.forClass(Judge0SubmissionDTO.class);
        verify(restTemplate).postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                dtoCaptor.capture(),
                eq(Judge0Response.class)
        );
        assertThat(decoded(dtoCaptor.getValue().getExpectedOutput())).isEqualTo("4");
    }

    @Test
    void oracleDispatchFailureMapsToInternalError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        OracleJudge0ExecutionService service = service(restTemplate);
        when(restTemplate.postForObject(
                eq("http://judge0/submissions?wait=true&base64_encoded=true"),
                org.mockito.ArgumentMatchers.any(Judge0SubmissionDTO.class),
                eq(Judge0Response.class)
        )).thenThrow(new RestClientException("unavailable"));

        OracleJudge0ExecutionService.SandboxExecutionResult result =
                service.run("source", 54, "4\n");

        assertThat(result.verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(result.diagnostic()).isEqualTo("Judge0 oracle dispatch failed");
    }

    private OracleJudge0ExecutionService service(RestTemplate restTemplate) {
        OracleJudge0ExecutionService service = new OracleJudge0ExecutionService(restTemplate);
        ReflectionTestUtils.setField(service, "judge0Url", "http://judge0/submissions?wait=false");
        ReflectionTestUtils.setField(service, "oracleCpuTimeLimitSeconds", 1.5);
        ReflectionTestUtils.setField(service, "oracleMemoryLimitKilobytes", 65536);
        return service;
    }

    private Judge0Response response(int statusId, String description, String stdout) {
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
