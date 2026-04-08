package com.server.contestControl.contestServer.service;

import com.server.contestControl.contestServer.dto.contest.ContestRequest;
import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.repository.ContestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContestService {

    private final ContestRepository contestRepository;

    public ContestResponse createContest(ContestRequest request) {

        boolean existsActive = contestRepository
                .existsByStatusIn(List.of(ContestStatus.UPCOMING, ContestStatus.RUNNING));

        if (existsActive) {
            throw new RuntimeException("A contest is already scheduled or running. " +
                    "Please end it before creating a new one.");
        }



        Contest contest = Contest.builder()
                .title(request.title())
                .description(request.description())
                .startTime(request.startTime())
                .durationMinutes(request.durationMinutes())
                .status(ContestStatus.UPCOMING)
                .build();

        contestRepository.save(contest);
        return ContestResponse.fromEntity(contest);
    }

    public ContestResponse updateStatus(Long id, ContestStatus newStatus) {
        Contest contest = contestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        ContestStatus current = contest.getStatus();

        if (!isValidTransition(current, newStatus)) {
            throw new RuntimeException("Invalid contest status transition: " + current + " -> " + newStatus);
        }

        if (newStatus == ContestStatus.RUNNING &&
                contestRepository.existsByStatus(ContestStatus.RUNNING) &&
                contest.getStatus() != ContestStatus.RUNNING) {
            throw new RuntimeException("Another contest is already running.");
        }

        contest.setStatus(newStatus);
        contestRepository.save(contest);

        return ContestResponse.fromEntity(contest);
    }

    private boolean isValidTransition(ContestStatus from, ContestStatus to) {
        return switch (from) {
            case UPCOMING -> (to == ContestStatus.RUNNING || to == ContestStatus.ENDED);
            case RUNNING  -> (to == ContestStatus.PAUSED || to == ContestStatus.ENDED);
            case PAUSED   -> (to == ContestStatus.RUNNING || to == ContestStatus.ENDED);
            case ENDED    -> false;
        };
    }
    public ContestResponse getActiveContest() {
        Contest contest = contestRepository.findByStatus(ContestStatus.RUNNING)
                .orElseThrow(() -> new RuntimeException("No active contest found"));

        return ContestResponse.fromEntity(contest);
    }


    public Contest getContestEntity() {
        Contest contest = contestRepository.findByStatus(ContestStatus.RUNNING)
                .orElseThrow(() -> new RuntimeException("No active contest found"));

        return contest;
    }
    public ContestResponse getUpcomingContest() {
        Contest contest = contestRepository.findByStatus(ContestStatus.UPCOMING)
                .orElseThrow(() -> new RuntimeException("No upcoming contest found"));

        return ContestResponse.fromEntity(contest);
    }

    public ContestResponse getPausedContest() {
        Contest contest = contestRepository.findByStatus(ContestStatus.PAUSED)
                .orElseThrow(() -> new RuntimeException("No paused contest found"));

        return ContestResponse.fromEntity(contest);
    }

    public ContestResponse getEndedContest() {
        Contest contest = contestRepository.findTopByStatusOrderByStartTimeDesc(ContestStatus.ENDED)
                .orElseThrow(() -> new RuntimeException("No ended contest found"));

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
                .orElseThrow(() -> new RuntimeException("No contest found with status " + status));

        return ContestResponse.fromEntity(contest);
    }

}
