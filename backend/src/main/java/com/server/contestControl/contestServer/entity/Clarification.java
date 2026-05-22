package com.server.contestControl.contestServer.entity;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.contestServer.enums.ClarificationStatus;
import com.server.contestControl.contestServer.enums.ClarificationType;
import com.server.contestControl.contestServer.enums.StandardReply;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "clarifications",
        indexes = {
                @Index(name = "idx_clarifications_contest_created", columnList = "contest_id, created_at"),
                @Index(name = "idx_clarifications_contest_reply_status_created", columnList = "contest_id, reply_type, status, created_at"),
                @Index(name = "idx_clarifications_user_contest_created", columnList = "user_id, contest_id, created_at"),
                @Index(name = "idx_clarifications_status_created", columnList = "status, created_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Clarification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Question fields
    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Reply fields
    @Enumerated(EnumType.STRING)
    private StandardReply standardReply;  // ICPC-style quick reply

    @Column(columnDefinition = "TEXT")
    private String reply;  // Custom reply text (used when standardReply = CUSTOM)

    private LocalDateTime repliedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replied_by_admin_id")
    private User repliedByAdmin;

    // Status & Type
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClarificationStatus status;

    @Enumerated(EnumType.STRING)
    private ClarificationType replyType;  // PRIVATE or PUBLIC (null if not answered yet)

    @PrePersist
    void setDefaults() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ClarificationStatus.PENDING;
        }
    }
}
