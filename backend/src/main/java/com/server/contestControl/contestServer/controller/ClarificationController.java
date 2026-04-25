package com.server.contestControl.contestServer.controller;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.exception.api.UserNotFoundException;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.dto.clarification.ClarificationRequest;
import com.server.contestControl.contestServer.dto.clarification.ClarificationResponse;
import com.server.contestControl.contestServer.dto.clarification.ReplyRequest;
import com.server.contestControl.contestServer.service.ClarificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clarifications")
@RequiredArgsConstructor
public class ClarificationController {

    private final ClarificationService clarificationService;
    private final UserRepository userRepository;

    /**
     * TEAM Endpoint: Submit a clarification question
     * POST /api/clarifications
     */
    @PostMapping
    @PreAuthorize("hasRole('TEAM')")
    public ResponseEntity<ClarificationResponse> submitClarification(
            @RequestBody ClarificationRequest request) {

        User user = getCurrentUser();
        ClarificationResponse response = clarificationService.submitClarification(request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * TEAM Endpoint: Fetch my clarifications + all public clarifications in a contest
     * GET /api/clarifications/my/{contestId}
     */
    @GetMapping("/my/{contestId}")
    @PreAuthorize("hasRole('TEAM')")
    public ResponseEntity<List<ClarificationResponse>> getMyClarifications(
            @PathVariable Long contestId) {

        User user = getCurrentUser();
        List<ClarificationResponse> clarifications = clarificationService
                .fetchClarificationsForTeam(contestId, user);
        return ResponseEntity.ok(clarifications);
    }

    /**
     * ADMIN Endpoint: Fetch all clarifications for a contest
     * GET /api/clarifications/admin/contest/{contestId}
     */
    @GetMapping("/admin/contest/{contestId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ClarificationResponse>> getContestClarifications(
            @PathVariable Long contestId) {

        List<ClarificationResponse> clarifications = clarificationService
                .fetchClarificationsForAdmin(contestId);
        return ResponseEntity.ok(clarifications);
    }

    /**
     * ADMIN Endpoint: Fetch all clarifications across all contests
     * GET /api/clarifications/admin/all
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ClarificationResponse>> getAllClarifications() {

        List<ClarificationResponse> clarifications = clarificationService
                .fetchAllClarificationsForAdmin();
        return ResponseEntity.ok(clarifications);
    }

    /**
     * ADMIN Endpoint: Reply to a clarification
     * PUT /api/clarifications/admin/{id}/reply
     */
    @PutMapping("/admin/{id}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClarificationResponse> replyClarification(
            @PathVariable Long id,
            @RequestBody ReplyRequest request) {

        User admin = getCurrentUser();
        ClarificationResponse response = clarificationService.replyClarification(id, request, admin);
        return ResponseEntity.ok(response);
    }

    /**
     * PUBLIC Endpoint: Fetch public clarifications for a contest (no auth required)
     * GET /api/clarifications/public/{contestId}
     */
    @GetMapping("/public/{contestId}")
    public ResponseEntity<List<ClarificationResponse>> getPublicClarifications(
            @PathVariable Long contestId) {

        List<ClarificationResponse> clarifications = clarificationService
                .fetchPublicClarifications(contestId);
        return ResponseEntity.ok(clarifications);
    }

    /**
     *  get the current authenticated user
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UserNotFoundException(auth.getName()));
    }
}
