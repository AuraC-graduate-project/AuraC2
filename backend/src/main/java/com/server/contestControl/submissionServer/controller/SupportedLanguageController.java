package com.server.contestControl.submissionServer.controller;

import com.server.contestControl.submissionServer.language.SupportedLanguageResponse;
import com.server.contestControl.submissionServer.language.SupportedLanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/languages")
@RequiredArgsConstructor
public class SupportedLanguageController {

    private final SupportedLanguageService supportedLanguageService;

    @GetMapping("/supported")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEAM')")
    public List<SupportedLanguageResponse> supportedLanguages() {
        return supportedLanguageService.supportedLanguages();
    }
}
