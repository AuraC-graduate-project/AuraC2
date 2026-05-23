package com.server.contestControl.submissionServer.service.compare;

import com.server.contestControl.contestServer.enums.ComparePolicy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

@Service
public class OutputComparator {

    public ComparisonResult compare(
            ComparePolicy policy,
            String expected,
            String actual,
            Double absoluteEpsilon,
            Double relativeEpsilon
    ) {
        ComparePolicy effectivePolicy = policy == null ? ComparePolicy.EXACT : policy;
        return switch (effectivePolicy) {
            case EXACT -> exact(expected, actual);
            case NORMALIZED_TEXT -> normalizedText(expected, actual);
            case TOKEN_NORMALIZED -> tokenNormalized(expected, actual);
            case FLOAT_TOLERANCE -> floatTolerance(expected, actual, absoluteEpsilon, relativeEpsilon);
        };
    }

    private ComparisonResult exact(String expected, String actual) {
        return result(normalizeNull(expected).equals(normalizeNull(actual)), ComparePolicy.EXACT);
    }

    private ComparisonResult normalizedText(String expected, String actual) {
        return result(normalizeText(expected).equals(normalizeText(actual)), ComparePolicy.NORMALIZED_TEXT);
    }

    private ComparisonResult tokenNormalized(String expected, String actual) {
        String[] expectedTokens = tokens(expected);
        String[] actualTokens = tokens(actual);

        if (expectedTokens.length != actualTokens.length) {
            return ComparisonResult.rejected(
                    "Output token count differs under TOKEN_NORMALIZED compare policy"
            );
        }

        return result(Arrays.equals(expectedTokens, actualTokens), ComparePolicy.TOKEN_NORMALIZED);
    }

    private ComparisonResult floatTolerance(
            String expected,
            String actual,
            Double absoluteEpsilon,
            Double relativeEpsilon
    ) {
        String[] expectedTokens = tokens(expected);
        String[] actualTokens = tokens(actual);

        if (expectedTokens.length != actualTokens.length) {
            return ComparisonResult.rejected(
                    "Output token count differs under FLOAT_TOLERANCE compare policy"
            );
        }

        BigDecimal absEpsilon = epsilon(absoluteEpsilon);
        BigDecimal relEpsilon = epsilon(relativeEpsilon);

        for (int i = 0; i < expectedTokens.length; i++) {
            Optional<BigDecimal> expectedNumber = parseFiniteDecimal(expectedTokens[i]);
            Optional<BigDecimal> actualNumber = parseFiniteDecimal(actualTokens[i]);
            if (expectedNumber.isEmpty() || actualNumber.isEmpty()) {
                return ComparisonResult.rejected(
                        "Non-numeric token encountered under FLOAT_TOLERANCE compare policy"
                );
            }

            BigDecimal difference = expectedNumber.get().subtract(actualNumber.get()).abs();
            BigDecimal scale = expectedNumber.get().abs().max(actualNumber.get().abs());
            BigDecimal tolerance = absEpsilon.max(relEpsilon.multiply(scale));
            if (difference.compareTo(tolerance) > 0) {
                return ComparisonResult.rejected(
                        "Numeric output differs beyond FLOAT_TOLERANCE epsilon"
                );
            }
        }

        return ComparisonResult.accepted();
    }

    private ComparisonResult result(boolean matches, ComparePolicy policy) {
        if (matches) {
            return ComparisonResult.accepted();
        }

        return ComparisonResult.rejected("Output mismatch under " + policy + " compare policy");
    }

    private String normalizeText(String value) {
        String normalized = normalizeNull(value)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].stripTrailing();
        }
        return String.join("\n", lines).stripTrailing();
    }

    private String[] tokens(String value) {
        String normalized = normalizeNull(value).trim();
        if (normalized.isEmpty()) {
            return new String[0];
        }

        return normalized.split("\\s+");
    }

    private String normalizeNull(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal epsilon(Double value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(value);
    }

    private Optional<BigDecimal> parseFiniteDecimal(String token) {
        try {
            return Optional.of(new BigDecimal(token));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public record ComparisonResult(boolean matches, String diagnostic) {
        public static ComparisonResult accepted() {
            return new ComparisonResult(true, null);
        }

        public static ComparisonResult rejected(String diagnostic) {
            return new ComparisonResult(false, diagnostic);
        }
    }
}
