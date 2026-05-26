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
            testCaseRepository,
            new StatementPdfRenderer()
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
        assertThat(pdf).contains(
                "/MediaBox [0 0 595 842]",
                "Sum",
                "time limit per test: 1000 milliseconds",
                "memory limit per test: 128 megabytes",
                "Input",
                "Output",
                "Constraints",
                "Examples",
                "Note",
                "/BaseFont /Courier",
                "1 2",
                "3"
        );
        assertThat(pdf).doesNotContain("Input Format", "Output Format", "Public Samples");
        assertThat(pdf.indexOf("Examples")).isLessThan(pdf.indexOf("Note"));
        assertThat(pdf).doesNotContain("secret setter note", "checker secret", "hidden");
    }

    @Test
    void problemPdfDoesNotFallbackStatementIntoOtherStructuredSections() {
        Problem problem = problem();
        problem.setStatement("<p>Only the statement text.</p>");
        problem.setDescription("<p>Legacy statement fallback.</p>");
        problem.setInputFormat(null);
        problem.setOutputFormat("");
        problem.setConstraintsText("   ");
        problem.setPublicNotes(null);

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(10L)).thenReturn(List.of());

        ProblemStatementPdfService.PdfExport export = service.problemPdf(10L);
        String pdf = new String(export.bytes(), StandardCharsets.ISO_8859_1);

        assertThat(pdf).contains("Statement", "Only the statement text.");
        assertThat(pdf).doesNotContain("Legacy statement fallback.", "Input", "Output", "Constraints", "Examples", "Note");
    }

    @Test
    void problemPdfUsesLegacyDescriptionOnlyForMissingStatement() {
        Problem problem = problem();
        problem.setStatement(null);
        problem.setDescription("<p>Legacy statement fallback.</p>");

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(10L)).thenReturn(List.of());

        ProblemStatementPdfService.PdfExport export = service.problemPdf(10L);
        String pdf = new String(export.bytes(), StandardCharsets.ISO_8859_1);

        assertThat(pdf).contains("Statement", "Legacy statement fallback.");
    }

    @Test
    void problemPdfPreservesPublicSampleLineBreaksAndExcludesHiddenTests() {
        Problem problem = problem();
        TestCase publicSample = TestCase.builder()
                .id(1L)
                .problem(problem)
                .inputData("2 2\n3 4\n")
                .expectedOutput("4\n7\n")
                .isPublic(true)
                .build();

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(10L)).thenReturn(List.of(publicSample));

        ProblemStatementPdfService.PdfExport export = service.problemPdf(10L);
        String pdf = new String(export.bytes(), StandardCharsets.ISO_8859_1);

        assertThat(pdf).contains("Examples", "2 2", "3 4", "4", "7");
        assertThat(pdf).doesNotContain("private", "hidden expected");
    }

    @Test
    void problemPdfGroupsMultiplePublicSamplesAsExamples() {
        Problem problem = problem();
        TestCase firstSample = TestCase.builder()
                .id(1L)
                .problem(problem)
                .inputData("2 2\n")
                .expectedOutput("4\n")
                .isPublic(true)
                .build();
        TestCase secondSample = TestCase.builder()
                .id(2L)
                .problem(problem)
                .inputData("10 5\n")
                .expectedOutput("15\n")
                .isPublic(true)
                .build();

        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(10L)).thenReturn(List.of(firstSample, secondSample));

        ProblemStatementPdfService.PdfExport export = service.problemPdf(10L);
        String pdf = new String(export.bytes(), StandardCharsets.ISO_8859_1);

        assertThat(pdf).contains("Examples", "Example 1", "Example 2", "2 2", "10 5", "15");
        assertThat(pdf.indexOf("Example 1")).isLessThan(pdf.indexOf("Example 2"));
        assertThat(pdf.indexOf("Output")).isGreaterThan(pdf.indexOf("Input"));
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
        assertThat(pdf).contains("Sum", "Max");
        assertThat(pdf).doesNotContain(
                "Practice Round Problem Booklet",
                "Contestant-safe problem booklet",
                "Hidden tests, admin notes, source code, and private diagnostics are excluded"
        );
        assertThat(pdf.indexOf("Sum")).isLessThan(pdf.indexOf("Max"));
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
