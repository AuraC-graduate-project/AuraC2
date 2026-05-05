package com.server.contestControl.contestServer.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ContestRequest(
        @JsonProperty("title")
        @NotBlank(message = "Title is required")
        @Size(max = 255)
        String title,

        @JsonProperty("description") String description,

        @JsonProperty("startTime")
        @NotNull(message = "Start time is required")
        Instant startTime,

        @JsonProperty("durationMinutes")
        @NotNull(message = "Duration is required")
        @Min(value = 1, message = "Duration must be at least 1 minute")
        Integer durationMinutes,

        @Min(value = 0, message = "Scoreboard freeze time cannot be negative")
        @JsonProperty("scoreboardFreezeMinutes") Integer scoreboardFreezeMinutes,

        @Min(value = 0, message = "Penalty minutes cannot be negative")
        @JsonProperty("penaltyMinutes") Integer penaltyMinutes
) {
    public ContestRequest {
        if (penaltyMinutes == null) {
            penaltyMinutes = 20; // ICPC default
        }
    }
}
