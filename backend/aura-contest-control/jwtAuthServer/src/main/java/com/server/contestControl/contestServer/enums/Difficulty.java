package com.server.contestControl.contestServer.enums;

public enum Difficulty {
    EASY, MEDIUM, HARD;

    public static Difficulty fromString(String value) {
        for (Difficulty difficulty : Difficulty.values()) {
            if (difficulty.name().equalsIgnoreCase(value)) {   // Ignore case
                return difficulty;
            }
        }
        throw new IllegalArgumentException(
                "Invalid difficulty: " + value + ". Valid values: EASY, MEDIUM, HARD"
        );
    }
}
