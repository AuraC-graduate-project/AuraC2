package com.server.contestControl.contestServer.controller;

import com.server.contestControl.contestServer.dto.contest.ContestRequest;
import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.service.ContestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contest")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContestResponse> createContest(
            @RequestBody ContestRequest request) {

        return ResponseEntity.ok(contestService.createContest(request));
    }

    @PutMapping("/{id}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContestResponse> startContest(@PathVariable Long id) {
        return ResponseEntity.ok(contestService.updateStatus(id, ContestStatus.RUNNING));
    }

    @PutMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContestResponse> pauseContest(@PathVariable Long id) {
        return ResponseEntity.ok(contestService.updateStatus(id, ContestStatus.PAUSED));
    }

    @PutMapping("/{id}/end")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContestResponse> endContest(@PathVariable Long id) {
        return ResponseEntity.ok(contestService.updateStatus(id, ContestStatus.ENDED));
    }

    @GetMapping("/active")
    public ResponseEntity<ContestResponse> getActive() {
        return ResponseEntity.ok(contestService.getActiveContest());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<ContestResponse> getUpcoming() {
        return ResponseEntity.ok(contestService.getUpcomingContest());
    }

    @GetMapping("/paused")
    public ResponseEntity<ContestResponse> getPaused() {
        return ResponseEntity.ok(contestService.getPausedContest());
    }

    @GetMapping("/ended")
    public ResponseEntity<List<ContestResponse>> getEnded() {
        return ResponseEntity.ok(contestService.getEndedContests());
    }


}

