package com.server.contestControl.submissionServer.service.submission;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.exception.api.UserNotFoundException;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import com.server.contestControl.authServer.util.TokenExtractor;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.service.ContestService;
import com.server.contestControl.contestServer.service.ProblemService;
import com.server.contestControl.submissionServer.dto.SubmissionRequest;
import com.server.contestControl.submissionServer.dto.SubmissionResponse;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.exceptions.InvalidSubmissionRequestException;
import com.server.contestControl.submissionServer.queue.submission.SubmissionProducer;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.sse.SubmissionSsePublisher;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEvent;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEventType;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionProducer submissionProducer;
    private final JwtService jwtService;
    private final ContestService contestService;
    private final ProblemService problemService;
    private final UserRepository userRepository;
    private final SubmissionSsePublisher submissionSsePublisher;

    @Transactional
    public SubmissionResponse submitCode(SubmissionRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UserNotFoundException(auth.getName()));

        Contest contest = contestService.getContestEntity();
        Problem problem = problemService.getProblemEntity(request.problemId());

        // Validate that the request contestId (if provided) matches the active contest.
        if (request.contestId() != null && !request.contestId().equals(contest.getId())) {
            throw new InvalidSubmissionRequestException(
                    "Submission contestId does not match the active contest. " +
                    "Active contest id=" + contest.getId() + ", requested contestId=" + request.contestId()
            );
        }

        // Validate that the problem belongs to the active contest.
        if (!problem.getContest().getId().equals(contest.getId())) {
            throw new InvalidSubmissionRequestException(
                    "Problem does not belong to the active contest. " +
                    "Problem id=" + problem.getId() + " belongs to contest id=" + problem.getContest().getId() +
                    ", active contest id=" + contest.getId()
            );
        }

        Submission submission = Submission.builder()
                .contest(contest)
                .problem(problem)
                .user(user)
                .code(request.code())
                .language(request.language())
                .verdict(Verdict.PENDING)
                .build();

        Submission savedSubmission = submissionRepository.save(submission);

        // Capture event data while the entity is fully loaded inside this transaction,
        // then publish after commit so the frontend reads the committed state.
        SubmissionStreamEvent createdEvent =
                submissionSsePublisher.buildEvent(SubmissionStreamEventType.CREATED, savedSubmission);
        publishAfterCommit(() -> {
            publishSubmission(savedSubmission.getId());
            submissionSsePublisher.dispatch(createdEvent);
        });

        return SubmissionResponse.fromEntity(savedSubmission);
    }

    public SubmissionResponse getSubmissionById(Long id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        User currentUser = getCurrentUser();

        if (!isAdmin() && !submission.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Submission not found");
        }

        return SubmissionResponse.fromEntity(submission);
    }

    public List<SubmissionResponse> getAllSubmissionByProblem(
            Long problemId,
            HttpServletRequest request
    ) {
        String token = TokenExtractor.extractToken(request);
        Claims claims = jwtService.extractAllClaims(token, TokenType.ACCESS);

        String username = claims.getSubject();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        List<Submission> submissions =
                submissionRepository.getAllByUser_idAndProblemId(
                        user.getId(),
                        problemId
                );

        return submissions.stream()
                .map(SubmissionResponse::fromEntity)
                .toList();
    }

    public List<SubmissionResponse> getAllSubmission(Long contestId) {
        List<Submission> submissions = contestId == null
                ? submissionRepository.findAllByOrderByCreatedAtDescIdDesc()
                : submissionRepository.findAllByContest_IdOrderByCreatedAtDescIdDesc(contestId);

        return submissions
                .stream()
                .map(SubmissionResponse::fromEntity)
                .toList();
    }

    public List<SubmissionResponse> getAllSubmissionsForUser(HttpServletRequest request) {
        String token = TokenExtractor.extractToken(request);
        Claims claims = jwtService.extractAllClaims(token, TokenType.ACCESS);

        String username = claims.getSubject();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        return submissionRepository.getAllByUser_id(user.getId())
                .stream()
                .map(SubmissionResponse::fromEntity)
                .toList();
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("User not authenticated");
        }

        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UserNotFoundException(auth.getName()));
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private void publishSubmission(Long submissionId) {
        try {
            submissionProducer.sendSubmission(submissionId);
        } catch (RuntimeException ex) {
            // The submission is already committed at this point; make the failure visible
            // without rolling back the user's accepted submission.
            log.error("Failed to publish committed submission to RabbitMQ. submissionId={}", submissionId, ex);
        }
    }

    private void publishAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}

