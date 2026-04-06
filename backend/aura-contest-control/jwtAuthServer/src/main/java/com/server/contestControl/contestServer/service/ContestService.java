package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.contest.ContestRequest;
import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.exception.ContestNotFoundException;
import com.server.contestControl.contestServer.exception.ContestValidationException;
import com.server.contestControl.contestServer.exception.InvalidContestStateException;
import com.server.contestControl.contestServer.repository.ContestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestLifecycleService contestLifecycleService;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
    private static final Comparator<Contest> CONTEST_START_TIME_DESC =
            Comparator.comparing(Contest::getStartTime, Comparator.nullsLast(Comparator.reverseOrder()));

    public ContestResponse createContest(ContestRequest request) {
        Instant now = Instant.now();

        // Validate no conflicting contest exists
        boolean existsActive = hasAnyContestInEffectiveStates(
                List.of(ContestStatus.UPCOMING, ContestStatus.RUNNING, ContestStatus.PAUSED),
                now
        );

        if (existsActive) {
            throw new InvalidContestStateException(
                    "A contest is already scheduled, running, or paused. End it before creating a new one.");
        }

        // Validate startTime is in the future
        if (request.startTime().isBefore(now)) {
            throw new ContestValidationException("Start time must be in the future.");
        }

        // Validate scoreboardFreezeMinutes if provided
        if (request.scoreboardFreezeMinutes() != null) {
            if (request.scoreboardFreezeMinutes() >= request.durationMinutes()) {
                throw new ContestValidationException(
                        "Scoreboard freeze time must be less than contest duration.");
            }
        }

        Contest contest = Contest.builder()
                .title(request.title())
                .description(request.description())
                .startTime(request.startTime())
                .durationMinutes(request.durationMinutes())
                .scoreboardFreezeMinutes(request.scoreboardFreezeMinutes())
                .penaltyMinutes(request.penaltyMinutes())
                .status(ContestStatus.UPCOMING)
                .build();

        contestRepository.save(contest);
        return toResponse(contest);
    }

    //
    public ContestResponse updateStatus(Long id, ContestStatus newStatus) {
        return updateStatus(id, newStatus, false);
    }

    /**
     * Updates contest status with time-based validation.
     *
     * @param id              Contest ID
     * @param newStatus       Target status
     * @param juryOverride    If true, allows ending contest before scheduled time (jury decision)
     */
    public ContestResponse updateStatus(Long id, ContestStatus newStatus, boolean juryOverride) {
        Contest contest = contestRepository.findById(id)
                .orElseThrow(() -> new ContestNotFoundException(id));

        ContestStatus current = contest.getStatus();

        if (!isValidTransition(current, newStatus)) {
            throw new InvalidContestStateException(
                    "Invalid contest status transition: " + current + " -> " + newStatus);
        }

        Instant now = Instant.now();

        // Time-based validations
        switch (newStatus) {
            case RUNNING -> {
                // Can only start if startTime has passed (or is within 1 minute grace period)
                Instant startTime = contest.getStartTime();
                Instant gracePeriodStart = startTime.minusSeconds(60);
                if (now.isBefore(gracePeriodStart)) {
                    throw new InvalidContestStateException(
                            "Cannot start contest before scheduled start time: " + startTime);
                }

                // Check no other contest is running
                if (contestRepository.existsByStatus(ContestStatus.RUNNING)
                        && current != ContestStatus.PAUSED) {
                    throw new InvalidContestStateException("Another contest is already running.");
                }
            }
            case ENDED -> {
                if (!juryOverride) {
                    // Normally, can only end if endTime has passed
                    Instant endTime = contest.getEndTime();
                    if (endTime != null && now.isBefore(endTime)) {
                        throw new InvalidContestStateException(
                                "Cannot end contest before scheduled end time: " + endTime +
                                        ". Use jury override to force end.");
                    }
                }
            }
            default -> { /* No additional validation for PAUSED */ }
        }

        contest.setStatus(newStatus);
        contestRepository.save(contest);

        return toResponse(contest);
    }

    private boolean isValidTransition(ContestStatus from, ContestStatus to) {
        return switch (from) {
            case UPCOMING -> (to == ContestStatus.RUNNING || to == ContestStatus.ENDED);
            case RUNNING -> (to == ContestStatus.PAUSED || to == ContestStatus.ENDED);
            case PAUSED -> (to == ContestStatus.RUNNING || to == ContestStatus.ENDED);
            case ENDED -> false;
        };
    }

    public ContestResponse getActiveContest() {
        Contest contest = findLatestContestByEffectiveState(ContestStatus.RUNNING)
                .orElseThrow(() -> new ContestNotFoundException("No active contest found"));

        return toResponse(contest);
    }

    public Contest getContestEntity() {
        return findLatestContestByEffectiveState(ContestStatus.RUNNING)
                .orElseThrow(() -> new ContestNotFoundException("No active contest found"));
    }

    public ContestResponse getUpcomingContest() {
        Contest contest = findLatestContestByEffectiveState(ContestStatus.UPCOMING)
                .orElseThrow(() -> new ContestNotFoundException("No upcoming contest found"));

        return toResponse(contest);
    }

    public ContestResponse getPausedContest() {
        Contest contest = findLatestContestByEffectiveState(ContestStatus.PAUSED)
                .orElseThrow(() -> new ContestNotFoundException("No paused contest found"));

        return toResponse(contest);
    }

    public ContestResponse getEndedContest() {
        Contest contest = findLatestContestByEffectiveState(ContestStatus.ENDED)
                .orElseThrow(() -> new ContestNotFoundException("No ended contest found"));

        return toResponse(contest);
    }

    public List<ContestResponse> getEndedContests() {
        return findAllContestsByEffectiveState(ContestStatus.ENDED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ContestResponse getContestByStatus(ContestStatus status) {
        Contest contest = contestRepository.findByStatus(status)
                .orElseThrow(() -> new ContestNotFoundException("No contest found with status " + status));

        return toResponse(contest);
    }

    private ContestResponse toResponse(Contest contest) {
        Instant now = Instant.now();
        Instant endTime = contest.getEndTime();
        Instant effectiveEndTime = contestLifecycleService.resolveEffectiveEndTime(contest, now);
        Instant freezeTime = contestLifecycleService.resolveEffectiveScoreboardFreezeTime(contest, now);
        ContestStatus effectiveState = contestLifecycleService.resolveEffectiveState(contest, now);
        boolean scoreboardFrozen = contestLifecycleService.isScoreboardFrozen(contest, now);


        return ContestResponse.builder()
                .id(contest.getId())
                .title(contest.getTitle())
                .description(contest.getDescription())
                .durationMinutes(contest.getDurationMinutes())
                .status(contest.getStatus().name())
                .effectiveState(effectiveState.name())
                .startTime(formatInstant(contest.getStartTime()))
                .endTime(formatInstant(endTime))
                .effectiveEndTime(formatInstant(effectiveEndTime))
                .scoreboardFreezeMinutes(contest.getScoreboardFreezeMinutes())
                .scoreboardFreezeTime(formatInstant(freezeTime))
                .penaltyMinutes(contest.getPenaltyMinutes())
                .scoreboardFrozen(scoreboardFrozen)
                .build();
    }

    private String formatInstant(Instant instant) {
        return instant != null ? ISO_FORMATTER.format(instant) : null;
    }

    private boolean hasAnyContestInEffectiveStates(List<ContestStatus> targetStates, Instant now) {
        return contestRepository.findAll()
                .stream()
                .map(contest -> contestLifecycleService.resolveEffectiveState(contest, now))
                .anyMatch(targetStates::contains);
    }

    private boolean hasAnotherEffectiveRunningContest(Long currentContestId, Instant now) {
        return contestRepository.findAll()
                .stream()
                .filter(contest -> !contest.getId().equals(currentContestId))
                .anyMatch(contest -> contestLifecycleService.resolveEffectiveState(contest, now) == ContestStatus.RUNNING);
    }

    private List<Contest> findAllContestsByEffectiveState(ContestStatus expectedState) {
        Instant now = Instant.now();
        return contestRepository.findAll()
                .stream()
                .filter(contest -> contestLifecycleService.resolveEffectiveState(contest, now) == expectedState)
                .sorted(CONTEST_START_TIME_DESC)
                .toList();
    }

    private java.util.Optional<Contest> findLatestContestByEffectiveState(ContestStatus expectedState) {
        Instant now = Instant.now();
        return contestRepository.findAll()
                .stream()
                .sorted(CONTEST_START_TIME_DESC)
                .filter(contest -> contestLifecycleService.resolveEffectiveState(contest, now) == expectedState)
                .findFirst();
    }
}
