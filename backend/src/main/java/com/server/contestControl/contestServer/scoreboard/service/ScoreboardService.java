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
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardMetadata;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardSnapshot;
import com.server.contestControl.contestServer.scoreboard.dto.ScoreboardUpdatePayload;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealCell;
import com.server.contestControl.contestServer.scoreboard.entity.ScoreboardRevealState;
import com.server.contestControl.contestServer.scoreboard.enums.RevealStatus;
import com.server.contestControl.contestServer.scoreboard.enums.ScoreboardAudience;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScoreboardService {

    private final ContestRepository contestRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final ScoreboardRevealStateRepository revealStateRepository;
    private final ScoreboardRevealCellRepository revealCellRepository;
    private final ContestLifecycleService contestLifecycleService;
    private final ScoreboardFreezePolicy freezePolicy;
    private final ScoreboardCalculator calculator;
    private final ScoreboardVersionService versionService;

    public ScoreboardSnapshot getPublicSnapshot(Long contestId) {
        return getSnapshot(contestId, ScoreboardAudience.PUBLIC, versionService.current(contestId, ScoreboardAudience.PUBLIC));
    }

    public ScoreboardSnapshot getAdminSnapshot(Long contestId) {
        return getSnapshot(contestId, ScoreboardAudience.ADMIN, versionService.current(contestId, ScoreboardAudience.ADMIN));
    }

    public ScoreboardSnapshot getSnapshot(Long contestId, ScoreboardAudience audience, long version) {
        Instant now = Instant.now();
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ContestNotFoundException(contestId));
        List<Problem> problems = problemRepository.findByContest_IdOrderByIdAsc(contestId);
        List<User> teams = userRepository.findAllByRoleOrderByUsernameAscIdAsc(Role.TEAM);
        List<Submission> allSubmissions = submissionRepository.findAllByContestIdForScoreboard(contestId);
        RevealContext revealContext = revealContext(contestId);

        Instant freezeTime = freezePolicy.freezeTime(contest, now);
        boolean publicFrozen = freezePolicy.isFrozenForAudience(
                contest,
                ScoreboardAudience.PUBLIC,
                revealContext.status(),
                now
        );
        ScoreboardViewMode viewMode = viewModeFor(audience);
        Set<ScoreboardCellKey> publicHiddenCells = !publicFrozen
                ? Set.of()
                : calculator.hiddenCellsAfterFreeze(freezeTime, allSubmissions);
        Set<ScoreboardCellKey> revealedCells = revealContext.revealedCells();
        Set<ScoreboardCellKey> rowHiddenCells = viewMode == ScoreboardViewMode.PUBLIC_OFFICIAL && publicFrozen
                ? publicHiddenCells
                : Set.of();
        Set<ScoreboardCellKey> rowRevealedCells = viewMode == ScoreboardViewMode.PUBLIC_OFFICIAL && publicFrozen
                ? revealedCells
                : Set.of();
        List<Submission> visibleSubmissions = buildVisibleSubmissions(
                viewMode,
                publicFrozen,
                freezeTime,
                revealedCells,
                allSubmissions
        );

        List<ScoreboardMetadata.ProblemColumn> columns = new ArrayList<>();
        for (int i = 0; i < problems.size(); i++) {
            Problem problem = problems.get(i);
            columns.add(new ScoreboardMetadata.ProblemColumn(
                    problem.getId(),
                    problemLabel(i),
                    problem.getTitle()
            ));
        }

        ContestStatus effectiveState = contestLifecycleService.resolveEffectiveState(contest, now);
        ScoreboardMetadata metadata = new ScoreboardMetadata(
                contest.getId(),
                contest.getTitle(),
                audience,
                version,
                now,
                contest.getStatus() == null ? null : contest.getStatus().name(),
                effectiveState.name(),
                audience == ScoreboardAudience.ADMIN,
                publicFrozen,
                freezeTime,
                contest.getScoreboardFreezeMinutes(),
                contest.getPenaltyMinutes(),
                revealContext.status(),
                revealContext.revealedCount(),
                publicHiddenCells.size(),
                List.copyOf(columns)
        );

        return new ScoreboardSnapshot(
                metadata,
                calculator.calculateRows(contest, problems, teams, visibleSubmissions, rowHiddenCells, rowRevealedCells)
        );
    }

    private ScoreboardViewMode viewModeFor(ScoreboardAudience audience) {
        return audience == ScoreboardAudience.ADMIN
                ? ScoreboardViewMode.ADMIN_LIVE
                : ScoreboardViewMode.PUBLIC_OFFICIAL;
    }

    private List<Submission> buildVisibleSubmissions(
            ScoreboardViewMode viewMode,
            boolean publicFrozen,
            Instant freezeTime,
            Set<ScoreboardCellKey> revealedCells,
            List<Submission> allSubmissions
    ) {
        if (viewMode == ScoreboardViewMode.ADMIN_LIVE || !publicFrozen) {
            return allSubmissions;
        }

        return allSubmissions.stream()
                .filter(submission -> isSubmissionVisibleForPublic(submission, freezeTime, revealedCells))
                .toList();
    }

    private boolean isSubmissionVisibleForPublic(
            Submission submission,
            Instant freezeTime,
            Set<ScoreboardCellKey> revealedCells
    ) {
        return !isFrozenSubmission(submission, freezeTime) || isCellRevealed(submission, revealedCells);
    }

    private boolean isFrozenSubmission(Submission submission, Instant freezeTime) {
        return !freezePolicy.isBeforeFreeze(submission, freezeTime);
    }

    private boolean isCellRevealed(Submission submission, Set<ScoreboardCellKey> revealedCells) {
        if (submission.getUser() == null || submission.getProblem() == null) {
            return false;
        }

        return revealedCells.contains(new ScoreboardCellKey(
                submission.getUser().getId(),
                submission.getProblem().getId()
        ));
    }

    public ScoreboardUpdatePayload buildUpdatePayload(
            String eventType,
            String reason,
            ScoreboardSnapshot previous,
            ScoreboardSnapshot current
    ) {
        Map<Long, com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow> previousRows =
                previous == null
                        ? Map.of()
                        : previous.rows().stream().collect(Collectors.toMap(
                                com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow::teamId,
                                Function.identity()
                        ));

        List<com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow> changedRows = current.rows().stream()
                .filter(row -> !row.equals(previousRows.get(row.teamId())))
                .toList();
        List<Long> changedTeamIds = changedRows.stream()
                .map(com.server.contestControl.contestServer.scoreboard.dto.ScoreboardRow::teamId)
                .toList();

        boolean fullSnapshot = previous == null;
        long previousVersion = previous == null ? 0L : previous.metadata().version();

        return new ScoreboardUpdatePayload(
                eventType,
                reason,
                current.metadata().contestId(),
                current.metadata().version(),
                previousVersion,
                fullSnapshot,
                changedTeamIds,
                changedRows,
                current.metadata(),
                fullSnapshot ? current : null
        );
    }

    private RevealContext revealContext(Long contestId) {
        Optional<ScoreboardRevealState> state = revealStateRepository.findByContest_Id(contestId);
        if (state.isEmpty()) {
            return new RevealContext(RevealStatus.NOT_STARTED, Set.of(), 0L);
        }

        List<ScoreboardRevealCell> cells = revealCellRepository
                .findByRevealState_IdOrderByRevealOrderAscIdAsc(state.get().getId());
        Set<ScoreboardCellKey> revealedCells = cells.stream()
                .filter(cell -> Boolean.TRUE.equals(cell.getRevealed()))
                .map(cell -> new ScoreboardCellKey(cell.getTeam().getId(), cell.getProblem().getId()))
                .collect(Collectors.toUnmodifiableSet());

        return new RevealContext(
                state.get().getStatus(),
                revealedCells,
                cells.stream().filter(cell -> Boolean.TRUE.equals(cell.getRevealed())).count()
        );
    }

    private String problemLabel(int index) {
        if (index >= 0 && index < 26) {
            return String.valueOf((char) ('A' + index));
        }
        return String.valueOf(index + 1);
    }

    private record RevealContext(
            RevealStatus status,
            Set<ScoreboardCellKey> revealedCells,
            long revealedCount
    ) {
    }

    private enum ScoreboardViewMode {
        ADMIN_LIVE,
        PUBLIC_OFFICIAL
    }
}
