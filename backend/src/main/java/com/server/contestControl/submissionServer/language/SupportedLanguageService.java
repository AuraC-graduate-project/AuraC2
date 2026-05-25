package com.server.contestControl.submissionServer.language;

import com.server.contestControl.submissionServer.exceptions.UnsupportedLanguageException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupportedLanguageService {

    public List<SupportedLanguageResponse> supportedLanguages() {
        return SupportedLanguageCatalog.languages().stream()
                .map(SupportedLanguageResponse::from)
                .toList();
    }

    public SupportedLanguage requireByValue(String value) {
        return SupportedLanguageCatalog.findByValueOrAlias(value)
                .orElseThrow(() -> new UnsupportedLanguageException(value));
    }

    public SupportedLanguage requireByJudge0LanguageId(Integer judge0LanguageId) {
        return SupportedLanguageCatalog.findByJudge0LanguageId(judge0LanguageId)
                .orElseThrow(() -> new UnsupportedLanguageException(String.valueOf(judge0LanguageId)));
    }
}
