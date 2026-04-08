package com.server.contestControl.contestServer.controller;

import com.server.contestControl.contestServer.dto.testcase.TestCaseRequest;
import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;
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
            @RequestBody TestCaseRequest request
    ) {
        return ResponseEntity.ok(testCaseService.addTestCase(problemId, request));
    }

    @GetMapping("/problem/{problemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEAM')")
    public ResponseEntity<List<TestCaseResponse>> getTestCases(
            @PathVariable Long problemId
    ) {
        return ResponseEntity.ok(testCaseService.getTestCases(problemId));
    }
}