package com.server.contestControl.contestServer.oracle.entity;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.oracle.enums.GeneratedTestCaseStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "generated_test_cases",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_generated_test_case_batch_number",
                columnNames = {"batch_id", "test_number"}
        ),
        indexes = {
                @Index(name = "idx_generated_test_cases_problem", columnList = "problem_id"),
                @Index(name = "idx_generated_test_cases_batch", columnList = "batch_id"),
                @Index(name = "idx_generated_test_cases_promoted", columnList = "promoted")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private GeneratedTestBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "test_number", nullable = false)
    private Integer testNumber;

    @Column(nullable = false)
    private Long seed;

    @Column(name = "input_data", columnDefinition = "TEXT")
    private String inputData;

    @Column(name = "reference_output", columnDefinition = "TEXT")
    private String referenceOutput;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GeneratedTestCaseStatus status;

    @Builder.Default
    @Column(nullable = false)
    private Boolean promoted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoted_test_case_id")
    private TestCase promotedTestCase;

    @Column(length = 4096)
    private String diagnostic;

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
