package com.server.contestControl.contestServer.oracle.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class GeneratedTestPromotionRequest {
    @NotEmpty
    private List<@Positive Long> generatedTestCaseIds;
}
