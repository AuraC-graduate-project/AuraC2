package com.server.contestControl.contestServer.dto.contest;


import com.server.contestControl.contestServer.entity.Contest;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@Data
@Builder
public class ContestResponse {
    private Long id;
    private String title;
    private String description;
    private Integer durationMinutes;
    private String status;
    private String startTime;      // ISO 8601 with Z suffix (UTC)
    private String endTime;        // Computed: startTime + durationMinutes
    private Integer scoreboardFreezeMinutes;
    private String scoreboardFreezeTime; // Computed: endTime - scoreboardFreezeMinutes
    private Integer penaltyMinutes;
    private Boolean scoreboardFrozen;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    public static ContestResponse fromEntity(Contest contest) {
        Instant endTime = contest.getEndTime();
        Instant freezeTime = contest.getScoreboardFreezeTime();

        return ContestResponse.builder()
                .id(contest.getId())
                .title(contest.getTitle())
                .description(contest.getDescription())
                .durationMinutes(contest.getDurationMinutes())
                .status(contest.getStatus().name())
                .startTime(formatInstant(contest.getStartTime()))
                .endTime(formatInstant(endTime))
                .scoreboardFreezeMinutes(contest.getScoreboardFreezeMinutes())
                .scoreboardFreezeTime(formatInstant(freezeTime))
                .penaltyMinutes(contest.getPenaltyMinutes())
                .scoreboardFrozen(contest.isScoreboardFrozen())
                .build();
    }

    private static String formatInstant(Instant instant) {
        return instant != null ? ISO_FORMATTER.format(instant) : null;
    }
}