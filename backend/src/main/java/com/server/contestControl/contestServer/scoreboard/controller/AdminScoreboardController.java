package com.server.contestControl.contestServer.scoreboard.controller;

import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRevealResponse;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.service.ScoreboardRevealService;
import com.server.contestControl.contestServer.scoreboard.service.ScoreboardService;
import com.server.contestControl.contestServer.scoreboard.sse.ScoreboardSseAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/scoreboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminScoreboardController {

    private final ScoreboardService scoreboardService;
    private final ScoreboardRevealService revealService;
    private final ScoreboardSseAdapter scoreboardSseAdapter;

    @GetMapping("/contests/{contestId}")
    public ScoreboardSnapshot adminScoreboard(@PathVariable Long contestId) {
        return scoreboardService.getAdminSnapshot(contestId);
    }

    @GetMapping("/contests/{contestId}/reveal")
    public ScoreboardRevealResponse revealState(@PathVariable Long contestId) {
        return revealService.getState(contestId);
    }

    @PostMapping("/contests/{contestId}/reveal/start")
    public ScoreboardRevealResponse startReveal(@PathVariable Long contestId) {
        ScoreboardRevealResponse response = revealService.start(contestId);
        if (response.status().name().equals("COMPLETED")) {
            scoreboardSseAdapter.publishRevealStep(contestId, "REVEAL_START_COMPLETED");
        } else {
            scoreboardSseAdapter.publishFreeze(contestId, "REVEAL_START");
        }
        return response;
    }

    @PostMapping("/contests/{contestId}/reveal/next")
    public ScoreboardRevealResponse revealNext(@PathVariable Long contestId) {
        ScoreboardRevealResponse response = revealService.revealNext(contestId);
        scoreboardSseAdapter.publishRevealStep(contestId, "REVEAL_NEXT");
        return response;
    }

    @PostMapping("/contests/{contestId}/reveal/all")
    public ScoreboardRevealResponse revealAll(@PathVariable Long contestId) {
        ScoreboardRevealResponse response = revealService.revealAll(contestId);
        scoreboardSseAdapter.publishRevealStep(contestId, "REVEAL_ALL");
        return response;
    }

    @PostMapping("/contests/{contestId}/reveal/reset")
    public ScoreboardRevealResponse resetReveal(@PathVariable Long contestId) {
        ScoreboardRevealResponse response = revealService.reset(contestId);
        scoreboardSseAdapter.publishFreeze(contestId, "REVEAL_RESET");
        return response;
    }
}
