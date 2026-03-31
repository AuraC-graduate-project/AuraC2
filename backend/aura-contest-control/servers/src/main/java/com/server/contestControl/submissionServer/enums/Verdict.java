package com.server.contestControl.submissionServer.enums;

public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    TLE,
    COMPILATION_ERROR,
    RUNTIME_ERROR,
    INTERNAL_ERROR,
    PENDING,
    RUNNING;

    public static Verdict fromJudge0Status(int statusId) {
        return switch (statusId) {
            case 3 -> ACCEPTED;
            case 4 -> WRONG_ANSWER;
            case 5 -> TLE;
            case 6 -> COMPILATION_ERROR;
            case 7 -> RUNTIME_ERROR;
            case 13 -> INTERNAL_ERROR; // judge0 system error
            default -> PENDING;
        };
    }
}