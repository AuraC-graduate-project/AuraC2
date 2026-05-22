package com.server.contestControl.submissionServer.service.callback;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

@Service
public class Judge0CallbackSignatureService {

    static final String DEV_FALLBACK_SECRET = "dev-only-change-me";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String callbackSecret;

    public Judge0CallbackSignatureService(
            @Value("${judge0.callback-secret:}") String callbackSecret,
            Environment environment
    ) {
        this.callbackSecret = callbackSecret == null ? "" : callbackSecret.trim();
        validateConfiguration(environment);
    }

    public String sign(Long submissionId, Long judgeRunId, int testCaseNumber) {
        String payload = payload(submissionId, judgeRunId, testCaseNumber);

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(callbackSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign Judge0 callback URL", ex);
        }
    }

    public boolean isValid(Long submissionId, Long judgeRunId, int testCaseNumber, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            byte[] expected = sign(submissionId, judgeRunId, testCaseNumber).getBytes(StandardCharsets.UTF_8);
            byte[] provided = signature.trim().getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, provided);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String payload(Long submissionId, Long judgeRunId, int testCaseNumber) {
        if (submissionId == null || judgeRunId == null || testCaseNumber < 1) {
            throw new IllegalArgumentException("Callback signature payload is incomplete");
        }

        return submissionId + ":" + judgeRunId + ":" + testCaseNumber;
    }

    private void validateConfiguration(Environment environment) {
        if (callbackSecret.isBlank()) {
            throw new IllegalStateException("judge0.callback-secret must be configured");
        }

        boolean prodProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("prod"::equalsIgnoreCase);
        if (prodProfile && DEV_FALLBACK_SECRET.equals(callbackSecret)) {
            throw new IllegalStateException("JUDGE0_CALLBACK_SECRET must be set to a production-safe value");
        }
    }
}
