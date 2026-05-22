package com.server.contestControl.contestServer.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "test_cases",
        indexes = {
                @Index(name = "idx_test_cases_problem", columnList = "problem_id"),
                @Index(name = "idx_test_cases_problem_public", columnList = "problem_id, is_public")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String inputData;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;
}
