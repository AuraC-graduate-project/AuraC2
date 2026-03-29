package com.server.contestControl.contestServer.dto.contest;


import com.server.contestControl.contestServer.entity.Contest;
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
    private String startTime;

    public static ContestResponse fromEntity(Contest contest) {
        return ContestResponse.builder()
                .id(contest.getId())
                .title(contest.getTitle())
                .description(contest.getDescription())
                .durationMinutes(contest.getDurationMinutes())
                .status(contest.getStatus().name())
                .startTime(contest.getStartTime().toString())
                .build();
    }
}