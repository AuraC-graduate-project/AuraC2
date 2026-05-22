package com.server.contestControl.submissionServer.entity;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.submissionServer.enums.Verdict;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "submissions",
        indexes = {
                @Index(name = "idx_submissions_contest", columnList = "contest_id"),
                @Index(name = "idx_submissions_problem", columnList = "problem_id"),
                @Index(name = "idx_submissions_user", columnList = "user_id"),
                @Index(name = "idx_submissions_verdict", columnList = "verdict"),
                @Index(name = "idx_submissions_created_at", columnList = "created_at"),
                @Index(name = "idx_submissions_contest_user", columnList = "contest_id, user_id"),
                @Index(name = "idx_submissions_contest_problem", columnList = "contest_id, problem_id"),
                @Index(name = "idx_submissions_contest_verdict", columnList = "contest_id, verdict")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verdict verdict;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    private Integer executionTime;
    private Integer memoryUsage;

    @Column(nullable = false)
    private Long judgeRunId;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.verdict == null) {
            this.verdict = Verdict.PENDING;
        }
        if (this.judgeRunId == null) {
            this.judgeRunId = 0L;
        }
    }
}
