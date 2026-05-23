package com.server.contestControl.contestServer.oracle.entity;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.oracle.enums.GeneratedTestBatchStatus;
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
        name = "generated_test_batches",
        indexes = {
                @Index(name = "idx_generated_test_batches_problem_created", columnList = "problem_id, created_at"),
                @Index(name = "idx_generated_test_batches_status", columnList = "status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedTestBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false)
    private Long seed;

    @Column(name = "generator_source_hash", nullable = false, length = 64)
    private String generatorSourceHash;

    @Column(name = "reference_solution_source_hash", nullable = false, length = 64)
    private String referenceSolutionSourceHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GeneratedTestBatchStatus status;

    @Column(name = "requested_count", nullable = false)
    private Integer requestedCount;

    @Builder.Default
    @Column(name = "generated_count", nullable = false)
    private Integer generatedCount = 0;

    @Builder.Default
    @Column(name = "invalid_count", nullable = false)
    private Integer invalidCount = 0;

    @Builder.Default
    @Column(name = "counterexample_count", nullable = false)
    private Integer counterexampleCount = 0;

    @Column(length = 4096)
    private String diagnostic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (generatedCount == null) {
            generatedCount = 0;
        }
        if (invalidCount == null) {
            invalidCount = 0;
        }
        if (counterexampleCount == null) {
            counterexampleCount = 0;
        }
    }
}
