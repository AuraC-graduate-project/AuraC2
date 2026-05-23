package com.server.contestControl.contestServer.oracle.entity;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.ValidationMode;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "counterexamples",
        indexes = {
                @Index(name = "idx_counterexamples_problem_created", columnList = "problem_id, created_at"),
                @Index(name = "idx_counterexamples_submission", columnList = "submission_id"),
                @Index(name = "idx_counterexamples_promoted", columnList = "promoted")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Counterexample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_test_case_id", nullable = false)
    private GeneratedTestCase generatedTestCase;

    @Column(name = "judge_run_id", nullable = false)
    private Long judgeRunId;

    @Column(name = "generated_input", nullable = false, columnDefinition = "TEXT")
    private String generatedInput;

    @Column(name = "reference_output", nullable = false, columnDefinition = "TEXT")
    private String referenceOutput;

    @Column(name = "team_output", columnDefinition = "TEXT")
    private String teamOutput;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Verdict verdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "compare_policy", length = 32)
    private ComparePolicy comparePolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_mode", length = 32)
    private ValidationMode validationMode;

    @Column(length = 4096)
    private String diagnostic;

    @Builder.Default
    @Column(nullable = false)
    private Boolean promoted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoted_test_case_id")
    private TestCase promotedTestCase;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (promoted == null) {
            promoted = false;
        }
    }
}
