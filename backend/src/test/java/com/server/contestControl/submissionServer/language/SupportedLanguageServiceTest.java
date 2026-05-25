package com.server.contestControl.submissionServer.language;

import com.server.contestControl.submissionServer.exceptions.UnsupportedLanguageException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportedLanguageServiceTest {

    private final SupportedLanguageService service = new SupportedLanguageService();

    @Test
    void resolvesSupportedLanguageAliases() {
        SupportedLanguage language = service.requireByValue("python3");

        assertThat(language.value()).isEqualTo("python");
        assertThat(language.judge0LanguageId()).isEqualTo(71);
    }

    @Test
    void rejectsUnsupportedLanguage() {
        assertThatThrownBy(() -> service.requireByValue("ruby"))
                .isInstanceOf(UnsupportedLanguageException.class)
                .hasMessageContaining("ruby");
    }
}
