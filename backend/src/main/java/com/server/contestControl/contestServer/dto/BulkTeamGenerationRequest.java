package com.server.contestControl.contestServer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkTeamGenerationRequest {
    private String prefix;
    private Integer startNumber;
    private Integer endNumber;
    private Integer passwordLength;
}
