package com.server.contestControl.submissionServer.controller;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.submissionServer.sse.AdminSubmissionSseRegistry;
import com.server.contestControl.submissionServer.sse.SubmissionSseRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Exposes SSE endpoints for live submission verdict updates.
 *
 * <ul>
 *   <li>{@code GET /api/submissions/stream} — team stream, targeted by user ID.
 *       Only events for the authenticated team's own submissions are pushed.</li>
 *   <li>{@code GET /api/admin/submissions/stream} — admin stream, broadcast.
 *       Every connected admin receives all submission updates.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class SubmissionStreamController {

    private static final long STREAM_TIMEOUT_MILLIS = 30L * 60_000L;

    private final SubmissionSseRegistry submissionSseRegistry;
    private final AdminSubmissionSseRegistry adminSubmissionSseRegistry;

    @PreAuthorize("hasRole('TEAM')")
    @GetMapping(value = "/api/submissions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter teamSubmissionStream(@AuthenticationPrincipal User user) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        submissionSseRegistry.register(user.getId(), emitter);
        log.debug("[SubmissionStreamController] Team {} connected to submission stream", user.getId());
        return emitter;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/api/admin/submissions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminSubmissionStream(@AuthenticationPrincipal User admin) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        adminSubmissionSseRegistry.register(emitter);
        log.debug("[SubmissionStreamController] Admin {} connected to submission stream", admin.getId());
        return emitter;
    }
}
