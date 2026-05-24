package com.server.contestControl.submissionServer.service.validator;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
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

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomValidatorService {

    private final RestTemplate judge0RestTemplate;

    @Value("${judge0.url}")
    private String judge0Url;

    @Value("${judge0.validator.cpu-time-limit-seconds:2.0}")
    private Double validatorCpuTimeLimitSeconds;

    @Value("${judge0.validator.memory-limit-kilobytes:131072}")
    private Integer validatorMemoryLimitKilobytes;

    public ValidatorResult validate(Problem problem, TestCase testCase, String actualOutput) {
        if (problem == null
                || !problem.hasActiveCustomValidator()
                || problem.getValidatorLanguageId() == null
                || problem.getValidatorSource() == null
                || problem.getValidatorSource().isBlank()) {
            return ValidatorResult.internalError("Custom validator is not configured");
        }

        Judge0SubmissionDTO dto = new Judge0SubmissionDTO(
                problem.getValidatorSource(),
                problem.getValidatorLanguageId(),
                buildValidatorInput(
                        testCase == null ? null : testCase.getInputData(),
                        testCase == null ? null : testCase.getExpectedOutput(),
                        actualOutput
                ),
                null,
                null,
                validatorCpuTimeLimitSeconds,
                validatorMemoryLimitKilobytes
        ).base64Encoded();

        Judge0Response response;
        try {
            response = judge0RestTemplate.postForObject(validatorJudge0Url(), dto, Judge0Response.class);
        } catch (RestClientException ex) {
            log.warn(
                    "Custom validator Judge0 request failed. problemId={} cause={}: {}",
                    problem.getId(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            return ValidatorResult.internalError("Custom validator dispatch failed");
        }

        return toValidatorResult(response);
    }

    String buildValidatorInput(String inputData, String expectedOutput, String actualOutput) {
        return section(inputData) + section(expectedOutput) + section(actualOutput);
    }

    String validatorJudge0Url() {
        return UriComponentsBuilder.fromUriString(judge0Url)
                .replaceQueryParam("wait", "true")
                .replaceQueryParam("base64_encoded", "true")
                .toUriString();
    }

    private ValidatorResult toValidatorResult(Judge0Response response) {
        if (response == null || response.getStatus() == null) {
            return ValidatorResult.internalError("Custom validator returned no status");
        }

        if (response.getStatus().getId() != 3) {
            return ValidatorResult.internalError(
                    Judge0AuditUtil.firstSafeDiagnostic(
                            "Custom validator execution failed: "
                                    + Judge0AuditUtil.safeStatusDescription(response.getStatus().getDescription()),
                            response.getDecodedCompileOutput(),
                            response.getDecodedMessage(),
                            response.getDecodedStderr()
                    )
            );
        }

        String decision = firstDecisionLine(response.getDecodedStdout());
        if (decision == null) {
            return ValidatorResult.internalError("Custom validator produced no decision");
        }

        return switch (decision) {
            case "ACCEPT", "ACCEPTED", "OK" -> ValidatorResult.accepted();
            case "REJECT", "REJECTED", "WRONG_ANSWER", "WA" ->
                    ValidatorResult.wrongAnswer("Custom validator rejected output");
            default -> ValidatorResult.internalError("Custom validator produced invalid decision");
        };
    }

    private String firstDecisionLine(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return null;
        }

        String normalized = stdout.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n")) {
            if (!line.isBlank()) {
                return line.trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private String section(String value) {
        String normalized = value == null ? "" : value;
        int byteLength = normalized.getBytes(StandardCharsets.UTF_8).length;
        return byteLength + "\n" + normalized + "\n";
    }

    public record ValidatorResult(Verdict verdict, String diagnostic) {
        public static ValidatorResult accepted() {
            return new ValidatorResult(Verdict.ACCEPTED, null);
        }

        public static ValidatorResult wrongAnswer(String diagnostic) {
            return new ValidatorResult(Verdict.WRONG_ANSWER, diagnostic);
        }

        public static ValidatorResult internalError(String diagnostic) {
            return new ValidatorResult(Verdict.INTERNAL_ERROR, diagnostic);
        }
    }
}
