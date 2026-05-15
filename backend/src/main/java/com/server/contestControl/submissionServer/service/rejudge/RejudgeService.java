package com.server.contestControl.submissionServer.service.rejudge;

import com.server.contestControl.contestServer.exceptions.ContestNotFoundException;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.submissionServer.dto.RejudgeResponse;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.event.SubmissionRejudgeQueuedEvent;
import com.server.contestControl.submissionServer.exceptions.InvalidRejudgeRequestException;
import com.server.contestControl.submissionServer.queue.submission.SubmissionProducer;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.sse.SubmissionSsePublisher;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEvent;
import com.server.contestControl.submissionServer.sse.SubmissionStreamEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RejudgeService {

    private static final Set<Verdict> ACTIVE_VERDICTS = EnumSet.of(
            Verdict.PENDING,
            Verdict.PENDING_REJUDGE,
            Verdict.RUNNING
    );

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final ContestRepository contestRepository;
    private final SubmissionProducer submissionProducer;
    private final SubmissionSsePublisher submissionSsePublisher;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RejudgeResponse rejudgeSelectedSubmissions(List<Long> submissionIds) {
        List<Long> requestedIds = normalizeSubmissionIds(submissionIds);
        log.info("Starting selected submission rejudge. requestedCount={}", requestedIds.size());

        List<Submission> submissions = submissionRepository.findAllById(requestedIds);
        RejudgeResponse response = rejudgeSubmissions("SUBMISSIONS", null, requestedIds, submissions);

        log.info(
                "Selected submission rejudge prepared. requestedCount={} foundCount={} queuedCount={} skippedCount={} missingCount={}",
                response.requestedCount(),
                response.foundCount(),
                response.queuedCount(),
                response.skippedCount(),
                response.missingSubmissionIds().size()
        );
        return response;
    }

    @Transactional
    public RejudgeResponse rejudgeProblem(Long problemId) {
        if (!problemRepository.existsById(problemId)) {
            throw new ProblemNotFoundException(problemId);
        }

        log.info("Starting problem rejudge. problemId={}", problemId);
        List<Submission> submissions = submissionRepository.findAllByProblem_Id(problemId);
        List<Long> requestedIds = submissions.stream()
                .map(Submission::getId)
                .toList();

        RejudgeResponse response = rejudgeSubmissions("PROBLEM", problemId, requestedIds, submissions);
        log.info(
                "Problem rejudge prepared. problemId={} foundCount={} queuedCount={} skippedCount={}",
                problemId,
                response.foundCount(),
                response.queuedCount(),
                response.skippedCount()
        );
        return response;
    }

    @Transactional
    public RejudgeResponse rejudgeContest(Long contestId) {
        if (!contestRepository.existsById(contestId)) {
            throw new ContestNotFoundException(contestId);
        }

        log.info("Starting contest-wide rejudge. contestId={}", contestId);
        List<Submission> submissions = submissionRepository.findAllByContest_Id(contestId);
        List<Long> requestedIds = submissions.stream()
                .map(Submission::getId)
                .toList();

        RejudgeResponse response = rejudgeSubmissions("CONTEST", contestId, requestedIds, submissions);
        log.info(
                "Contest rejudge prepared. contestId={} foundCount={} queuedCount={} skippedCount={}",
                contestId,
                response.foundCount(),
                response.queuedCount(),
                response.skippedCount()
        );
        return response;
    }

    // ─── Force Rejudge Public Methods ───────────────────────────────────────────

    @Transactional
    public RejudgeResponse forceRejudgeSelectedSubmissions(List<Long> submissionIds) {
        List<Long> requestedIds = normalizeSubmissionIds(submissionIds);
        log.info("Starting FORCE selected submission rejudge. requestedCount={}", requestedIds.size());

        List<Submission> submissions = submissionRepository.findAllById(requestedIds);
        RejudgeResponse response = rejudgeSubmissions("FORCE_SUBMISSIONS", null, requestedIds, submissions, true);

        log.info(
                "FORCE selected submission rejudge prepared. requestedCount={} foundCount={} queuedCount={} skippedCount={} missingCount={}",
                response.requestedCount(),
                response.foundCount(),
                response.queuedCount(),
                response.skippedCount(),
                response.missingSubmissionIds().size()
        );
        return response;
    }

    @Transactional
    public RejudgeResponse forceRejudgeProblem(Long problemId) {
        if (!problemRepository.existsById(problemId)) {
            throw new ProblemNotFoundException(problemId);
        }

        log.info("Starting FORCE problem rejudge. problemId={}", problemId);
        List<Submission> submissions = submissionRepository.findAllByProblem_Id(problemId);
        List<Long> requestedIds = submissions.stream()
                .map(Submission::getId)
                .toList();

        RejudgeResponse response = rejudgeSubmissions("FORCE_PROBLEM", problemId, requestedIds, submissions, true);
        log.info(
                "FORCE problem rejudge prepared. problemId={} foundCount={} queuedCount={} skippedCount={}",
                problemId,
                response.foundCount(),
                response.queuedCount(),
                response.skippedCount()
        );
        return response;
    }

    @Transactional
    public RejudgeResponse forceRejudgeContest(Long contestId) {
        if (!contestRepository.existsById(contestId)) {
            throw new ContestNotFoundException(contestId);
        }

        log.info("Starting FORCE contest-wide rejudge. contestId={}", contestId);
        List<Submission> submissions = submissionRepository.findAllByContest_Id(contestId);
        List<Long> requestedIds = submissions.stream()
                .map(Submission::getId)
                .toList();

        RejudgeResponse response = rejudgeSubmissions("FORCE_CONTEST", contestId, requestedIds, submissions, true);
        log.info(
                "FORCE contest rejudge prepared. contestId={} foundCount={} queuedCount={} skippedCount={}",
                contestId,
                response.foundCount(),
                response.queuedCount(),
                response.skippedCount()
        );
        return response;
    }

    // ─── Core Rejudge Logic ──────────────────────────────────────────────────────

    private RejudgeResponse rejudgeSubmissions(
            String scope,
            Long scopeId,
            List<Long> requestedIds,
            List<Submission> submissions
    ) {
        return rejudgeSubmissions(scope, scopeId, requestedIds, submissions, false);
    }

    private RejudgeResponse rejudgeSubmissions(
            String scope,
            Long scopeId,
            List<Long> requestedIds,
            List<Submission> submissions,
            boolean force
    ) {
        List<Submission> submissionsToQueue = new ArrayList<>();
        List<Long> queuedIds = new ArrayList<>();
        List<Long> skippedIds = new ArrayList<>();

        for (Submission submission : submissions) {
            if (!force && ACTIVE_VERDICTS.contains(submission.getVerdict())) {
                skippedIds.add(submission.getId());
                continue;
            }

            // Reserve the next judgeRunId immediately so old callbacks become stale
            Long currentRunId = submission.getJudgeRunId();
            Long nextJudgeRunId = (currentRunId == null) ? 1L : currentRunId + 1;
            submission.setJudgeRunId(nextJudgeRunId);
            submission.setVerdict(Verdict.PENDING_REJUDGE);
            submission.setExecutionTime(null);
            submission.setMemoryUsage(null);
            submissionsToQueue.add(submission);
            queuedIds.add(submission.getId());
        }

        if (!submissionsToQueue.isEmpty()) {
            // Capture SSE event data while entities are loaded (still in transaction).
            List<SubmissionStreamEvent> sseEvents = submissionsToQueue.stream()
                    .map(s -> submissionSsePublisher.buildEvent(SubmissionStreamEventType.REJUDGE_QUEUED, s))
                    .toList();
            List<SubmissionRejudgeQueuedEvent> scoreboardEvents = submissionsToQueue.stream()
                    .map(s -> new SubmissionRejudgeQueuedEvent(
                            s.getId(),
                            s.getContest().getId(),
                            s.getProblem().getId(),
                            s.getUser().getId()
                    ))
                    .toList();

            submissionRepository.saveAll(submissionsToQueue);
            publishAfterCommit(queuedIds);

            // Dispatch SSE events after the DB commit so frontend reads committed state.
            registerAfterCommit(() -> {
                sseEvents.forEach(submissionSsePublisher::dispatch);
                scoreboardEvents.forEach(eventPublisher::publishEvent);
            });
        }

        return new RejudgeResponse(
                scope,
                scopeId,
                requestedIds.size(),
                submissions.size(),
                queuedIds.size(),
                skippedIds.size(),
                List.copyOf(queuedIds),
                List.copyOf(skippedIds),
                missingSubmissionIds(requestedIds, submissions)
        );
    }

    private List<Long> normalizeSubmissionIds(List<Long> submissionIds) {
        if (submissionIds == null) {
            throw new InvalidRejudgeRequestException("submissionIds must not be null.");
        }

        List<Long> normalized = submissionIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(
                        ArrayList::new,
                        (ids, id) -> {
                            if (!ids.contains(id)) {
                                ids.add(id);
                            }
                        },
                        ArrayList::addAll
                );

        if (normalized.isEmpty()) {
            throw new InvalidRejudgeRequestException("submissionIds must contain at least one positive ID.");
        }

        return normalized;
    }

    private List<Long> missingSubmissionIds(List<Long> requestedIds, List<Submission> foundSubmissions) {
        Set<Long> foundIds = new LinkedHashSet<>();
        foundSubmissions.forEach(submission -> foundIds.add(submission.getId()));

        return requestedIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
    }

    private void publishAfterCommit(List<Long> submissionIds) {
        registerAfterCommit(() -> publishSubmissions(submissionIds));
    }

    private void registerAfterCommit(Runnable task) {
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

    private void publishSubmissions(List<Long> submissionIds) {
        int publishedCount = 0;
        int failedCount = 0;

        for (Long submissionId : submissionIds) {
            try {
                submissionProducer.sendSubmission(submissionId);
                publishedCount++;
            } catch (RuntimeException ex) {
                failedCount++;
                log.error("Failed to republish submission for rejudge. submissionId={}", submissionId, ex);
            }
        }

        log.info(
                "Rejudge republish completed. requestedPublishCount={} publishedCount={} failedCount={}",
                submissionIds.size(),
                publishedCount,
                failedCount
        );
    }
}
