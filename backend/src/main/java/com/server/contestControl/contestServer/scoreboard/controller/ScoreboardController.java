package com.server.contestControl.contestServer.scoreboard.controller;

import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.service.ScoreboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scoreboard")
@RequiredArgsConstructor
public class ScoreboardController {

    private final ScoreboardService scoreboardService;

    @GetMapping("/contests/{contestId}")
    public ScoreboardSnapshot publicScoreboard(@PathVariable Long contestId) {
        return scoreboardService.getPublicSnapshot(contestId);
    }
}
