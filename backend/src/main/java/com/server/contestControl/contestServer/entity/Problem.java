package com.server.contestControl.contestServer.entity;

import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(
        name = "problems",
        indexes = {
                @Index(name = "idx_problems_contest", columnList = "contest_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer timeLimit;

    @Column(nullable = false)
    private Integer memoryLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "compare_policy", nullable = false, length = 32)
    private ComparePolicy comparePolicy = ComparePolicy.EXACT;

    @Column(name = "float_absolute_epsilon")
    private Double floatAbsoluteEpsilon;

    @Column(name = "float_relative_epsilon")
    private Double floatRelativeEpsilon;

    @Builder.Default
    @Column(name = "balloon_color", nullable = false, length = 7)
    private String balloonColor = "#2563EB";

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TestCase> testCases;

    @PrePersist
    @PreUpdate
    public void applyDefaults() {
        if (comparePolicy == null) {
            comparePolicy = ComparePolicy.EXACT;
        }
    }
}
