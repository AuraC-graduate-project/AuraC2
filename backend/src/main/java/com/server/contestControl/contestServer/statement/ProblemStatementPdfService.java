package com.server.contestControl.contestServer.statement;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.entity.TestCase;
import com.server.contestControl.contestServer.exceptions.ContestNotFoundException;
import com.server.contestControl.contestServer.exceptions.ProblemNotFoundException;
import com.server.contestControl.contestServer.repository.ContestRepository;
import com.server.contestControl.contestServer.repository.ProblemRepository;
import com.server.contestControl.contestServer.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProblemStatementPdfService {

    private final ProblemRepository problemRepository;
    private final ContestRepository contestRepository;
    private final TestCaseRepository testCaseRepository;
    private final StatementPdfRenderer statementPdfRenderer;

    @Transactional(readOnly = true)
    public PdfExport problemPdf(Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));
        List<TestCase> publicSamples = testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(problemId);
        ContestantSafeStatementModel model = toContestantSafeModel(problem, publicSamples);
        return new PdfExport(
                safeFileName(problem.getTitle(), "problem-" + problemId) + "-statement.pdf",
                statementPdfRenderer.renderProblem(model)
        );
    }

    @Transactional(readOnly = true)
    public PdfExport contestBookletPdf(Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ContestNotFoundException(contestId));
        List<Problem> problems = problemRepository.findByContest_IdOrderByIdAsc(contestId);
        List<ContestantSafeStatementModel> models = problems.stream()
                .map(problem -> toContestantSafeModel(
                        problem,
                        testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(problem.getId())
                ))
                .toList();

        String title = contest.getTitle() == null ? "Contest booklet" : contest.getTitle() + " Problem Booklet";
        return new PdfExport(
                safeFileName(contest.getTitle(), "contest-" + contestId) + "-problem-booklet.pdf",
                statementPdfRenderer.renderBooklet(title, models)
        );
    }

    private ContestantSafeStatementModel toContestantSafeModel(Problem problem, List<TestCase> publicSamples) {
        return new ContestantSafeStatementModel(
                plainText(problem.getTitle()),
                problem.getTimeLimit(),
                problem.getMemoryLimit(),
                plainText(effectiveStatement(problem)),
                plainText(problem.getInputFormat()),
                plainText(problem.getOutputFormat()),
                plainText(problem.getConstraintsText()),
                plainText(problem.getPublicNotes()),
                publicSamples.stream()
                        .map(sample -> new ContestantSafeStatementModel.Sample(
                                normalizeSampleText(sample.getInputData()),
                                normalizeSampleText(sample.getExpectedOutput())
                        ))
                        .toList()
        );
    }

    private String effectiveStatement(Problem problem) {
        return hasText(problem.getStatement()) ? problem.getStatement() : problem.getDescription();
    }

    private String normalizeSampleText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String plainText(String value) {
        if (value == null) {
            return "";
        }
        String text = value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</h[1-6]>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)<tr[^>]*>", "\n")
                .replaceAll("(?i)</tr>", "\n")
                .replaceAll("(?i)</t[dh]>", " ")
                .replaceAll("(?i)<li[^>]*>", "- ")
                .replaceAll("(?s)<[^>]+>", "");
        text = decodeEntities(text);
        return text.replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String decodeEntities(String text) {
        String named = text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&le;", "<=")
                .replace("&ge;", ">=")
                .replace("&minus;", "-")
                .replace("&times;", "x");
        return decodeNumericEntity(decodeNumericEntity(named, "&#x([0-9a-fA-F]+);", 16), "&#([0-9]+);", 10);
    }

    private static String decodeNumericEntity(String text, String regex, int radix) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        StringBuilder decoded = new StringBuilder();
        while (matcher.find()) {
            String replacement = matcher.group(0);
            try {
                int codePoint = Integer.parseInt(matcher.group(1), radix);
                replacement = new String(Character.toChars(codePoint));
            } catch (IllegalArgumentException ignored) {
                // Keep malformed entities as plain text; PDF generation should stay best-effort.
            }
            matcher.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(decoded);
        return decoded.toString();
    }

    private String safeFileName(String value, String fallback) {
        String source = hasText(value) ? value : fallback;
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    public record PdfExport(String filename, byte[] bytes) {
    }
}
