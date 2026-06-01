package com.server.contestControl.contestServer.prompt.controller;

import com.server.contestControl.contestServer.prompt.service.PromptExportService;
import com.server.contestControl.contestServer.prompt.dto.PromptExportRequest;
import com.server.contestControl.contestServer.prompt.dto.PromptExportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/prompt-exports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PromptExportController {

    private final PromptExportService promptExportService;

    @PostMapping("/problems/{problemId}/preview")
    public ResponseEntity<PromptExportResponse> preview(
            @PathVariable Long problemId,
            @Valid @RequestBody PromptExportRequest request
    ) {
        return ResponseEntity.ok(promptExportService.preview(problemId, request));
    }
}
