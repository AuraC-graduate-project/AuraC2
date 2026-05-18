package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.contest.ContestRequest;
import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.dto.contest.ContestUpdateRequest;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.event.ContestUpdatedEvent;
import com.server.contestControl.contestServer.exception.ContestNotFoundException;
import com.server.contestControl.contestServer.exception.ContestValidationException;
import com.server.contestControl.contestServer.exception.InvalidContestStateException;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.sse.contest.ContestStreamSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestLifecycleService contestLifecycleService;
    private final ApplicationEventPublisher eventPublisher;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
    private static final Comparator<Contest> CONTEST_START_TIME_DESC =
            Comparator.comparing(Contest::getStartTime, Comparator.nullsLast(Comparator.reverseOrder()));

    //@Transactional means everything inside this method runs inside one database transaction.
    // or If an exception happens, rollback everything that was done in this method so the database is not left in an inconsistent state.
    @Transactional
    public ContestResponse createContest(ContestRequest request) {
        Instant now = Instant.now();

        // Validate no conflicting contest exists, to prevent creating another live contest.
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
                .status(ContestStatus.UPCOMING)// persistedStatus --> UPCOMING
                .build();

        contestRepository.save(contest);
        ContestResponse response = toResponse(contest);
        eventPublisher.publishEvent(new ContestUpdatedEvent(ContestUpdatedEvent.Reason.CREATED, response));


        return response;
    }

    @Transactional
    public ContestResponse updateContestDetails(Long id, ContestUpdateRequest request) {
        Instant now = Instant.now();
        Contest contest = contestRepository.findById(id)
                .orElseThrow(() -> new ContestNotFoundException(id));

        ContestStatus effectiveState = contestLifecycleService.resolveEffectiveState(contest, now);
        if (effectiveState == null) {
            effectiveState = contest.getStatus();
        }

        validateContestUpdateFields(request);
        applyContestUpdateByState(contest, request, effectiveState, now);

        contestRepository.save(contest);

        ContestResponse response = toResponse(contest);
        eventPublisher.publishEvent(new ContestUpdatedEvent(ContestUpdatedEvent.Reason.UPDATED, response));

        return response;
    }

    private void validateContestUpdateFields(ContestUpdateRequest request) {
        if (request.startTime() == null) {
            throw new ContestValidationException("Start time is required.");
        }

        if (request.durationMinutes() == null || request.durationMinutes() < 1) {
            throw new ContestValidationException("Duration must be at least 1 minute.");
        }

        if (request.scoreboardFreezeMinutes() != null && request.scoreboardFreezeMinutes() < 0) {
            throw new ContestValidationException("Scoreboard freeze time cannot be negative.");
        }

        if (request.penaltyMinutes() == null || request.penaltyMinutes() < 0) {
            throw new ContestValidationException("Penalty minutes cannot be negative.");
        }
    }

    private void applyContestUpdateByState(
            Contest contest,
            ContestUpdateRequest request,
            ContestStatus effectiveState,
            Instant now
    ) {
        boolean startChanged = !Objects.equals(request.startTime(), contest.getStartTime());
        boolean durationChanged = !Objects.equals(request.durationMinutes(), contest.getDurationMinutes());
        boolean freezeChanged = !Objects.equals(request.scoreboardFreezeMinutes(), contest.getScoreboardFreezeMinutes());
        boolean penaltyChanged = !Objects.equals(request.penaltyMinutes(), contest.getPenaltyMinutes());

        contest.setTitle(request.title());
        contest.setDescription(request.description());

        switch (effectiveState) {
            case UPCOMING -> {
                if (request.startTime().isBefore(now)) {
                    throw new ContestValidationException("Start time must be in the future.");
                }
                validateFreezeAgainstDuration(request.scoreboardFreezeMinutes(), request.durationMinutes());

                contest.setStartTime(request.startTime());
                contest.setDurationMinutes(request.durationMinutes());
                contest.setScoreboardFreezeMinutes(request.scoreboardFreezeMinutes());
                contest.setPenaltyMinutes(request.penaltyMinutes());
            }
            case RUNNING, PAUSED -> {
                if (startChanged) {
                    throw new InvalidContestStateException(
                            "Start time is locked once a contest is RUNNING or PAUSED.");
                }
                if (request.durationMinutes() < contest.getDurationMinutes()) {
                    throw new InvalidContestStateException(
                            "Duration can only be increased once a contest is RUNNING or PAUSED.");
                }
                validateFreezeAgainstDuration(request.scoreboardFreezeMinutes(), request.durationMinutes());

                contest.setDurationMinutes(request.durationMinutes());
                contest.setScoreboardFreezeMinutes(request.scoreboardFreezeMinutes());
                contest.setPenaltyMinutes(request.penaltyMinutes());
            }
            case ENDED -> {
                if (startChanged || durationChanged || freezeChanged || penaltyChanged) {
                    throw new InvalidContestStateException(
                            "Ended contests only allow title and description updates. Timing, freeze, and penalty settings are locked because they affect historical scoreboard results.");
                }
            }
        }
    }

    private void validateFreezeAgainstDuration(Integer freezeMinutes, Integer durationMinutes) {
        if (freezeMinutes != null && freezeMinutes >= durationMinutes) {
            throw new ContestValidationException(
                    "Scoreboard freeze time must be less than contest duration.");
        }
    }

    @Transactional
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
    @Transactional
    public ContestResponse updateStatus(Long id, ContestStatus newStatus, boolean juryOverride) {
        Contest contest = contestRepository.findById(id)
                .orElseThrow(() -> new ContestNotFoundException(id));

        ContestStatus current = contest.getStatus();

        if (!isValidTransition(current, newStatus)) {
            throw new InvalidContestStateException(
                    "Invalid contest status transition: " + current + " -> " + newStatus);
        }

        Instant now = Instant.now();

        // Time-based validations & lifecycle bookkeeping
        switch (newStatus) {
            // If target is RUNNING(manual start), handle start/resume logic.
            case RUNNING -> {
                // This checks if another contest is already RUNNING.
                if (contestRepository.existsByStatus(ContestStatus.RUNNING)
                        && current != ContestStatus.PAUSED) {
                    throw new InvalidContestStateException("Another contest is already running.");
                }

                // Manual start logic:
                if (current == ContestStatus.UPCOMING) {
                    //There are two start times:
                    //      startTime = scheduled/planned start time
                    //      actualStartTime = when the contest actually started

                    // Duration should count from actual start.
                    contest.setActualStartTime(now);
                    log.info(
                            "Manual start stamped actualStartTime | contestId={} | startTime={} | actualStartTime={}",
                            contest.getId(),
                            contest.getStartTime(),
                            contest.getActualStartTime()
                    );
                  // Resume logic:
                } else if (current == ContestStatus.PAUSED) {
                    // Resume: accumulate pause duration, clear pause marker.
                    Instant pausedAt = contest.getPausedAt();
                    if (pausedAt != null) {
                        long existing = contest.getTotalPauseMillis() != null
                                ? contest.getTotalPauseMillis() : 0L;
                        contest.setTotalPauseMillis(existing + (now.toEpochMilli() - pausedAt.toEpochMilli()));// Adds new pause duration to existing pause duration.
                    }
                    contest.setPausedAt(null);
                }
            }
            // Case PAUSED
            case PAUSED -> {
                contest.setPausedAt(now);// Store exact pause time.
            }
            // Case manual ENDING
            case ENDED -> {
                if (!juryOverride) {
                    // Pause-aware effective end — null when paused, so jury override is required to end paused contests.
                    Instant effectiveEnd = contestLifecycleService.resolveEffectiveEndTime(contest, now);
                    if (effectiveEnd == null || now.isBefore(effectiveEnd)) {
                        throw new InvalidContestStateException(
                                "Cannot end contest before its effective end time. Use jury override to force end.");
                    }
                }
            }
            default -> { }
        }

        contest.setStatus(newStatus);
        contestRepository.save(contest);

        ContestResponse response = toResponse(contest);

        // That means: when ContestService publishes ContestUpdatedEvent,
        // Spring will automatically call those listener methods.
        // The service itself does not directly call the broadcaster or scheduler.
        // It only publishes the event.
        // Spring searches the application for methods listening to ContestUpdatedEvent.
        eventPublisher.publishEvent(new ContestUpdatedEvent(
                manualReason(current, newStatus),
                response
        ));


        return response;
    }

    // Converts state transition into event reason.
    private ContestUpdatedEvent.Reason manualReason(ContestStatus from, ContestStatus to) {
        return switch (to) {
            case RUNNING -> from == ContestStatus.PAUSED
                    ? ContestUpdatedEvent.Reason.MANUAL_RESUME// active tab
                    : ContestUpdatedEvent.Reason.MANUAL_START;// active tab
            case PAUSED -> ContestUpdatedEvent.Reason.MANUAL_PAUSE; // paused tab
            case ENDED -> ContestUpdatedEvent.Reason.MANUAL_END;// ended tab
            default -> throw new IllegalStateException("Unexpected transition target: " + to);
        };
    }

    private boolean isValidTransition(ContestStatus from, ContestStatus to) {
        return switch (from) {
            case UPCOMING -> (to == ContestStatus.RUNNING || to == ContestStatus.ENDED);
            case RUNNING -> (to == ContestStatus.PAUSED || to == ContestStatus.ENDED);
            case PAUSED -> (to == ContestStatus.RUNNING || to == ContestStatus.ENDED);
            case ENDED -> false;
        };
    }

    // Get active contests by effective state
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

    // This builds the full snapshot sent to frontend when SSE connects.
    public ContestStreamSnapshot getStreamSnapshot() {
        Instant now = Instant.now();
        List<Contest> all = contestRepository.findAll();

        ContestResponse active = all.stream()
                .filter(c -> contestLifecycleService.resolveEffectiveState(c, now) == ContestStatus.RUNNING)
                .sorted(CONTEST_START_TIME_DESC)
                .findFirst()
                .map(this::toResponse)
                .orElse(null);

        ContestResponse upcoming = all.stream()
                .filter(c -> contestLifecycleService.resolveEffectiveState(c, now) == ContestStatus.UPCOMING)
                .sorted(CONTEST_START_TIME_DESC)
                .findFirst()
                .map(this::toResponse)
                .orElse(null);

        ContestResponse paused = all.stream()
                .filter(c -> contestLifecycleService.resolveEffectiveState(c, now) == ContestStatus.PAUSED)
                .sorted(CONTEST_START_TIME_DESC)
                .findFirst()
                .map(this::toResponse)
                .orElse(null);

        List<ContestResponse> ended = all.stream()
                .filter(c -> contestLifecycleService.resolveEffectiveState(c, now) == ContestStatus.ENDED)
                .sorted(CONTEST_START_TIME_DESC)
                .map(this::toResponse)
                .toList();

        return ContestStreamSnapshot.builder()
                .active(active)
                .upcoming(upcoming)
                .paused(paused)
                .ended(ended)
                .build();
    }

    /**
     * Opens a separate read-only transaction so the response is built from the
     * latest committed contest state, not from an older Hibernate L1 cache.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ContestResponse> buildResponseForId(Long contestId) {
        return contestRepository.findById(contestId).map(this::toResponse);
    }

    ContestResponse toResponse(Contest contest) {
        Instant now = Instant.now();
        Instant endTime = contest.getEndTime();
        Instant effectiveEndTime = contestLifecycleService.resolveEffectiveEndTime(contest, now);
        Instant freezeTime = contestLifecycleService.resolveEffectiveScoreboardFreezeTime(contest, now);
        ContestStatus effectiveState = contestLifecycleService.resolveEffectiveState(contest, now);
        boolean scoreboardFrozen = contestLifecycleService.isScoreboardFrozen(contest, now);
        long remainingMillis = contestLifecycleService.resolveRemainingMillis(contest, now);
        long totalPauseMillis = contest.getTotalPauseMillis() != null ? contest.getTotalPauseMillis() : 0L;

        log.debug(
                "toResponse | contestId={} | persistedStatus={} | effectiveState={} | startTime={} | actualStartTime={} | endTime={} | effectiveEndTime={}",
                contest.getId(),
                contest.getStatus(),
                effectiveState,
                contest.getStartTime(),
                contest.getActualStartTime(),
                endTime,
                effectiveEndTime
        );
        return ContestResponse.builder()
                .id(contest.getId())
                .title(contest.getTitle())
                .description(contest.getDescription())
                .durationMinutes(contest.getDurationMinutes())
                .status(contest.getStatus().name())
                .effectiveState(effectiveState.name())
                .statusLocked(Boolean.TRUE.equals(contest.getStatusLocked()))
                .startTime(formatInstant(contest.getStartTime()))
                .actualStartTime(formatInstant(contest.getActualStartTime()))
                .pausedAt(formatInstant(contest.getPausedAt()))
                .totalPauseMillis(totalPauseMillis)
                .remainingMillis(remainingMillis)
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

    // This checks if any contest currently has an effective state in the target list.
    // Example target list: List.of(UPCOMING, RUNNING, PAUSED)
    private boolean hasAnyContestInEffectiveStates(List<ContestStatus> targetStates, Instant now) {
        return contestRepository.findAll()
                .stream()
                .map(contest -> contestLifecycleService.resolveEffectiveState(contest, now))
                .anyMatch(targetStates::contains);
    }


    // Finds all contests whose calculated state equals the expected state.
    private List<Contest> findAllContestsByEffectiveState(ContestStatus expectedState) {
        Instant now = Instant.now();
        return contestRepository.findAll()
                .stream()
                .filter(contest -> contestLifecycleService.resolveEffectiveState(contest, now) == expectedState)
                .sorted(CONTEST_START_TIME_DESC)
                .toList();
    }

    // Finds one latest contest by effective state.
    private java.util.Optional<Contest> findLatestContestByEffectiveState(ContestStatus expectedState) {
        Instant now = Instant.now();
        return contestRepository.findAll()
                .stream()
                .sorted(CONTEST_START_TIME_DESC)
                .filter(contest -> contestLifecycleService.resolveEffectiveState(contest, now) == expectedState)
                .findFirst();
    }
}
