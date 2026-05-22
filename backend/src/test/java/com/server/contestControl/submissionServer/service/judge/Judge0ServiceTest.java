package com.server.contestControl.submissionServer.service.judge;

import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.service.callback.Judge0CallbackSignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Judge0ServiceTest {

    @Test
    void callbackUrlIncludesSignatureBoundToSubmissionRunAndTestCase() {
        Judge0CallbackSignatureService signatureService = mock(Judge0CallbackSignatureService.class);
        Judge0Service judge0Service = new Judge0Service(signatureService);
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
}
