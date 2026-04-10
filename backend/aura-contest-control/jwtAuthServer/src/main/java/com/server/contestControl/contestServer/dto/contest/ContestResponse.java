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
    private String startTime;      // ISO 8601 with Z suffix (UTC)
    private String endTime;        // Computed: startTime + durationMinutes
    private String effectiveEndTime; // Computed contest end time used for effective lifecycle checks
    private Integer scoreboardFreezeMinutes;
    private String scoreboardFreezeTime; // Computed: endTime - scoreboardFreezeMinutes
    private Integer penaltyMinutes;
    private Boolean scoreboardFrozen;
}
