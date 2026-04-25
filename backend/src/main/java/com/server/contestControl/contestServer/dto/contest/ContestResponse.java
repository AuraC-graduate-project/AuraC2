package com.server.contestControl.contestServer.dto.contest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContestResponse {
    private Long id;
    private String title;
    private String description;
    private Integer durationMinutes;
    private String status;
    private String effectiveState; // Computed lifecycle state based on time + status overrides
    private Boolean statusLocked;  // If true, scheduler will not auto-update this contest's status
    private String startTime;      // Scheduled start (ISO 8601 UTC) — planning/display only
    private String actualStartTime; // ISO 8601 UTC, set on manual start; null until then
    private String pausedAt;       // ISO 8601 UTC, set while PAUSED; null otherwise
    private Long totalPauseMillis; // Accumulated pause time across all pause/resume cycles
    private Long remainingMillis;  // Countdown value the UI should display (pause-aware)
    private String endTime;        // Scheduled end: startTime + durationMinutes
    private String effectiveEndTime; // Live pause-aware end; null when UPCOMING or PAUSED
    private Integer scoreboardFreezeMinutes;
    private String scoreboardFreezeTime; // effectiveEndTime - scoreboardFreezeMinutes
    private Integer penaltyMinutes;
    private Boolean scoreboardFrozen;
}
