package com.server.contestControl.submissionServer.run.controller;

import com.server.contestControl.submissionServer.run.dto.CreateCustomTestCaseRequest;
import com.server.contestControl.submissionServer.run.dto.CustomTestCaseResponse;
import com.server.contestControl.submissionServer.run.dto.RunRequest;
import com.server.contestControl.submissionServer.run.dto.RunResponse;
import com.server.contestControl.submissionServer.run.dto.UpdateCustomTestCaseRequest;
import com.server.contestControl.submissionServer.run.service.TeamRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/team/problems/{problemId}")
@PreAuthorize("hasRole('TEAM')")
@RequiredArgsConstructor
public class TeamRunController {

    private final TeamRunService teamRunService;

    @GetMapping("/custom-tests")
    public List<CustomTestCaseResponse> customTests(
            @PathVariable Long problemId,
            Authentication authentication
    ) {
        return teamRunService.getCustomTests(problemId, authentication.getName());
    }

    @PostMapping("/custom-tests")
    public CustomTestCaseResponse createCustomTest(
            @PathVariable Long problemId,
            @RequestBody CreateCustomTestCaseRequest request,
            Authentication authentication
    ) {
        return teamRunService.createCustomTest(problemId, request, authentication.getName());
    }

    @PutMapping("/custom-tests/{customTestId}")
    public CustomTestCaseResponse updateCustomTest(
            @PathVariable Long problemId,
            @PathVariable Long customTestId,
            @RequestBody UpdateCustomTestCaseRequest request,
            Authentication authentication
    ) {
        return teamRunService.updateCustomTest(problemId, customTestId, request, authentication.getName());
    }

    @DeleteMapping("/custom-tests/{customTestId}")
    public ResponseEntity<Void> deleteCustomTest(
            @PathVariable Long problemId,
            @PathVariable Long customTestId,
            Authentication authentication
    ) {
        teamRunService.deleteCustomTest(problemId, customTestId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/run")
    public RunResponse run(
            @PathVariable Long problemId,
            @RequestBody RunRequest request,
            Authentication authentication
    ) {
        return teamRunService.run(problemId, request, authentication.getName());
    }
}
