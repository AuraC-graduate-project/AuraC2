package com.server.contestControl.submissionServer.service.judge;

import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.submissionServer.dto.Judge0SubmissionDTO;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.service.callback.Judge0CallbackSignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
@RequiredArgsConstructor
public class Judge0Service {

    private final Judge0CallbackSignatureService callbackSignatureService;
    private final RestTemplate judge0RestTemplate;
    private final Judge0ExpectedOutputPolicy expectedOutputPolicy;

    @Value("${judge0.url}")
    private String judge0Url;

    @Value("${judge0.callback}")
    private String callbackUrl;

    public void sendSingleTest(Submission submission, TestCase tc, int testCaseNumber, int languageId) {

        Judge0SubmissionDTO dto = new Judge0SubmissionDTO(
                submission.getCode(),
                languageId,
                tc.getInputData(),
                expectedOutputForJudge0(submission, tc),
                buildSignedCallbackUrl(submission, testCaseNumber),
                toJudge0CpuTimeLimitSeconds(submission.getProblem().getTimeLimit()),
                toJudge0MemoryLimitKilobytes(submission.getProblem().getMemoryLimit())
        ).base64Encoded();

        judge0RestTemplate.postForObject(judge0SubmissionUrl(), dto, Object.class);

        log.info(
                "Sent test case to Judge0. submissionId={} judgeRunId={} testCaseNumber={}",
                submission.getId(),
                submission.getJudgeRunId(),
                testCaseNumber
        );
    }

    Double toJudge0CpuTimeLimitSeconds(Integer timeLimitMillis) {
        if (timeLimitMillis == null || timeLimitMillis <= 0) {
            return null;
        }

        return BigDecimal.valueOf(timeLimitMillis)
                .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .doubleValue();
    }

    Integer toJudge0MemoryLimitKilobytes(Integer memoryLimitMegabytes) {
        if (memoryLimitMegabytes == null || memoryLimitMegabytes <= 0) {
            return null;
        }

        return Math.multiplyExact(memoryLimitMegabytes, 1024);
    }

    String expectedOutputForJudge0(Submission submission, TestCase testCase) {
        return expectedOutputPolicy.expectedOutputForJudge0(
                submission.getProblem(),
                testCase.getExpectedOutput()
        );
    }

    String buildSignedCallbackUrl(Submission submission, int testCaseNumber) {
        String signature = callbackSignatureService.sign(
                submission.getId(),
                submission.getJudgeRunId(),
                testCaseNumber
        );

        return UriComponentsBuilder.fromUriString(callbackUrl)
                .pathSegment(
                        String.valueOf(submission.getId()),
                        String.valueOf(submission.getJudgeRunId()),
                        String.valueOf(testCaseNumber)
                )
                .queryParam("signature", signature)
                .toUriString();
    }

    String judge0SubmissionUrl() {
        return UriComponentsBuilder.fromUriString(judge0Url)
                .replaceQueryParam("base64_encoded", "true")
                .toUriString();
    }
}
