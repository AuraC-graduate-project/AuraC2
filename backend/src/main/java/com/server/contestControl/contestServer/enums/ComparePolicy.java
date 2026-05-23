package com.server.contestControl.contestServer.enums;

import java.util.Locale;

public enum ComparePolicy {
    EXACT,
    NORMALIZED_TEXT,
    TOKEN_NORMALIZED,
    FLOAT_TOLERANCE;

    public static ComparePolicy fromString(String value) {
        if (value == null || value.isBlank()) {
            return EXACT;
        }

        return ComparePolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
