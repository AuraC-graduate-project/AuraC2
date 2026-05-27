package com.server.contestControl.contestServer.controller;

import com.server.contestControl.contestServer.dto.testcase.TestCaseRequest;
import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;
import com.server.contestControl.contestServer.dto.testcase.TestCaseUpdateRequest;
import com.server.contestControl.contestServer.dto.testcase.PublicTestCaseResponse;
import jakarta.validation.Valid;
import com.server.contestControl.contestServer.moderation.service.ContestTeamModerationService;
import com.server.contestControl.contestServer.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/testcases")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final ContestTeamModerationService moderationService;

    @PostMapping("/{problemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestCaseResponse> addTestCase(
            @PathVariable Long problemId,
            @Valid @RequestBody TestCaseRequest request
    ) {
        return ResponseEntity.ok(testCaseService.addTestCase(problemId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestCaseResponse> updateTestCase(
            @PathVariable Long id,
            @Valid @RequestBody TestCaseUpdateRequest request
    ) {
        return ResponseEntity.ok(testCaseService.updateTestCase(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTestCase(@PathVariable Long id) {
        testCaseService.deleteTestCase(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/problem/{problemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TestCaseResponse>> getTestCases(
            @PathVariable Long problemId
    ) {
        return ResponseEntity.ok(testCaseService.getAdminTestCases(problemId));
    }

    @GetMapping("/public/problem/{problemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEAM')")
    public ResponseEntity<List<PublicTestCaseResponse>> getPublicTestCases(
            @PathVariable Long problemId,
            Authentication authentication
    ) {
        if (authentication != null && authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            moderationService.assertProblemWorkspaceVisible(problemId, authentication.getName());
        }
        return ResponseEntity.ok(testCaseService.getPublicTestCases(problemId));
    }
}
