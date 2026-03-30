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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContestService {

    private final ContestRepository contestRepository;

    public ContestResponse createContest(ContestRequest request) {
        // Validate no conflicting contest exists
        boolean existsActive = contestRepository
                .existsByStatusIn(List.of(ContestStatus.UPCOMING, ContestStatus.RUNNING, ContestStatus.PAUSED));

        if (existsActive) {
            throw new InvalidContestStateException(
                    "A contest is already scheduled, running, or paused. End it before creating a new one.");
        }

        // Validate startTime is in the future
        if (request.startTime().isBefore(Instant.now())) {
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
        return ContestResponse.fromEntity(contest);
    }

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

        return ContestResponse.fromEntity(contest);
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
        Contest contest = contestRepository.findByStatus(ContestStatus.RUNNING)
                .orElseThrow(() -> new ContestNotFoundException("No active contest found"));

        return ContestResponse.fromEntity(contest);
    }

    public Contest getContestEntity() {
        return contestRepository.findByStatus(ContestStatus.RUNNING)
                .orElseThrow(() -> new ContestNotFoundException("No active contest found"));
    }

    public ContestResponse getUpcomingContest() {
        Contest contest = contestRepository.findByStatus(ContestStatus.UPCOMING)
                .orElseThrow(() -> new ContestNotFoundException("No upcoming contest found"));

        return ContestResponse.fromEntity(contest);
    }

    public ContestResponse getPausedContest() {
        Contest contest = contestRepository.findByStatus(ContestStatus.PAUSED)
                .orElseThrow(() -> new ContestNotFoundException("No paused contest found"));

        return ContestResponse.fromEntity(contest);
    }

    public ContestResponse getEndedContest() {
        Contest contest = contestRepository.findTopByStatusOrderByStartTimeDesc(ContestStatus.ENDED)
                .orElseThrow(() -> new ContestNotFoundException("No ended contest found"));

        return ContestResponse.fromEntity(contest);
    }

    public List<ContestResponse> getEndedContests() {
        return contestRepository.findAllByStatus(ContestStatus.ENDED)
                .stream()
                .map(ContestResponse::fromEntity)
                .toList();
    }

    public ContestResponse getContestByStatus(ContestStatus status) {
        Contest contest = contestRepository.findByStatus(status)
                .orElseThrow(() -> new ContestNotFoundException("No contest found with status " + status));

        return ContestResponse.fromEntity(contest);
    }
}
