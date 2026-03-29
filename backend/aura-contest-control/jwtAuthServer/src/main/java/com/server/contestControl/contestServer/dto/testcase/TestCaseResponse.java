package com.server.contestControl.contestServer.dto.testcase;

import com.server.contestControl.contestServer.entity.TestCase;
import lombok.Builder;

@Builder
public record TestCaseResponse(
        Long id,
        String inputData,
        String expectedOutput,
        boolean isPublic
) {

    public static TestCaseResponse fromEntity(TestCase entity) {
        return new TestCaseResponse(
                entity.getId(),
                entity.getInputData(),
                entity.getExpectedOutput(),
                entity.isPublic()   //  THIS FIXES YOUR POSTMAN ISSUE
        );
    }
}