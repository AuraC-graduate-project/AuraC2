package com.server.contestControl.contestServer.enums;

import java.util.Locale;

public enum ValidationMode {
    BUILTIN_COMPARE_POLICY,
    CUSTOM_VALIDATOR;

    public static ValidationMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return BUILTIN_COMPARE_POLICY;
        }

        return ValidationMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
