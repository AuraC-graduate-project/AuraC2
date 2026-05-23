package com.server.contestControl.contestServer.oracle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class OracleProgramRequest {
    @Positive
    private Integer languageId;

    @NotBlank
    private String source;

    private Boolean active;

    @Positive
    private Integer defaultTestCount;
}
