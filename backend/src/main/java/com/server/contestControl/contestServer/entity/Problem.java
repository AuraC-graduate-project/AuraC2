package com.server.contestControl.contestServer.entity;

import com.server.contestControl.contestServer.enums.Difficulty;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "problems")
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

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer timeLimit;
    private Integer memoryLimit;

    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TestCase> testCases;
}
