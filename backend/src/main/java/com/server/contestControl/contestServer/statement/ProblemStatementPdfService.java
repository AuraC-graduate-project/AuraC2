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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProblemStatementPdfService {

    private final ProblemRepository problemRepository;
    private final ContestRepository contestRepository;
    private final TestCaseRepository testCaseRepository;

    @Transactional(readOnly = true)
    public PdfExport problemPdf(Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));
        List<TestCase> publicSamples = testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(problemId);
        return new PdfExport(
                safeFileName(problem.getTitle(), "problem-" + problemId) + "-statement.pdf",
                render(List.of(problem), publicSamplesByProblem(List.of(problem), List.of(publicSamples)), problem.getTitle())
        );
    }

    @Transactional(readOnly = true)
    public PdfExport contestBookletPdf(Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ContestNotFoundException(contestId));
        List<Problem> problems = problemRepository.findByContest_IdOrderByIdAsc(contestId);
        List<List<TestCase>> samples = problems.stream()
                .map(problem -> testCaseRepository.findByProblemIdAndIsPublicTrueOrderByIdAsc(problem.getId()))
                .toList();

        String title = contest.getTitle() == null ? "Contest booklet" : contest.getTitle() + " Problem Booklet";
        return new PdfExport(
                safeFileName(contest.getTitle(), "contest-" + contestId) + "-problem-booklet.pdf",
                render(problems, publicSamplesByProblem(problems, samples), title)
        );
    }

    private byte[] render(List<Problem> problems, List<ProblemSamples> sampleSets, String title) {
        SimplePdf pdf = new SimplePdf();
        if (problems.size() != 1) {
            pdf.addTitle(title);
            pdf.addParagraph("Contestant-safe export. Hidden tests, admin notes, and problem-engineering source are excluded.");
        }

        if (problems.isEmpty()) {
            pdf.addHeading("No problems");
            pdf.addParagraph("This contest does not currently contain any problems.");
            return pdf.toBytes();
        }

        for (int i = 0; i < problems.size(); i++) {
            if (i > 0) {
                pdf.newPage();
            }
            addProblem(pdf, problems.get(i), sampleSets.get(i).samples());
        }

        return pdf.toBytes();
    }

    private void addProblem(SimplePdf pdf, Problem problem, List<TestCase> publicSamples) {
        pdf.addTitle(problem.getTitle());
        pdf.addParagraph("Time limit: " + problem.getTimeLimit() + " ms");
        pdf.addParagraph("Memory limit: " + problem.getMemoryLimit() + " MB");

        addSection(pdf, "Statement", effectiveStatement(problem));
        addSection(pdf, "Input Format", problem.getInputFormat());
        addSection(pdf, "Output Format", problem.getOutputFormat());
        addSection(pdf, "Constraints", problem.getConstraintsText());
        addSection(pdf, "Notes", problem.getPublicNotes());

        pdf.addHeading("Public Samples");
        if (publicSamples.isEmpty()) {
            pdf.addParagraph("No public samples have been added.");
            return;
        }

        int sampleNumber = 1;
        for (TestCase sample : publicSamples) {
            pdf.addSubheading("Sample " + sampleNumber++);
            pdf.addCode("Input", sample.getInputData());
            pdf.addCode("Output", sample.getExpectedOutput());
        }
    }

    private void addSection(SimplePdf pdf, String heading, String value) {
        String text = plainText(value);
        if (text.isBlank()) {
            return;
        }
        pdf.addHeading(heading);
        pdf.addParagraph(text);
    }

    private String effectiveStatement(Problem problem) {
        return hasText(problem.getStatement()) ? problem.getStatement() : problem.getDescription();
    }

    private List<ProblemSamples> publicSamplesByProblem(List<Problem> problems, List<List<TestCase>> samples) {
        List<ProblemSamples> result = new ArrayList<>();
        for (int i = 0; i < problems.size(); i++) {
            result.add(new ProblemSamples(problems.get(i).getId(), samples.get(i)));
        }
        return result;
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
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)<li[^>]*>", "- ")
                .replaceAll("(?s)<[^>]+>", "");
        text = decodeEntities(text);
        return text.replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String decodeEntities(String text) {
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
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

    private record ProblemSamples(Long problemId, List<TestCase> samples) {
    }

    private static final class SimplePdf {
        private static final int PAGE_WIDTH = 612;
        private static final int PAGE_HEIGHT = 792;
        private static final int MARGIN_LEFT = 50;
        private static final int MARGIN_TOP = 54;
        private static final int MARGIN_BOTTOM = 54;
        private static final int TEXT_WIDTH = PAGE_WIDTH - (MARGIN_LEFT * 2);

        private final List<StringBuilder> pages = new ArrayList<>();
        private StringBuilder current;
        private int y;

        private SimplePdf() {
            newPage();
        }

        private void newPage() {
            current = new StringBuilder();
            pages.add(current);
            y = PAGE_HEIGHT - MARGIN_TOP;
        }

        private void addTitle(String value) {
            addWrapped(value, 17, true, 22, 14);
        }

        private void addHeading(String value) {
            addWrapped(value, 13, true, 17, 8);
        }

        private void addSubheading(String value) {
            addWrapped(value, 11, true, 15, 5);
        }

        private void addParagraph(String value) {
            for (String block : safeText(value).split("\\n\\s*\\n")) {
                addWrapped(block, 10, false, 14, 6);
            }
        }

        private void addCode(String label, String value) {
            addSubheading(label);
            for (String line : safeText(value).split("\\n", -1)) {
                addWrapped(line.isEmpty() ? " " : line, 9, false, 12, 0);
            }
            y -= 5;
        }

        private void addWrapped(String value, int fontSize, boolean bold, int lineHeight, int after) {
            List<String> lines = wrap(safeText(value), Math.max(24, (int) (TEXT_WIDTH / (fontSize * 0.52))));
            for (String line : lines) {
                ensureSpace(lineHeight);
                current.append("BT /")
                        .append(bold ? "F2" : "F1")
                        .append(' ')
                        .append(fontSize)
                        .append(" Tf ")
                        .append(MARGIN_LEFT)
                        .append(' ')
                        .append(y)
                        .append(" Td (")
                        .append(escape(line))
                        .append(") Tj ET\n");
                y -= lineHeight;
            }
            y -= after;
        }

        private void ensureSpace(int lineHeight) {
            if (y - lineHeight < MARGIN_BOTTOM) {
                newPage();
            }
        }

        private List<String> wrap(String text, int maxChars) {
            List<String> wrapped = new ArrayList<>();
            for (String rawLine : text.split("\\n", -1)) {
                String line = rawLine.stripTrailing();
                if (line.isEmpty()) {
                    wrapped.add(" ");
                    continue;
                }
                while (line.length() > maxChars) {
                    int breakAt = line.lastIndexOf(' ', maxChars);
                    if (breakAt < maxChars / 2) {
                        breakAt = maxChars;
                    }
                    wrapped.add(line.substring(0, breakAt).stripTrailing());
                    line = line.substring(breakAt).stripLeading();
                }
                wrapped.add(line);
            }
            return wrapped;
        }

        private String safeText(String value) {
            if (value == null) {
                return "";
            }
            String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                    .replaceAll("\\p{M}", "");
            return normalized.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                    .replace('\t', ' ');
        }

        private String escape(String value) {
            String ascii = value.chars()
                    .map(ch -> ch >= 32 && ch <= 126 ? ch : '?')
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
            return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }

        private byte[] toBytes() {
            List<byte[]> objects = new ArrayList<>();
            objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));

            int firstPageObject = 5;
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
                kids.append(firstPageObject + (i * 2)).append(" 0 R ");
            }
            objects.add(bytes("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>"));
            objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));
            objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>"));

            for (int i = 0; i < pages.size(); i++) {
                int pageObjectNumber = firstPageObject + (i * 2);
                int contentObjectNumber = pageObjectNumber + 1;
                byte[] streamBytes = pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
                objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                        + contentObjectNumber + " 0 R >>"));
                objects.add(bytes("<< /Length " + streamBytes.length + " >>\nstream\n"
                        + pages.get(i)
                        + "endstream"));
            }

            return writeObjects(objects);
        }

        private byte[] writeObjects(List<byte[]> objects) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            write(out, "%PDF-1.4\n");
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (int i = 0; i < objects.size(); i++) {
                offsets.add(out.size());
                write(out, (i + 1) + " 0 obj\n");
                out.writeBytes(objects.get(i));
                write(out, "\nendobj\n");
            }
            int xrefOffset = out.size();
            write(out, "xref\n0 " + (objects.size() + 1) + "\n");
            write(out, "0000000000 65535 f \n");
            for (int i = 1; i < offsets.size(); i++) {
                write(out, String.format("%010d 00000 n \n", offsets.get(i)));
            }
            write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n");
            write(out, "startxref\n" + xrefOffset + "\n%%EOF\n");
            return out.toByteArray();
        }

        private byte[] bytes(String value) {
            return value.getBytes(StandardCharsets.ISO_8859_1);
        }

        private void write(ByteArrayOutputStream out, String value) {
            out.writeBytes(bytes(value));
        }
    }
}
