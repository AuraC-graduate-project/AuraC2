package com.server.contestControl.submissionServer.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictTest {

    @Test
    void mapsTerminalJudge0StatusesToInternalVerdicts() {
        assertThat(Verdict.fromJudge0Status(3)).isEqualTo(Verdict.ACCEPTED);
        assertThat(Verdict.fromJudge0Status(4)).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(Verdict.fromJudge0Status(5)).isEqualTo(Verdict.TLE);
        assertThat(Verdict.fromJudge0Status(6)).isEqualTo(Verdict.COMPILATION_ERROR);
        assertThat(Verdict.fromJudge0Status(7)).isEqualTo(Verdict.RUNTIME_ERROR);
        assertThat(Verdict.fromJudge0Status(12)).isEqualTo(Verdict.RUNTIME_ERROR);
        assertThat(Verdict.fromJudge0Status(13)).isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(Verdict.fromJudge0Status(14)).isEqualTo(Verdict.INTERNAL_ERROR);
    }

    @Test
    void mapsNonTerminalAndUnknownJudge0StatusesSafely() {
        assertThat(Verdict.fromJudge0Status(1)).isEqualTo(Verdict.PENDING);
        assertThat(Verdict.fromJudge0Status(2)).isEqualTo(Verdict.PENDING);
        assertThat(Verdict.fromJudge0Status(999)).isEqualTo(Verdict.INTERNAL_ERROR);
    }
}
