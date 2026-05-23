package com.server.contestControl.submissionServer.service.compare;

import com.server.contestControl.contestServer.enums.ComparePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTest {

    private final OutputComparator comparator = new OutputComparator();

    @Test
    void exactAcceptsOnlyIdenticalOutput() {
        assertThat(compare(ComparePolicy.EXACT, "hello\n", "hello\n").matches()).isTrue();
        assertThat(compare(ComparePolicy.EXACT, "hello\n", "hello").matches()).isFalse();
    }

    @Test
    void normalizedTextIgnoresLineEndingsTrailingWhitespaceAndFinalNewline() {
        OutputComparator.ComparisonResult result = compare(
                ComparePolicy.NORMALIZED_TEXT,
                "alpha  \r\nbeta\t\n",
                "alpha\nbeta"
        );

        assertThat(result.matches()).isTrue();
    }

    @Test
    void normalizedTextRejectsSemanticTextDifferences() {
        OutputComparator.ComparisonResult result = compare(
                ComparePolicy.NORMALIZED_TEXT,
                "alpha\nbeta",
                "alpha\ngamma"
        );

        assertThat(result.matches()).isFalse();
    }

    @Test
    void tokenNormalizedIgnoresWhitespaceBetweenTokens() {
        OutputComparator.ComparisonResult result = compare(
                ComparePolicy.TOKEN_NORMALIZED,
                "1 2\n3",
                "1\t2   3"
        );

        assertThat(result.matches()).isTrue();
    }

    @Test
    void tokenNormalizedRejectsTokenMismatchAndCountMismatch() {
        assertThat(compare(ComparePolicy.TOKEN_NORMALIZED, "1 2 3", "1 2 4").matches()).isFalse();
        assertThat(compare(ComparePolicy.TOKEN_NORMALIZED, "1 2 3", "1 2").matches()).isFalse();
    }

    @Test
    void floatToleranceAcceptsAbsoluteAndRelativeMatches() {
        assertThat(comparator.compare(ComparePolicy.FLOAT_TOLERANCE, "1.000", "1.004", 0.01, null).matches())
                .isTrue();
        assertThat(comparator.compare(ComparePolicy.FLOAT_TOLERANCE, "1000", "1005", null, 0.01).matches())
                .isTrue();
    }

    @Test
    void floatToleranceRejectsOutsideToleranceAndBadTokens() {
        assertThat(comparator.compare(ComparePolicy.FLOAT_TOLERANCE, "1.000", "1.02", 0.01, null).matches())
                .isFalse();
        assertThat(comparator.compare(ComparePolicy.FLOAT_TOLERANCE, "1.000 two", "1.000 2", 0.01, null).matches())
                .isFalse();
        assertThat(comparator.compare(ComparePolicy.FLOAT_TOLERANCE, "NaN", "NaN", 0.01, null).matches())
                .isFalse();
        assertThat(comparator.compare(ComparePolicy.FLOAT_TOLERANCE, "Infinity", "Infinity", 0.01, null).matches())
                .isFalse();
    }

    private OutputComparator.ComparisonResult compare(ComparePolicy policy, String expected, String actual) {
        return comparator.compare(policy, expected, actual, null, null);
    }
}

