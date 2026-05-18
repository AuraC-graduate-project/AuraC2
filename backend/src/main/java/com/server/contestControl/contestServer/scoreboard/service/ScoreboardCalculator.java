package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardProblemCell;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow;
import com.server.contestControl.contestServer.scoreboard.model.ScoreboardCellKey;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoreboardCalculator {

    private static final Set<Verdict> WRONG_PENALTY_VERDICTS = EnumSet.of(
            Verdict.WRONG_ANSWER,
            Verdict.TLE,
            Verdict.COMPILATION_ERROR,
            Verdict.RUNTIME_ERROR
    );

    private static final Set<Verdict> TERMINAL_VERDICTS = EnumSet.of(
            Verdict.ACCEPTED,
            Verdict.WRONG_ANSWER,
            Verdict.TLE,
            Verdict.COMPILATION_ERROR,
            Verdict.RUNTIME_ERROR,
            Verdict.INTERNAL_ERROR
    );

    private static final Set<Verdict> ACTIVE_VERDICTS = EnumSet.of(
            Verdict.PENDING,
            Verdict.PENDING_REJUDGE,
            Verdict.RUNNING
    );

    private final ScoreboardRankingService rankingService;

    public List<ScoreboardRow> calculateRows(
            Contest contest,
            List<Problem> problems,
            List<User> teams,
            List<Submission> visibleSubmissions,
            Set<ScoreboardCellKey> hiddenCells,
            Set<ScoreboardCellKey> revealedCells
    ) {
        Map<Long, Integer> problemIndex = new HashMap<>();
        for (int i = 0; i < problems.size(); i++) {
            problemIndex.put(problems.get(i).getId(), i);
        }

        Map<ScoreboardCellKey, List<Submission>> submissionsByCell = visibleSubmissions.stream()
                .filter(this::isTeamSubmission)
                .filter(submission -> problemIndex.containsKey(submission.getProblem().getId()))
                .collect(Collectors.groupingBy(
                        submission -> new ScoreboardCellKey(
                                submission.getUser().getId(),
                                submission.getProblem().getId()
                        )
                ));

        Map<Long, ScoreboardCellKey> firstSolveByProblem = firstSolveByProblem(contest, submissionsByCell);
        List<ScoreboardRow> unranked = new ArrayList<>();

        for (User team : teams) {
            List<ScoreboardProblemCell> cells = new ArrayList<>();
            int solvedCount = 0;
            int totalPenalty = 0;

            for (int i = 0; i < problems.size(); i++) {
                Problem problem = problems.get(i);
                ScoreboardCellKey key = new ScoreboardCellKey(team.getId(), problem.getId());
                CellScore score = scoreCell(contest, submissionsByCell.getOrDefault(key, List.of()));
                if (score.solved()) {
                    solvedCount++;
                    totalPenalty += score.penalty();
                }

                cells.add(new ScoreboardProblemCell(
                        problem.getId(),
                        problemLabel(i),
                        score.solved(),
                        score.attempts(),
                        score.wrongAttempts(),
                        score.pendingCount(),
                        score.solvedTimeMinutes(),
                        score.solved() ? score.penalty() : null,
                        key.equals(firstSolveByProblem.get(problem.getId())),
                        hiddenCells.contains(key),
                        revealedCells.contains(key)
                ));
            }

            unranked.add(new ScoreboardRow(
                    0,
                    team.getId(),
                    team.getUsername(),
                    solvedCount,
                    totalPenalty,
                    List.copyOf(cells)
            ));
        }

        return rankingService.rankRows(unranked);
    }

    public Set<ScoreboardCellKey> hiddenCellsAfterFreeze(
            Instant freezeTime,
            List<Submission> allSubmissions
    ) {
        if (freezeTime == null) {
            return Set.of();
        }

        return allSubmissions.stream()
                .filter(this::isTeamSubmission)
                .filter(submission -> submission.getVerdict() != null)
                .filter(submission -> !isBefore(submission, freezeTime))
                .map(submission -> new ScoreboardCellKey(
                        submission.getUser().getId(),
                        submission.getProblem().getId()
                ))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean isTerminalForReveal(Submission submission) {
        return submission.getVerdict() != null && TERMINAL_VERDICTS.contains(submission.getVerdict());
    }

    private Map<Long, ScoreboardCellKey> firstSolveByProblem(
            Contest contest,
            Map<ScoreboardCellKey, List<Submission>> submissionsByCell
    ) {
        Map<Long, Submission> acceptedByProblem = new HashMap<>();

        for (Map.Entry<ScoreboardCellKey, List<Submission>> entry : submissionsByCell.entrySet()) {
            Optional<Submission> firstAccepted = firstAccepted(entry.getValue());
            if (firstAccepted.isEmpty()) {
                continue;
            }

            Submission candidate = firstAccepted.get();
            Long problemId = candidate.getProblem().getId();
            Submission current = acceptedByProblem.get(problemId);
            if (current == null || compareSubmissionTime(candidate, current) < 0) {
                acceptedByProblem.put(problemId, candidate);
            }
        }

        return acceptedByProblem.values().stream()
                .collect(Collectors.toMap(
                        submission -> submission.getProblem().getId(),
                        submission -> new ScoreboardCellKey(
                                submission.getUser().getId(),
                                submission.getProblem().getId()
                        )
                ));
    }

    private CellScore scoreCell(Contest contest, List<Submission> submissions) {
        List<Submission> ordered = submissions.stream()
                .filter(submission -> submission.getVerdict() != null)
                .sorted(this::compareSubmissionTime)
                .toList();

        Optional<Submission> firstAccepted = firstAccepted(ordered);
        int wrongAttempts;
        int attempts;
        int pendingCount = countPendingAttempts(ordered);

        if (firstAccepted.isPresent()) {
            Submission accepted = firstAccepted.get();
            List<Submission> beforeAccepted = ordered.stream()
                    .filter(submission -> compareSubmissionTime(submission, accepted) < 0)
                    .toList();
            wrongAttempts = countWrongPenaltyAttempts(beforeAccepted);
            attempts = wrongAttempts + 1;
            int solvedMinutes = solvedMinutes(contest, accepted);
            int penalty = solvedMinutes + wrongAttempts * penaltyMinutes(contest);
            return new CellScore(true, attempts, wrongAttempts, pendingCount, solvedMinutes, penalty);
        }

        wrongAttempts = countWrongPenaltyAttempts(ordered);
        return new CellScore(false, wrongAttempts, wrongAttempts, pendingCount, null, 0);
    }

    private Optional<Submission> firstAccepted(List<Submission> submissions) {
        return submissions.stream()
                .filter(submission -> submission.getVerdict() == Verdict.ACCEPTED)
                .min(this::compareSubmissionTime);
    }

    private int countWrongPenaltyAttempts(List<Submission> submissions) {
        return (int) submissions.stream()
                .filter(submission -> WRONG_PENALTY_VERDICTS.contains(submission.getVerdict()))
                .count();
    }

    private int countPendingAttempts(List<Submission> submissions) {
        return (int) submissions.stream()
                .filter(submission -> ACTIVE_VERDICTS.contains(submission.getVerdict()))
                .count();
    }

    private int solvedMinutes(Contest contest, Submission submission) {
        Instant actualStart = contest.getActualStartTime();
        if (actualStart == null || submission.getCreatedAt() == null) {
            return 0;
        }

        Instant submittedAt = submission.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .toInstant();
        return (int) Math.max(0, Duration.between(actualStart, submittedAt).toMinutes());
    }

    private int penaltyMinutes(Contest contest) {
        return contest.getPenaltyMinutes() == null ? 20 : contest.getPenaltyMinutes();
    }

    private boolean isTeamSubmission(Submission submission) {
        return submission.getUser() != null && submission.getUser().getRole() == Role.TEAM;
    }

    private boolean isBefore(Submission submission, Instant cutoff) {
        if (submission.getCreatedAt() == null) {
            return true;
        }

        return submission.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .isBefore(cutoff);
    }

    private int compareSubmissionTime(Submission left, Submission right) {
        Comparator<Submission> comparator = Comparator
                .comparing(Submission::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Submission::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        return comparator.compare(left, right);
    }

    private String problemLabel(int index) {
        if (index >= 0 && index < 26) {
            return String.valueOf((char) ('A' + index));
        }
        return String.valueOf(index + 1);
    }

    private record CellScore(
            boolean solved,
            int attempts,
            int wrongAttempts,
            int pendingCount,
            Integer solvedTimeMinutes,
            int penalty
    ) {
    }
}
