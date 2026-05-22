package com.server.contestControl.submissionServer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Judge0SubmissionDTO {

    @JsonProperty("source_code")
    private final String sourceCode;

    @JsonProperty("language_id")
    private final int languageId;

    @JsonProperty("stdin")
    private final String stdin;              // concatenated test cases

    @JsonProperty("expected_output")
    private final String expectedOutput;     // concatenated outputs

    @JsonProperty("callback_url")
    private final String callbackUrl;

    @JsonProperty("cpu_time_limit")
    private final Double cpuTimeLimit;

    @JsonProperty("memory_limit")
    private final Integer memoryLimit;
}
