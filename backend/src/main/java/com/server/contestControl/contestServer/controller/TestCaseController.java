package com.server.contestControl.contestServer.controller;

import com.server.contestControl.contestServer.dto.testcase.TestCaseRequest;
import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;
import com.server.contestControl.contestServer.dto.testcase.TestCaseUpdateRequest;
import jakarta.validation.Valid;
import com.server.contestControl.contestServer.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/testcases")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;

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
    @PreAuthorize("hasAnyRole('ADMIN', 'TEAM')")
    public ResponseEntity<List<TestCaseResponse>> getTestCases(
            @PathVariable Long problemId
    ) {
        return ResponseEntity.ok(testCaseService.getTestCases(problemId));
    }
}
