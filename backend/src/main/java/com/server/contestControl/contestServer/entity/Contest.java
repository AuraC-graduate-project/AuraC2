package com.server.contestControl.contestServer.entity;

import com.server.contestControl.contestServer.enums.ContestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "contests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private Instant startTime;
    private Integer durationMinutes;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "contest", cascade = CascadeType.ALL)
    private List<Problem> problems;

    @Enumerated(EnumType.STRING)
    private ContestStatus status;

    @PrePersist
    public void prePersist() {
        if (startTime == null) {
            startTime = Instant.now();
        }
    }
}
