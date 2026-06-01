package com.server.contestControl.contestServer.util;

import com.server.contestControl.contestServer.exceptions.InvalidBalloonColorException;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ProblemBalloonColors {
    public static final String DEFAULT = "#2563EB";
    private static final String[] FALLBACK_PALETTE = {
            "#2563EB",
            "#E11D48",
            "#F59E0B",
            "#16A34A",
            "#7C3AED",
            "#DB2777",
            "#0891B2",
            "#475569"
    };

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private ProblemBalloonColors() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        String normalized = addHashIfNeeded(value.trim());
        if (!HEX_COLOR.matcher(normalized).matches()) {
            throw new InvalidBalloonColorException(value);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public static String normalizeOrFallback(String value, int problemIndex) {
        if (value == null || value.isBlank()) {
            return fallbackForIndex(problemIndex);
        }
        return normalize(value);
    }

    public static String valueOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        String normalized = addHashIfNeeded(value.trim());
        return HEX_COLOR.matcher(normalized).matches()
                ? normalized.toUpperCase(Locale.ROOT)
                : DEFAULT;
    }

    public static String valueOrFallback(String value, int problemIndex) {
        if (value == null || value.isBlank()) {
            return fallbackForIndex(problemIndex);
        }

        String normalized = addHashIfNeeded(value.trim());
        if (!HEX_COLOR.matcher(normalized).matches()) {
            return fallbackForIndex(problemIndex);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public static String fallbackForIndex(int problemIndex) {
        return FALLBACK_PALETTE[Math.floorMod(problemIndex, FALLBACK_PALETTE.length)];
    }

    private static String addHashIfNeeded(String value) {
        return value.startsWith("#") ? value : "#" + value;
    }
}
