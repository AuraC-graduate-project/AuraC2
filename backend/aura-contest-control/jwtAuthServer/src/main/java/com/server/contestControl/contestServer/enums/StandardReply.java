package com.server.contestControl.contestServer.enums;

public enum StandardReply {
    NO_COMMENT("No comment."),
    READ_PROBLEM_STATEMENT_CAREFULLY("Read the problem statement carefully."),
    YES("Yes."),
    NO("No."),
    ANSWERED("Answered."),
    CUSTOM(null);

    private final String text;

    StandardReply(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
