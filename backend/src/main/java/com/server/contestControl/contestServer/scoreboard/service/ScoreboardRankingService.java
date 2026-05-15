package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class ScoreboardRankingService {

    public List<ScoreboardRow> rankRows(List<ScoreboardRow> unrankedRows) {
        List<ScoreboardRow> sorted = unrankedRows.stream()
                .sorted(Comparator
                        .comparing(ScoreboardRow::solvedCount).reversed()
                        .thenComparing(ScoreboardRow::totalPenalty)
                        .thenComparing(row -> row.teamName().toLowerCase())
                        .thenComparing(ScoreboardRow::teamId))
                .toList();

        int previousSolved = Integer.MIN_VALUE;
        int previousPenalty = Integer.MIN_VALUE;
        int currentRank = 0;

        java.util.ArrayList<ScoreboardRow> ranked = new java.util.ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            ScoreboardRow row = sorted.get(i);
            if (i == 0
                    || row.solvedCount() != previousSolved
                    || row.totalPenalty() != previousPenalty) {
                currentRank = i + 1;
                previousSolved = row.solvedCount();
                previousPenalty = row.totalPenalty();
            }

            ranked.add(new ScoreboardRow(
                    currentRank,
                    row.teamId(),
                    row.teamName(),
                    row.solvedCount(),
                    row.totalPenalty(),
                    row.problemCells()
            ));
        }

        return List.copyOf(ranked);
    }
}
