package com.server.contestControl.contestServer.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ContestUpdateRequest(
        @JsonProperty("title")
        @NotBlank
        @Size(max = 255)
        String title,
        @JsonProperty("startTime")
        @NotNull
        Instant startTime,
        @JsonProperty("durationMinutes")
        @NotNull
        @Positive
        Integer durationMinutes,
        @JsonProperty("scoreboardFreezeMinutes")
        @Min(value = 0, message = "Scoreboard freeze time cannot be negative")
        Integer scoreboardFreezeMinutes,
        @JsonProperty("penaltyMinutes")
        @NotNull
        @Min(value = 0, message = "Penalty minutes cannot be negative")
        Integer penaltyMinutes
) {}
