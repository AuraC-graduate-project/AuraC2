package com.server.contestControl.contestServer.oracle.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class GeneratedTestBatchRequest {
    @Positive
    private Integer testCount;

    private Long seed;

    @Positive
    private Long submissionId;
}
