package com.server.contestControl.submissionServer.service.submission;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.exception.api.UserNotFoundException;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import com.server.contestControl.authServer.util.TokenExtractor;
import com.server.contestControl.contestServer.dto.UserResponse;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.service.ContestService;
import com.server.contestControl.contestServer.service.ProblemService;
import com.server.contestControl.submissionServer.dto.SubmissionRequest;
import com.server.contestControl.submissionServer.dto.SubmissionResponse;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.queue.submission.SubmissionProducer;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionProducer submissionProducer;
    private final JwtService jwtService;
    private final ContestService contestService;
    private final ProblemService problemService;
    private final UserRepository userRepository;

    @Transactional
    public SubmissionResponse submitCode(SubmissionRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UserNotFoundException(auth.getName()));

        Contest contest = contestService.getContestEntity();
        Problem problem = problemService.getProblemEntity(request.problemId());


        Submission submission = Submission.builder()
                .contest(contest)
                .problem(problem)
                .user(user)
                .code(request.code())
                .language(request.language())
                .verdict(Verdict.PENDING)
                .build();

        submissionRepository.save(submission);

        submissionProducer.sendSubmission(submission.getId());

        return SubmissionResponse.fromEntity(submission);
    }

    public SubmissionResponse getSubmissionById(Long id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
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

    public List<SubmissionResponse> getAllSubmission() {
        return submissionRepository.findAll()
                .stream()
                .map(SubmissionResponse::fromEntity)
                .toList();
    }

    public List<SubmissionResponse> getAllSubmissionsForUser(HttpServletRequest request) {
        String token = TokenExtractor.extractToken(request);
        Claims claims = jwtService.extractAllClaims(token, TokenType.ACCESS);

        String username = claims.getSubject();        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        return submissionRepository.getAllByUser_id(user.getId())
                .stream()
                .map(SubmissionResponse::fromEntity)
                .toList();
    }
}

