package com.server.contestControl.submissionServer.entity;

import com.server.contestControl.submissionServer.enums.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "submission_judge_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_submission_judge_result_run_case",
                columnNames = {"submission_id", "judge_run_id", "test_case_number"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionJudgeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(name = "judge_run_id", nullable = false)
    private Long judgeRunId;

    @Column(name = "test_case_number", nullable = false)
    private Integer testCaseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Verdict verdict;

    private Integer executionTime;
    private Integer memoryUsage;

    private Integer judge0StatusId;

    @Column(length = 128)
    private String judge0StatusDescription;

    @Column(length = 4096)
    private String diagnostic;

    private LocalDateTime receivedAt;

    @PrePersist
    @PreUpdate
    public void touchReceivedAt() {
        this.receivedAt = LocalDateTime.now();
    }
}
