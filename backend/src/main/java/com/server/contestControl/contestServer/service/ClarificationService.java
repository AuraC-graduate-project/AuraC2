package com.server.contestControl.contestServer.service;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.contestServer.dto.clarification.ClarificationRequest;
import com.server.contestControl.contestServer.dto.clarification.ClarificationResponse;
import com.server.contestControl.contestServer.dto.clarification.ReplyRequest;
import com.server.contestControl.contestServer.entity.Clarification;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ClarificationStatus;
import com.server.contestControl.contestServer.enums.ClarificationType;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.enums.StandardReply;
import com.server.contestControl.contestServer.exceptions.ClarificationAlreadyClosedException;
import com.server.contestControl.contestServer.exceptions.ClarificationNotFoundException;
import com.server.contestControl.contestServer.exceptions.ContestNotFoundException;
import com.server.contestControl.contestServer.exceptions.InvalidContestStateException;
import com.server.contestControl.contestServer.exceptions.InvalidReplyException;
import com.server.contestControl.contestServer.exceptions.ProblemDoesNotBelongToContestException;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.repository.ClarificationRepository;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClarificationService {

    private final ClarificationRepository clarificationRepository;
    private final ContestRepository contestRepository;
    private final ProblemRepository problemRepository;

    /**
     * Method 1: Team submits a clarification
     */
    @Transactional
    public ClarificationResponse submitClarification(ClarificationRequest request, User user) {
        // Validate contest exists and is running
        Contest contest = contestRepository.findById(request.contestId())
                .orElseThrow(() -> new ContestNotFoundException(request.contestId()));

        if (contest.getStatus() != ContestStatus.RUNNING) {
            throw new InvalidContestStateException("Contest is not running. Cannot submit clarification.");
        }

        // Validate problem if provided
        Problem problem = null;
        if (request.problemId() != null) {
            problem = problemRepository.findById(request.problemId())
                    .orElseThrow(() -> new ProblemNotFoundException(request.problemId()));

            // Null safety check + Ensure problem belongs to the contest
            if (problem.getContest() == null || !problem.getContest().getId().equals(contest.getId())) {
                throw new ProblemDoesNotBelongToContestException(request.problemId(), request.contestId());
            }
        }

        // Create clarification entity
        Clarification clarification = Clarification.builder()
                .contest(contest)
                .problem(problem)
                .user(user)
                .question(request.question())
                .build();

        // Save and return response
        Clarification saved = clarificationRepository.save(clarification);
        return ClarificationResponse.fromEntity(saved);
    }

    /**
     *  Admin replies to a clarification (or updates existing reply)
     */
    @Transactional
    public ClarificationResponse replyClarification(Long clarificationId, ReplyRequest request, User admin) {
        // Find clarification
        Clarification clarification = clarificationRepository.findById(clarificationId)
                .orElseThrow(() -> new ClarificationNotFoundException(clarificationId));

        //  Allow replying to PENDING or updating ANSWERED clarifications
        if (clarification.getStatus() == ClarificationStatus.CLOSED) {
            throw new ClarificationAlreadyClosedException(clarificationId);
        }

        // Set reply based on standardReply or custom text
        if (request.standardReply() != null && request.standardReply() != StandardReply.CUSTOM) {
            // Use predefined standard reply text
            clarification.setStandardReply(request.standardReply());
            clarification.setReply(null); // Clear custom reply
        } else if (request.standardReply() == StandardReply.CUSTOM) {
            // Use custom reply text
            if (request.reply() == null || request.reply().isBlank()) {
                throw new InvalidReplyException("Custom reply text is required when standardReply is CUSTOM");
            }
            clarification.setStandardReply(StandardReply.CUSTOM);
            clarification.setReply(request.reply());
        } else {
            // No standard reply provided, must have custom text
            if (request.reply() == null || request.reply().isBlank()) {
                throw new InvalidReplyException("Either standardReply or custom reply text is required");
            }
            clarification.setStandardReply(null);
            clarification.setReply(request.reply());
        }


        clarification.setReplyType(request.replyType());
        

        if (clarification.getRepliedAt() == null) {
            clarification.setRepliedAt(LocalDateTime.now());
        }
        
        clarification.setRepliedByAdmin(admin);
        clarification.setStatus(ClarificationStatus.ANSWERED);

        // Save and return response
        Clarification updated = clarificationRepository.save(clarification);
        return ClarificationResponse.fromEntity(updated);
    }

    /**
     *  Fetch clarifications for team
     * Returns: team's own clarifications + all PUBLIC ANSWERED clarifications in the contest
     * contest validation and performance optimization
     */
    @Transactional(readOnly = true)
    public List<ClarificationResponse> fetchClarificationsForTeam(Long contestId, User user) {
        // Validate contest exists
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ContestNotFoundException(contestId));

        if (contest.getStatus() != ContestStatus.RUNNING) {
            throw new InvalidContestStateException("Cannot view clarifications for non-running contest");
        }

        // Get team's own clarifications in this contest
        List<Clarification> myClarifications = clarificationRepository
                .findByUserIdAndContestIdOrderByCreatedAtDesc(user.getId(), contestId);

        //  Get ONLY ANSWERED public clarifications (prevents pending leaks)
        List<Clarification> publicClarifications = clarificationRepository
                .findByContestIdAndReplyTypeAndStatusOrderByCreatedAtDesc(
                        contestId, 
                        ClarificationType.PUBLIC, 
                        ClarificationStatus.ANSWERED
                );


        Set<Long> seenIds = myClarifications.stream()
                .map(Clarification::getId)
                .collect(Collectors.toSet());

        List<Clarification> combined = new ArrayList<>(myClarifications);
        for (Clarification publicClarification : publicClarifications) {
            if (!seenIds.contains(publicClarification.getId())) {
                combined.add(publicClarification);
            }
        }

        // Sort by created date descending
        combined.sort((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt()));

        return combined.stream()
                .map(ClarificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Fetch all clarifications for admin (by contest)
     */
    @Transactional(readOnly = true)
    public List<ClarificationResponse> fetchClarificationsForAdmin(Long contestId) {
        List<Clarification> clarifications = clarificationRepository
                .findByContestIdOrderByCreatedAtDesc(contestId);

        return clarifications.stream()
                .map(ClarificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Helper: Fetch all clarifications across all contests (admin)
     */
    @Transactional(readOnly = true)
    public List<ClarificationResponse> fetchAllClarificationsForAdmin() {
        List<Clarification> clarifications = clarificationRepository.findAllByOrderByCreatedAtDesc();

        return clarifications.stream()
                .map(ClarificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
      Fetch public ANSWERED clarifications for a contest (no authentication required)

     */
    @Transactional(readOnly = true)
    public List<ClarificationResponse> fetchPublicClarifications(Long contestId) {
        // Get only PUBLIC and ANSWERED clarifications
        List<Clarification> publicClarifications = clarificationRepository
                .findByContestIdAndReplyTypeAndStatusOrderByCreatedAtDesc(
                        contestId,
                        ClarificationType.PUBLIC,
                        ClarificationStatus.ANSWERED
                );

        return publicClarifications.stream()
                .map(ClarificationResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
