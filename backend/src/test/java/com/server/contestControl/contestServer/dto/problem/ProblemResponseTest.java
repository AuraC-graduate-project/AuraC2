package com.server.contestControl.contestServer.dto.problem;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.enums.ValidationMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemResponseTest {

    @Test
    void structuredStatementFallsBackToLegacyDescription() {
        Problem problem = baseProblem()
                .statement(null)
                .description("Legacy body")
                .build();

        ProblemResponse response = ProblemResponse.from(problem, 0, false);

        assertThat(response.getDescription()).isEqualTo("Legacy body");
        assertThat(response.getStatement()).isEqualTo("Legacy body");
    }

    @Test
    void adminNotesAreOnlyIncludedWhenRequested() {
        Problem problem = baseProblem()
                .statement("Structured body")
                .adminNotes("Hidden trap cases")
                .build();

        assertThat(ProblemResponse.from(problem, 0, false).getAdminNotes()).isNull();
        assertThat(ProblemResponse.from(problem, 0, true).getAdminNotes()).isEqualTo("Hidden trap cases");
    }

    private Problem.ProblemBuilder baseProblem() {
        return Problem.builder()
                .id(10L)
                .contest(Contest.builder().id(1L).build())
                .title("A + B")
                .description("Add two integers")
                .timeLimit(1000)
                .memoryLimit(128)
                .difficulty(Difficulty.EASY)
                .comparePolicy(ComparePolicy.EXACT)
                .validationMode(ValidationMode.BUILTIN_COMPARE_POLICY)
                .validatorEnabled(false);
    }
}
