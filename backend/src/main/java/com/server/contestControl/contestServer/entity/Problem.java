package com.server.contestControl.contestServer.entity;

import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.ValidationMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
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

    @Column(columnDefinition = "TEXT")
    private String statement;

    @Column(name = "input_format", columnDefinition = "TEXT")
    private String inputFormat;

    @Column(name = "output_format", columnDefinition = "TEXT")
    private String outputFormat;

    @Column(name = "constraints_text", columnDefinition = "TEXT")
    private String constraintsText;

    @Column(name = "public_notes", columnDefinition = "TEXT")
    private String publicNotes;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

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
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_mode", nullable = false, length = 32)
    private ValidationMode validationMode = ValidationMode.BUILTIN_COMPARE_POLICY;

    @Column(name = "validator_language_id")
    private Integer validatorLanguageId;

    @Column(name = "validator_source", columnDefinition = "TEXT")
    private String validatorSource;

    @Column(name = "validator_source_hash", length = 64)
    private String validatorSourceHash;

    @Builder.Default
    @Column(name = "validator_enabled", nullable = false)
    private Boolean validatorEnabled = false;

    @Column(name = "validator_created_at")
    private LocalDateTime validatorCreatedAt;

    @Column(name = "validator_updated_at")
    private LocalDateTime validatorUpdatedAt;

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
        if (validationMode == null) {
            validationMode = ValidationMode.BUILTIN_COMPARE_POLICY;
        }
        if (validatorEnabled == null) {
            validatorEnabled = false;
        }
    }

    public boolean hasActiveCustomValidator() {
        return validationMode == ValidationMode.CUSTOM_VALIDATOR && Boolean.TRUE.equals(validatorEnabled);
    }
}
