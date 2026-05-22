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

@Service
@Slf4j
@RequiredArgsConstructor
public class Judge0Service {

    private final Judge0CallbackSignatureService callbackSignatureService;

    @Value("${judge0.url}")
    private String judge0Url;

    @Value("${judge0.callback}")
    private String callbackUrl;

    public void sendSingleTest(Submission submission, TestCase tc, int testCaseNumber, int languageId) {

        Judge0SubmissionDTO dto = new Judge0SubmissionDTO(
                submission.getCode(),
                languageId,
                tc.getInputData(),
                tc.getExpectedOutput(),
                buildSignedCallbackUrl(submission, testCaseNumber)
        );

        new RestTemplate().postForObject(judge0Url, dto, Object.class);

        log.info(
                "Sent test case to Judge0. submissionId={} judgeRunId={} testCaseNumber={}",
                submission.getId(),
                submission.getJudgeRunId(),
                testCaseNumber
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
}
