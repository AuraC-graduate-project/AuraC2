package com.server.contestControl.submissionServer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
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
}