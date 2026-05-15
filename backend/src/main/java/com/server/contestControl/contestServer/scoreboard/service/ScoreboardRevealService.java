package com.server.contestControl.contestServer.scoreboard.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.exception.ContestNotFoundException;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRevealResponse;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealCell;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealState;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
import com.server.contestControl.contestServer.scoreboard.exception.InvalidScoreboardRevealStateException;
import com.server.contestControl.contestServer.scoreboard.model.ScoreboardCellKey;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealCellRepository;
import com.server.contestControl.contestServer.scoreboard.repository.ScoreboardRevealStateRepository;
import com.server.contestControl.contestServer.service.ContestLifecycleService;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoreboardRevealService {

    private final ContestRepository contestRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final ScoreboardRevealStateRepository revealStateRepository;
    private final ScoreboardRevealCellRepository revealCellRepository;
    private final ContestLifecycleService contestLifecycleService;
    private final ScoreboardFreezePolicy freezePolicy;
    private final ScoreboardCalculator calculator;
    private final ScoreboardService scoreboardService;
    private final ScoreboardVersionService versionService;

    @Transactional(readOnly = true)
    public ScoreboardRevealResponse getState(Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ContestNotFoundException(contestId));
        ScoreboardRevealState state = revealStateRepository.findByContest_Id(contestId)
                .orElseGet(() -> ScoreboardRevealState.builder()
                        .contest(contest)
                        .status(RevealStatus.NOT_STARTED)
                        .build());
        return toResponse(state);
    }

    @Transactional
    public ScoreboardRevealResponse start(Long contestId) {
        Contest contest = endedContest(contestId);
        ScoreboardRevealState state = revealStateRepository.findByContest_Id(contestId)
                .orElseGet(() -> ScoreboardRevealState.builder()
                        .contest(contest)
                        .status(RevealStatus.NOT_STARTED)
                        .build());

        if (state.getId() != null) {
            revealCellRepository.deleteByRevealState_Id(state.getId());
        }

        Instant now = Instant.now();
        state.setStartedAt(now);
        state.setCompletedAt(null);
        state.setUpdatedAt(now);
        state.setStatus(RevealStatus.IN_PROGRESS);
        revealStateRepository.save(state);

        List<ScoreboardRevealCell> queue = buildRevealQueue(contest, state);
        revealCellRepository.saveAll(queue);
        if (queue.isEmpty()) {
            state.setStatus(RevealStatus.COMPLETED);
            state.setCompletedAt(now);
            revealStateRepository.save(state);
        }

        return toResponse(state);
    }

    @Transactional
    public ScoreboardRevealResponse revealNext(Long contestId) {
        ScoreboardRevealState state = stateOrThrow(contestId);
        ensureEnded(contestId);

        Optional<ScoreboardRevealCell> next = revealCellRepository
                .findFirstByRevealState_IdAndRevealedFalseOrderByRevealOrderAscIdAsc(state.getId());

        if (next.isEmpty()) {
            state.setStatus(RevealStatus.COMPLETED);
            if (state.getCompletedAt() == null) {
                state.setCompletedAt(Instant.now());
            }
            revealStateRepository.save(state);
            return toResponse(state);
        }

        ScoreboardRevealCell cell = next.get();
        cell.setRevealed(true);
        cell.setRevealedAt(Instant.now());
        revealCellRepository.save(cell);

        boolean hasMore = revealCellRepository
                .findFirstByRevealState_IdAndRevealedFalseOrderByRevealOrderAscIdAsc(state.getId())
                .isPresent();
        state.setStatus(hasMore ? RevealStatus.IN_PROGRESS : RevealStatus.COMPLETED);
        if (!hasMore) {
            state.setCompletedAt(Instant.now());
        }
        revealStateRepository.save(state);
        return toResponse(state);
    }

    @Transactional
    public ScoreboardRevealResponse revealAll(Long contestId) {
        ScoreboardRevealState state = stateOrThrow(contestId);
        ensureEnded(contestId);

        List<ScoreboardRevealCell> cells = revealCellRepository
                .findByRevealState_IdOrderByRevealOrderAscIdAsc(state.getId());
        Instant now = Instant.now();
        for (ScoreboardRevealCell cell : cells) {
            cell.setRevealed(true);
            if (cell.getRevealedAt() == null) {
                cell.setRevealedAt(now);
            }
        }
        revealCellRepository.saveAll(cells);
        state.setStatus(RevealStatus.COMPLETED);
        state.setCompletedAt(now);
        revealStateRepository.save(state);
        return toResponse(state);
    }

    @Transactional
    public ScoreboardRevealResponse reset(Long contestId) {
        ScoreboardRevealState state = stateOrThrow(contestId);
        ensureEnded(contestId);

        List<ScoreboardRevealCell> cells = revealCellRepository
                .findByRevealState_IdOrderByRevealOrderAscIdAsc(state.getId());
        for (ScoreboardRevealCell cell : cells) {
            cell.setRevealed(false);
            cell.setRevealedAt(null);
        }
        revealCellRepository.saveAll(cells);
        state.setStatus(RevealStatus.NOT_STARTED);
        state.setCompletedAt(null);
        revealStateRepository.save(state);
        return toResponse(state);
    }

    private List<ScoreboardRevealCell> buildRevealQueue(Contest contest, ScoreboardRevealState state) {
        Instant freezeTime = freezePolicy.freezeTime(contest, Instant.now());
        if (freezeTime == null) {
            return List.of();
        }

        Long contestId = contest.getId();
        List<Submission> submissions = submissionRepository.findAllByContestIdForScoreboard(contestId);
        Set<ScoreboardCellKey> hiddenCells = calculator.hiddenCellsAfterFreeze(freezeTime, submissions);
        if (hiddenCells.isEmpty()) {
            return List.of();
        }

        Map<Long, Problem> problemsById = problemRepository.findByContest_IdOrderByIdAsc(contestId).stream()
                .collect(Collectors.toMap(Problem::getId, problem -> problem));
        Map<Long, User> teamsById = userRepository.findAllByRoleOrderByUsernameAscIdAsc(Role.TEAM).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        ScoreboardSnapshot frozenSnapshot = scoreboardService.getSnapshot(
                contestId,
                ScoreboardAudience.PUBLIC,
                versionService.current(contestId, ScoreboardAudience.PUBLIC)
        );

        List<ScoreboardCellKey> orderedKeys = new ArrayList<>();
        List<ScoreboardRow> reverseRows = new ArrayList<>(frozenSnapshot.rows());
        Collections.reverse(reverseRows);
        for (ScoreboardRow row : reverseRows) {
            for (com.server.contestControl.contestServer.scoreboard.dto.ScoreboardProblemCell cell : row.problemCells()) {
                ScoreboardCellKey key = new ScoreboardCellKey(row.teamId(), cell.problemId());
                if (hiddenCells.contains(key)) {
                    orderedKeys.add(key);
                }
            }
        }

        int order = 1;
        List<ScoreboardRevealCell> queue = new ArrayList<>();
        for (ScoreboardCellKey key : orderedKeys) {
            User team = teamsById.get(key.teamId());
            Problem problem = problemsById.get(key.problemId());
            if (team == null || problem == null) {
                continue;
            }
            queue.add(ScoreboardRevealCell.builder()
                    .revealState(state)
                    .team(team)
                    .problem(problem)
                    .revealOrder(order++)
                    .revealed(false)
                    .build());
        }
        return queue;
    }

    private ScoreboardRevealState stateOrThrow(Long contestId) {
        return revealStateRepository.findByContest_Id(contestId)
                .orElseThrow(() -> new InvalidScoreboardRevealStateException(
                        "Reveal has not been started for contest " + contestId + "."
                ));
    }

    private Contest endedContest(Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ContestNotFoundException(contestId));
        ContestStatus effectiveState = contestLifecycleService.resolveEffectiveState(contest, Instant.now());
        if (effectiveState != ContestStatus.ENDED) {
            throw new InvalidScoreboardRevealStateException("Scoreboard reveal can only start after the contest ends.");
        }
        return contest;
    }

    private void ensureEnded(Long contestId) {
        endedContest(contestId);
    }

    private ScoreboardRevealResponse toResponse(ScoreboardRevealState state) {
        if (state.getId() == null) {
            return new ScoreboardRevealResponse(
                    state.getContest().getId(),
                    RevealStatus.NOT_STARTED,
                    0L,
                    0L,
                    null,
                    null,
                    state.getStartedAt(),
                    state.getUpdatedAt(),
                    state.getCompletedAt()
            );
        }

        long total = revealCellRepository.countByRevealState_Id(state.getId());
        long revealed = revealCellRepository.countByRevealState_IdAndRevealedTrue(state.getId());
        Optional<ScoreboardRevealCell> next = revealCellRepository
                .findFirstByRevealState_IdAndRevealedFalseOrderByRevealOrderAscIdAsc(state.getId());

        return new ScoreboardRevealResponse(
                state.getContest().getId(),
                state.getStatus(),
                total,
                revealed,
                next.map(cell -> cell.getTeam().getId()).orElse(null),
                next.map(cell -> cell.getProblem().getId()).orElse(null),
                state.getStartedAt(),
                state.getUpdatedAt(),
                state.getCompletedAt()
        );
    }
}
