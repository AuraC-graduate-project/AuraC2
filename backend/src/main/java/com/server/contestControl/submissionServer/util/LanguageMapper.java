package com.server.contestControl.submissionServer.util;

import com.server.contestControl.submissionServer.language.SupportedLanguageCatalog;

public class LanguageMapper {

    public static int convertLanguage(String language) {
        return SupportedLanguageCatalog.findByValueOrAlias(language)
                .map(supportedLanguage -> supportedLanguage.judge0LanguageId())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + language));
    }
}
