package com.server.contestControl.contestServer.dto.testcase;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TestCaseRequest {

    @JsonProperty("inputData")
    private String inputData;

    @JsonProperty("expectedOutput")
    private String expectedOutput;

    @JsonProperty("isPublic")
    private boolean isPublic;
}