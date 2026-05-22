package com.server.contestControl.submissionServer.service.callback;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Judge0CallbackSignatureServiceTest {

    @Test
    void validSignatureBindsSubmissionRunAndTestCase() {
        Judge0CallbackSignatureService signatureService = signatureService("test-secret", "dev");

        String signature = signatureService.sign(1L, 7L, 2);

        assertThat(signatureService.isValid(1L, 7L, 2, signature)).isTrue();
        assertThat(signatureService.isValid(2L, 7L, 2, signature)).isFalse();
        assertThat(signatureService.isValid(1L, 8L, 2, signature)).isFalse();
        assertThat(signatureService.isValid(1L, 7L, 3, signature)).isFalse();
    }

    @Test
    void missingOrBlankSignatureIsRejected() {
        Judge0CallbackSignatureService signatureService = signatureService("test-secret", "dev");

        assertThat(signatureService.isValid(1L, 7L, 2, null)).isFalse();
        assertThat(signatureService.isValid(1L, 7L, 2, " ")).isFalse();
    }

    @Test
    void prodProfileRejectsDevFallbackSecret() {
        assertThatThrownBy(() -> signatureService(Judge0CallbackSignatureService.DEV_FALLBACK_SECRET, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JUDGE0_CALLBACK_SECRET");
    }

    @Test
    void blankSecretIsRejected() {
        assertThatThrownBy(() -> signatureService(" ", "dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("judge0.callback-secret");
    }

    private Judge0CallbackSignatureService signatureService(String secret, String... profiles) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(profiles);
        return new Judge0CallbackSignatureService(secret, environment);
    }
}
