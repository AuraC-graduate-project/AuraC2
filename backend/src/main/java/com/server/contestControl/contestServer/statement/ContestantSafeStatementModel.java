package com.server.contestControl.contestServer.statement;

import java.util.List;

public record ContestantSafeStatementModel(
        String title,
        Integer timeLimit,
        Integer memoryLimit,
        String statement,
        String inputFormat,
        String outputFormat,
        String constraintsText,
        String publicNotes,
        List<Sample> publicSamples
) {
    public record Sample(String input, String output) {
    }
}
