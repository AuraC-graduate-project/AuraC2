package com.server.contestControl.submissionServer.enums;

public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    TLE,
    COMPILATION_ERROR,
    RUNTIME_ERROR,
    INTERNAL_ERROR,
    PENDING,
    PENDING_REJUDGE,
    RUNNING;

    public static Verdict fromJudge0Status(int statusId) {
        return switch (statusId) {
            case 3 -> ACCEPTED;
            case 4 -> WRONG_ANSWER;
            case 5 -> TLE;
            case 6 -> COMPILATION_ERROR;
            // Judge0 emits 7..12 for distinct runtime-fault flavors (SIGSEGV,
            // SIGXFSZ, SIGFPE divide-by-zero, SIGABRT, NZEC, Other). All are
            // terminal runtime failures and should resolve as RUNTIME_ERROR.
            case 7, 8, 9, 10, 11, 12 -> RUNTIME_ERROR;
            case 13, 14 -> INTERNAL_ERROR; // Judge0 system / exec-format error
            case 1, 2 -> PENDING; // In Queue / Processing are non-terminal
            default -> INTERNAL_ERROR; // Unknown terminal-looking statuses are unsafe to accept
        };
    }
}
