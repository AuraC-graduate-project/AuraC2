package com.server.contestControl.contestServer.oracle.service;

import com.server.contestControl.submissionServer.dto.Judge0Response;
import com.server.contestControl.submissionServer.dto.Judge0SubmissionDTO;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.util.Judge0AuditUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class OracleJudge0ExecutionService {

    private final RestTemplate judge0RestTemplate;

    @Value("${judge0.url}")
    private String judge0Url;

    @Value("${judge0.oracle.cpu-time-limit-seconds:2.0}")
    private Double oracleCpuTimeLimitSeconds;

    @Value("${judge0.oracle.memory-limit-kilobytes:131072}")
    private Integer oracleMemoryLimitKilobytes;

    public SandboxExecutionResult run(String source, int languageId, String stdin) {
        Judge0SubmissionDTO dto = new Judge0SubmissionDTO(
                source,
                languageId,
                stdin,
                null,
                null,
                oracleCpuTimeLimitSeconds,
                oracleMemoryLimitKilobytes
        ).base64Encoded();

        try {
            Judge0Response response = judge0RestTemplate.postForObject(
                    synchronousJudge0Url(),
                    dto,
                    Judge0Response.class
            );
            return fromResponse(response);
        } catch (RestClientException ex) {
            log.warn(
                    "Oracle Judge0 execution failed to dispatch. cause={}: {}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            return SandboxExecutionResult.internalError("Judge0 oracle dispatch failed");
        }
    }

    String synchronousJudge0Url() {
        return UriComponentsBuilder.fromUriString(judge0Url)
                .replaceQueryParam("wait", "true")
                .replaceQueryParam("base64_encoded", "true")
                .toUriString();
    }

    private SandboxExecutionResult fromResponse(Judge0Response response) {
        if (response == null || response.getStatus() == null) {
            return SandboxExecutionResult.internalError("Judge0 oracle returned no status");
        }

        Verdict verdict = Verdict.fromJudge0Status(response.getStatus().getId());
        return new SandboxExecutionResult(
                verdict,
                response.getStatus().getId(),
                Judge0AuditUtil.safeStatusDescription(response.getStatus().getDescription()),
                response.getDecodedStdout(),
                response.getTimeAsInt(),
                response.getMemoryAsInt(),
                Judge0AuditUtil.firstSafeDiagnostic(
                        response.getDecodedCompileOutput(),
                        response.getDecodedMessage(),
                        response.getDecodedStderr()
                )
        );
    }

    public record SandboxExecutionResult(
            Verdict verdict,
            Integer statusId,
            String statusDescription,
            String stdout,
            Integer executionTime,
            Integer memoryUsage,
            String diagnostic
    ) {
        static SandboxExecutionResult internalError(String diagnostic) {
            return new SandboxExecutionResult(
                    Verdict.INTERNAL_ERROR,
                    null,
                    null,
                    null,
                    0,
                    0,
                    Judge0AuditUtil.firstSafeDiagnostic(diagnostic)
            );
        }

        boolean accepted() {
            return verdict == Verdict.ACCEPTED;
        }
    }
}
