package com.server.contestControl.contestServer.moderation.repository;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ContestStatus;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.moderation.entity.ContestModerationAuditLog;
import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ContestModerationAuditLogRepositoryTest {

    @Autowired private ContestModerationAuditLogRepository auditLogRepository;
    @Autowired private ContestRepository contestRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void searchBuildsOnlyProvidedPredicatesForOptionalFilters() {
        Contest contest = contestRepository.save(contest("Contest"));
        Contest otherContest = contestRepository.save(contest("Other"));
        Problem problem = problemRepository.save(problem(contest));
        User team = userRepository.save(user("team1", Role.TEAM));
        User admin = userRepository.save(user("admin", Role.ADMIN));

        LocalDateTime firstTime = LocalDateTime.parse("2026-05-25T10:15:00");
        LocalDateTime secondTime = LocalDateTime.parse("2026-05-26T10:15:00");

        ContestModerationAuditLog first = auditLogRepository.save(log(
                contest,
                team,
                admin,
                null,
                ModerationActionType.HIDE_FROM_SCOREBOARD,
                firstTime
        ));
        ContestModerationAuditLog second = auditLogRepository.save(log(
                contest,
                null,
                admin,
                problem,
                ModerationActionType.ADMIN_RUN_LAB_EXECUTION,
                secondTime
        ));
        ContestModerationAuditLog third = auditLogRepository.save(log(
                otherContest,
                team,
                admin,
                null,
                ModerationActionType.DISABLE_RUN,
                LocalDateTime.parse("2026-05-27T10:15:00")
        ));

        assertThat(auditLogRepository.search(null, null, null, null, null, null))
                .extracting(ContestModerationAuditLog::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
        assertThat(auditLogRepository.search(contest.getId(), null, null, null, null, null))
                .extracting(ContestModerationAuditLog::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(auditLogRepository.search(null, null, null, ModerationActionType.ADMIN_RUN_LAB_EXECUTION, null, null))
                .extracting(ContestModerationAuditLog::getId)
                .containsExactly(second.getId());
        assertThat(auditLogRepository.search(null, null, null, null, secondTime, null))
                .extracting(ContestModerationAuditLog::getActionType)
                .containsExactly(ModerationActionType.DISABLE_RUN, ModerationActionType.ADMIN_RUN_LAB_EXECUTION);
        assertThat(auditLogRepository.search(null, null, null, null, null, firstTime))
                .extracting(ContestModerationAuditLog::getId)
                .containsExactly(first.getId());
        assertThat(auditLogRepository.search(null, null, admin.getId(), null, firstTime, secondTime))
                .extracting(ContestModerationAuditLog::getId)
                .containsExactly(second.getId(), first.getId());
    }

    private Contest contest(String title) {
        return Contest.builder()
                .title(title)
                .startTime(Instant.now())
                .durationMinutes(300)
                .status(ContestStatus.UPCOMING)
                .build();
    }

    private Problem problem(Contest contest) {
        return Problem.builder()
                .contest(contest)
                .title("A+B")
                .description("Solve it")
                .timeLimit(1000)
                .memoryLimit(128)
                .difficulty(Difficulty.EASY)
                .build();
    }

    private User user(String username, Role role) {
        return User.builder()
                .username(username)
                .password("password")
                .role(role)
                .build();
    }

    private ContestModerationAuditLog log(
            Contest contest,
            User team,
            User admin,
            Problem problem,
            ModerationActionType actionType,
            LocalDateTime createdAt
    ) {
        return ContestModerationAuditLog.builder()
                .contest(contest)
                .team(team)
                .admin(admin)
                .problem(problem)
                .actionType(actionType)
                .oldValueJson("{}")
                .newValueJson("{}")
                .createdAt(createdAt)
                .build();
    }
}
