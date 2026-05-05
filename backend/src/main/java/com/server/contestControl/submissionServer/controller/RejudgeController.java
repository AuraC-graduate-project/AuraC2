package com.server.contestControl.submissionServer.controller;

import com.server.contestControl.submissionServer.dto.RejudgeResponse;
import com.server.contestControl.submissionServer.dto.RejudgeSubmissionsRequest;
import com.server.contestControl.submissionServer.service.rejudge.RejudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/rejudge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RejudgeController {

    private final RejudgeService rejudgeService;

    @PostMapping("/submissions")
    public ResponseEntity<RejudgeResponse> rejudgeSubmissions(
            @RequestBody RejudgeSubmissionsRequest request
    ) {
        return ResponseEntity.ok(
                rejudgeService.rejudgeSelectedSubmissions(
                        request == null ? null : request.submissionIds()
                )
        );
    }

    @PostMapping("/problem/{problemId}")
    public ResponseEntity<RejudgeResponse> rejudgeProblem(@PathVariable Long problemId) {
        return ResponseEntity.ok(rejudgeService.rejudgeProblem(problemId));
    }

    @PostMapping("/contests/{contestId}")
    public ResponseEntity<RejudgeResponse> rejudgeContest(@PathVariable Long contestId) {
        return ResponseEntity.ok(rejudgeService.rejudgeContest(contestId));
    }
}
