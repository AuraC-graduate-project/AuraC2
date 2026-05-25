package com.server.contestControl.contestServer.statement;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.enums.Difficulty;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProblemStatementPdfServiceTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ContestRepository contestRepository = mock(ContestRepository.class);
    private final TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
    private final ProblemStatementPdfService service = new ProblemStatementPdfService(
            problemRepository,
            contestRepository,
            testCaseRepository
    );

    @Test
    void problemPdfIncludesOnlyContestantSafeStatementAndPublicSamples() {
        Problem problem = problem();
        problem.setAdminNotes("secret setter note");
        problem.setValidatorSource("checker secret");
        TestCase publicSample = TestCase.builder()
                .id(1L)
                .problem(problem)
                .inputData("1 2\n")
                .expectedOutput("3\n")
                .isPublic(true)
                .build();

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(10L)).thenReturn(List.of(publicSample));

        ProblemStatementPdfService.PdfExport export = service.problemPdf(10L);
        String pdf = new String(export.bytes(), StandardCharsets.ISO_8859_1);

        assertThat(export.filename()).isEqualTo("sum-statement.pdf");
        assertThat(pdf).startsWith("%PDF-1.4");
        assertThat(pdf).contains("Sum", "Read two numbers", "Input Format", "1 2", "3");
        assertThat(pdf).doesNotContain("secret setter note", "checker secret", "hidden");
    }

    @Test
    void contestBookletUsesContestOrderAndPublicSamplesOnly() {
        Contest contest = Contest.builder()
                .id(7L)
                .title("Practice Round")
                .startTime(Instant.now())
                .durationMinutes(120)
                .build();
        Problem first = problem();
        Problem second = problem();
        second.setId(11L);
        second.setTitle("Max");
        second.setDescription("Find maximum");

        when(contestRepository.findById(7L)).thenReturn(Optional.of(contest));
        when(problemRepository.findByContest_IdOrderByIdAsc(7L)).thenReturn(List.of(first, second));
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(10L)).thenReturn(List.of());
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(11L)).thenReturn(List.of());

        ProblemStatementPdfService.PdfExport export = service.contestBookletPdf(7L);
        String pdf = new String(export.bytes(), StandardCharsets.ISO_8859_1);

        assertThat(export.filename()).isEqualTo("practice-round-problem-booklet.pdf");
        assertThat(pdf).contains("Practice Round Problem Booklet", "Sum", "Max");
    }

    private Problem problem() {
        Contest contest = Contest.builder().id(7L).title("Practice Round").build();
        return Problem.builder()
                .id(10L)
                .contest(contest)
                .title("Sum")
                .description("<p>Read two numbers.</p>")
                .statement("<p>Read two numbers.</p>")
                .inputFormat("<p>Two integers a and b.</p>")
                .outputFormat("<p>Their sum.</p>")
                .constraintsText("<p>0 <= a,b <= 100</p>")
                .publicNotes("<p>Use standard output.</p>")
                .timeLimit(1000)
                .memoryLimit(128)
                .difficulty(Difficulty.EASY)
                .build();
    }
}
