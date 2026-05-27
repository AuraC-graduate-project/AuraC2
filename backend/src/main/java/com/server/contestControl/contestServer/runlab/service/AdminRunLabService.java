package com.server.contestControl.contestServer.runlab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.exception.api.UserNotFoundException;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.exception.ContestNotFoundException;
import com.server.contestControl.contestServer.exceptions.ProblemDoesNotBelongToContestException;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.moderation.entity.ContestModerationAuditLog;
import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;
import com.server.contestControl.contestServer.moderation.exception.ContestTeamModerationException;
import com.server.contestControl.contestServer.moderation.repository.ContestModerationAuditLogRepository;
import com.server.contestControl.contestServer.oracle.service.OracleJudge0ExecutionService;
import com.server.contestControl.contestServer.runlab.dto.AdminRunLabRequest;
import com.server.contestControl.contestServer.runlab.dto.AdminRunLabResponse;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.language.SupportedLanguageService;
import com.server.contestControl.submissionServer.run.exception.RunRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminRunLabService {

    private static final int MAX_SOURCE_CHARS = 200_000;
    private static final int MAX_INPUT_CHARS = 50_000;

    private final ContestRepository contestRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final SupportedLanguageService supportedLanguageService;
    private final OracleJudge0ExecutionService judge0ExecutionService;
    private final ContestModerationAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminRunLabResponse run(AdminRunLabRequest request, String adminUsername) {
        if (request == null) {
            throw new RunRequestException("Run Lab request is required.");
        }
        String sourceCode = sourceCode(request.sourceCode());
        String customInput = normalizeInput(request.customInput());
        int languageId = languageId(request.languageId());
        Contest contest = contest(request.contestId());
        Problem problem = problem(request.problemId());
        if (problem.getContest() == null || !contest.getId().equals(problem.getContest().getId())) {
            throw new ProblemDoesNotBelongToContestException(problem.getId(), contest.getId());
        }
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException(adminUsername));

        OracleJudge0ExecutionService.SandboxExecutionResult result = judge0ExecutionService.run(
                sourceCode,
                languageId,
                customInput,
                toJudge0CpuTimeLimitSeconds(problem.getTimeLimit()),
                toJudge0MemoryLimitKilobytes(problem.getMemoryLimit())
        );

        String sourceHash = sourceHash(sourceCode);
        auditLogRepository.save(ContestModerationAuditLog.builder()
                .contest(contest)
                .team(null)
                .admin(admin)
                .problem(problem)
                .actionType(ModerationActionType.ADMIN_RUN_LAB_EXECUTION)
                .reason("Admin Run Lab custom input execution")
                .oldValueJson("{}")
                .newValueJson(auditMetadata(result, customInput))
                .languageId(languageId)
                .sourceHash(sourceHash)
                .executionMode("CUSTOM_INPUT")
                .createdAt(LocalDateTime.now())
                .build());

        return new AdminRunLabResponse(
                false,
                result.verdict(),
                result.statusId(),
                result.statusDescription(),
                result.stdout(),
                result.stderr(),
                result.verdict() == Verdict.COMPILATION_ERROR ? result.diagnostic() : null,
                result.executionTime(),
                result.memoryUsage()
        );
    }

    private Contest contest(Long contestId) {
        if (contestId == null) {
            throw new RunRequestException("contestId is required.");
        }
        return contestRepository.findById(contestId)
                .orElseThrow(() -> new ContestNotFoundException(contestId));
    }

    private Problem problem(Long problemId) {
        if (problemId == null) {
            throw new RunRequestException("problemId is required.");
        }
        return problemRepository.findByIdWithContest(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));
    }

    private int languageId(Integer languageId) {
        if (languageId == null || languageId <= 0) {
            throw new RunRequestException("languageId must be positive.");
        }
        supportedLanguageService.requireByJudge0LanguageId(languageId);
        return languageId;
    }

    private String sourceCode(String value) {
        if (value == null || value.isBlank()) {
            throw new RunRequestException("Source code cannot be empty.");
        }
        String sourceCode = value.replace("\r\n", "\n").replace('\r', '\n');
        if (sourceCode.length() > MAX_SOURCE_CHARS) {
            throw new RunRequestException("Source code is too large to run.");
        }
        return sourceCode;
    }

    private String normalizeInput(String value) {
        String input = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        if (input.length() > MAX_INPUT_CHARS) {
            throw new RunRequestException("Custom input cannot exceed " + MAX_INPUT_CHARS + " characters.");
        }
        return input;
    }

    private String auditMetadata(
            OracleJudge0ExecutionService.SandboxExecutionResult result,
            String customInput
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("executionMode", "CUSTOM_INPUT");
        metadata.put("customInputChars", customInput.length());
        metadata.put("verdict", result.verdict() == null ? null : result.verdict().name());
        metadata.put("statusId", result.statusId());
        metadata.put("runtimeMillis", result.executionTime());
        metadata.put("memoryKb", result.memoryUsage());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new ContestTeamModerationException("Could not record Run Lab audit metadata.");
        }
    }

    private String sourceHash(String sourceCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sourceCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new ContestTeamModerationException("Could not hash Run Lab source.");
        }
    }

    private Double toJudge0CpuTimeLimitSeconds(Integer timeLimitMillis) {
        if (timeLimitMillis == null || timeLimitMillis <= 0) {
            return null;
        }
        return BigDecimal.valueOf(timeLimitMillis)
                .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .doubleValue();
    }

    private Integer toJudge0MemoryLimitKilobytes(Integer memoryLimitMegabytes) {
        if (memoryLimitMegabytes == null || memoryLimitMegabytes <= 0) {
            return null;
        }
        return Math.multiplyExact(memoryLimitMegabytes, 1024);
    }
}
