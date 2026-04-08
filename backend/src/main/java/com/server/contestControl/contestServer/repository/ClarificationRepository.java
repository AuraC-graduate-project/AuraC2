package com.server.contestControl.contestServer.repository;

import com.server.contestControl.contestServer.entity.Clarification;
import com.server.contestControl.contestServer.enums.ClarificationStatus;
import com.server.contestControl.contestServer.enums.ClarificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClarificationRepository extends JpaRepository<Clarification, Long> {

    // Team queries - get my clarifications
    List<Clarification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Team queries - get public clarifications in a contest
    List<Clarification> findByContestIdAndReplyTypeOrderByCreatedAtDesc(
            Long contestId,
            ClarificationType replyType
    );

    // Admin queries - get all clarifications for a contest
    List<Clarification> findByContestIdOrderByCreatedAtDesc(Long contestId);

    // Admin queries - get all clarifications
    List<Clarification> findAllByOrderByCreatedAtDesc();

    // Admin queries - filter by status
    List<Clarification> findByStatusOrderByCreatedAtDesc(ClarificationStatus status);

    // Combined query for team: my clarifications in a specific contest
    List<Clarification> findByUserIdAndContestIdOrderByCreatedAtDesc(Long userId, Long contestId);

    // Get only ANSWERED public clarifications (prevents pending leaks)
    List<Clarification> findByContestIdAndReplyTypeAndStatusOrderByCreatedAtDesc(
            Long contestId,
            ClarificationType replyType,
            ClarificationStatus status
    );
}
