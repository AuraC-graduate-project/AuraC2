package com.server.contestControl.submissionServer.util;

public final class Judge0AuditUtil {

    public static final int MAX_STATUS_DESCRIPTION_LENGTH = 128;
    public static final int MAX_DIAGNOSTIC_LENGTH = 4096;

    private Judge0AuditUtil() {
    }

    public static String safeStatusDescription(String value) {
        return truncate(clean(value), MAX_STATUS_DESCRIPTION_LENGTH);
    }

    public static String firstSafeDiagnostic(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) {
                return truncate(cleaned, MAX_DIAGNOSTIC_LENGTH);
            }
        }

        return null;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.replace("\u0000", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }

        return value.substring(0, maxLength - 3) + "...";
    }
}
