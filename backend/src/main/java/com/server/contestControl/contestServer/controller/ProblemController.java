package com.server.contestControl.contestServer.controller;

import com.server.contestControl.contestServer.dto.problem.ProblemRequest;
import com.server.contestControl.contestServer.dto.problem.ProblemResponse;
import com.server.contestControl.contestServer.dto.problem.ProblemUpdateRequest;
import com.server.contestControl.contestServer.moderation.service.ContestTeamModerationService;
import jakarta.validation.Valid;
import com.server.contestControl.contestServer.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;
    private final ContestTeamModerationService moderationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProblemResponse> createProblem(@Valid @RequestBody ProblemRequest request) {
        return ResponseEntity.ok(problemService.createProblem(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProblemResponse> updateProblem(
            @PathVariable Long id,
            @Valid @RequestBody ProblemUpdateRequest request
    ) {
        return ResponseEntity.ok(problemService.updateProblem(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEAM')")
    public ResponseEntity<ProblemResponse> getProblem(@PathVariable Long id, Authentication authentication) {
        ProblemResponse response = problemService.getProblem(id, isAdmin(authentication));
        if (!isAdmin(authentication)) {
            moderationService.assertWorkspaceVisible(response.getContestId(), authentication.getName());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/contest/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEAM')")
    public ResponseEntity<List<ProblemResponse>> getProblemsByContest(
            @PathVariable Long id,
            Authentication authentication
    ) {
        if (!isAdmin(authentication)) {
            moderationService.assertWorkspaceVisible(id, authentication.getName());
        }
        return ResponseEntity.ok(problemService.getAllProblems(id, isAdmin(authentication)));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
