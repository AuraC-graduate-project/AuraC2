package com.server.contestControl.submissionServer.service.judge;

import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.submissionServer.dto.Judge0SubmissionDTO;
import com.server.contestControl.submissionServer.entity.Submission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class Judge0Service {

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
                callbackUrl + "/" + submission.getId() + "/" + testCaseNumber
        );

        new RestTemplate().postForObject(judge0Url, dto, Object.class);

        log.info("Sent Test Case {} → submission {}", testCaseNumber, submission.getId());
    }
}
