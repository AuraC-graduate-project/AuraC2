package com.server.contestControl.submissionServer.controller;

import com.server.contestControl.submissionServer.dto.SubmissionRequest;
import com.server.contestControl.submissionServer.dto.SubmissionResponse;

import com.server.contestControl.submissionServer.service.submission.SubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TEAM', 'ADMIN')")
    public SubmissionResponse submit(@RequestBody SubmissionRequest request) {
        return submissionService.submitCode(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEAM', 'ADMIN')")
    public SubmissionResponse getSubmission(@PathVariable Long id) {
        return submissionService.getSubmissionById(id);
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('TEAM', 'ADMIN')")
    public List<SubmissionResponse> getAllSubmission(Long problemID, HttpServletRequest request) {
        return submissionService.getAllSubmissionByProblem(problemID, request);
    }
    @GetMapping("/my/all")
    @PreAuthorize("hasRole('TEAM')")
    public List<SubmissionResponse> getAllMySubmissions(HttpServletRequest request) {
        return submissionService.getAllSubmissionsForUser(request);
    }
}