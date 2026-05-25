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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            pdf.addCenteredMuted("Contestant-safe problem booklet");
            pdf.addParagraph("Hidden tests, admin notes, source code, and private diagnostics are excluded from this export.");
        }

        if (problems.isEmpty()) {
            pdf.addHeading("No problems");
            pdf.addParagraph("This contest does not currently contain any problems.");
            return pdf.toBytes();
        }

        for (int i = 0; i < problems.size(); i++) {
            if (problems.size() != 1 || i > 0) {
                pdf.newPage();
            }
            addProblem(pdf, problems.get(i), sampleSets.get(i).samples());
        }

        return pdf.toBytes();
    }

    private void addProblem(SimplePdf pdf, Problem problem, List<TestCase> publicSamples) {
        pdf.addTitle(problem.getTitle());
        pdf.addLimitLine("time limit per test: " + problem.getTimeLimit() + " milliseconds");
        pdf.addLimitLine("memory limit per test: " + problem.getMemoryLimit() + " megabytes");
        pdf.addDivider();

        addSection(pdf, "Statement", effectiveStatement(problem));
        addSection(pdf, "Input", problem.getInputFormat());
        addSection(pdf, "Output", problem.getOutputFormat());
        addSection(pdf, "Constraints", problem.getConstraintsText());
        addSamples(pdf, publicSamples);
        addSection(pdf, "Note", problem.getPublicNotes());
    }

    private void addSamples(SimplePdf pdf, List<TestCase> publicSamples) {
        if (publicSamples.isEmpty()) {
            return;
        }
        boolean multipleSamples = publicSamples.size() > 1;
        TestCase firstSample = publicSamples.getFirst();
        pdf.ensureExamplesSectionStart(firstSample.getInputData(), firstSample.getExpectedOutput(), multipleSamples);
        pdf.addHeading("Examples");
        int sampleNumber = 1;
        for (TestCase sample : publicSamples) {
            pdf.ensureSamplePairSpace(sample.getInputData(), sample.getExpectedOutput(), multipleSamples);
            if (multipleSamples) {
                pdf.addSubheading("Example " + sampleNumber);
            }
            pdf.addCodeBox("Input", sample.getInputData());
            pdf.addCodeBox("Output", sample.getExpectedOutput());
            sampleNumber++;
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

    private record ProblemSamples(Long problemId, List<TestCase> samples) {
    }

    private static final class SimplePdf {
        private static final int PAGE_WIDTH = 595;
        private static final int PAGE_HEIGHT = 842;
        private static final int MARGIN_LEFT = 57;
        private static final int MARGIN_TOP = 51;
        private static final int MARGIN_BOTTOM = 51;
        private static final int TEXT_WIDTH = PAGE_WIDTH - (MARGIN_LEFT * 2);
        private static final int SAMPLE_BOX_WIDTH = Math.min(TEXT_WIDTH, 430);
        private static final int CODE_PADDING = 10;

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
            addWrappedCentered(value, 20, true, 25, 6);
        }

        private void addCenteredMuted(String value) {
            addWrappedCentered(value, 10, false, 14, 18);
        }

        private void addLimitLine(String value) {
            addWrappedCentered(value, 10, false, 14, 0);
        }

        private void addDivider() {
            ensureSpace(18);
            y -= 10;
            drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_LEFT, y, 0.80, 0.84, 0.90);
            y -= 18;
        }

        private void addHeading(String value) {
            y -= 7;
            addWrapped(value, 14, true, 18, 8);
        }

        private void addSubheading(String value) {
            addWrapped(value, 11, true, 15, 5);
        }

        private void addParagraph(String value) {
            for (String block : safeText(value).split("\\n\\s*\\n")) {
                addWrapped(block, 10, false, 15, 8);
            }
        }

        private void addCodeBox(String label, String value) {
            addSampleLabel(label);
            List<String> lines = codeLines(value);
            int boxHeight = codeBoxHeight(lines);
            ensureSpace(boxHeight + 8);

            int boxTop = y;
            int boxBottom = y - boxHeight;
            drawFilledRect(MARGIN_LEFT, boxBottom, SAMPLE_BOX_WIDTH, boxHeight, 0.965, 0.972, 0.982);
            drawRect(MARGIN_LEFT, boxBottom, SAMPLE_BOX_WIDTH, boxHeight, 0.78, 0.82, 0.88);

            int textY = boxTop - CODE_PADDING - 9;
            for (String line : lines) {
                drawText(MARGIN_LEFT + CODE_PADDING, textY, line.isEmpty() ? " " : line, 9, "F3");
                textY -= 12;
            }
            y = boxBottom - 8;
        }

        private void addSampleLabel(String value) {
            addWrapped(value, 10, true, 13, 3);
        }

        private void ensureSamplePairSpace(String input, String output, boolean includeExampleTitle) {
            int sampleTitleHeight = includeExampleTitle ? 20 : 0;
            int labelHeights = 32;
            int inputHeight = codeBoxHeight(codeLines(input)) + 8;
            int outputHeight = codeBoxHeight(codeLines(output)) + 8;
            ensureSpace(sampleTitleHeight + labelHeights + inputHeight + outputHeight);
        }

        private void ensureExamplesSectionStart(String input, String output, boolean includeExampleTitle) {
            ensureSpace(34
                    + (includeExampleTitle ? 20 : 0)
                    + 32
                    + codeBoxHeight(codeLines(input))
                    + codeBoxHeight(codeLines(output))
                    + 16);
        }

        private List<String> codeLines(String value) {
            return wrapCode(safeText(value), Math.max(24, (int) ((SAMPLE_BOX_WIDTH - (CODE_PADDING * 2)) / (9 * 0.56))));
        }

        private int codeBoxHeight(List<String> lines) {
            return Math.max(28, CODE_PADDING + lines.size() * 12 + CODE_PADDING - 2);
        }

        private void addWrapped(String value, int fontSize, boolean bold, int lineHeight, int after) {
            List<String> lines = wrap(safeText(value), Math.max(24, (int) (TEXT_WIDTH / (fontSize * 0.52))));
            for (String line : lines) {
                ensureSpace(lineHeight);
                drawText(MARGIN_LEFT, y, line, fontSize, bold ? "F2" : "F1");
                y -= lineHeight;
            }
            y -= after;
        }

        private void addWrappedCentered(String value, int fontSize, boolean bold, int lineHeight, int after) {
            List<String> lines = wrap(safeText(value), Math.max(24, (int) (TEXT_WIDTH / (fontSize * 0.50))));
            for (String line : lines) {
                ensureSpace(lineHeight);
                int textWidth = estimateTextWidth(line, fontSize);
                int x = Math.max(MARGIN_LEFT, (PAGE_WIDTH - textWidth) / 2);
                drawText(x, y, line, fontSize, bold ? "F2" : "F1");
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

        private List<String> wrapCode(String text, int maxChars) {
            List<String> wrapped = new ArrayList<>();
            String[] lines = text.split("\\n", -1);
            for (String rawLine : lines) {
                String line = rawLine.stripTrailing();
                if (line.isEmpty()) {
                    wrapped.add("");
                    continue;
                }
                while (line.length() > maxChars) {
                    wrapped.add(line.substring(0, maxChars));
                    line = line.substring(maxChars);
                }
                wrapped.add(line);
            }
            return wrapped.isEmpty() ? List.of("") : wrapped;
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

        private void drawText(int x, int y, String line, int fontSize, String fontName) {
            current.append("0 0 0 rg BT /")
                    .append(fontName)
                    .append(' ')
                    .append(fontSize)
                    .append(" Tf ")
                    .append(x)
                    .append(' ')
                    .append(y)
                    .append(" Td (")
                    .append(escape(line))
                    .append(") Tj ET\n");
        }

        private void drawLine(int x1, int y1, int x2, int y2, double r, double g, double b) {
            current.append(formatColor(r, g, b, "RG"))
                    .append(" 0.7 w ")
                    .append(x1).append(' ').append(y1).append(" m ")
                    .append(x2).append(' ').append(y2).append(" l S\n");
        }

        private void drawRect(int x, int y, int width, int height, double r, double g, double b) {
            current.append(formatColor(r, g, b, "RG"))
                    .append(" 0.8 w ")
                    .append(x).append(' ').append(y).append(' ')
                    .append(width).append(' ').append(height)
                    .append(" re S\n");
        }

        private void drawFilledRect(int x, int y, int width, int height, double r, double g, double b) {
            current.append(formatColor(r, g, b, "rg"))
                    .append(' ')
                    .append(x).append(' ').append(y).append(' ')
                    .append(width).append(' ').append(height)
                    .append(" re f\n");
        }

        private String formatColor(double r, double g, double b, String operator) {
            return String.format(Locale.ROOT, "%.3f %.3f %.3f %s", r, g, b, operator);
        }

        private int estimateTextWidth(String line, int fontSize) {
            return (int) Math.round(line.length() * fontSize * 0.50);
        }

        private byte[] toBytes() {
            List<byte[]> objects = new ArrayList<>();
            objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));

            int firstPageObject = 6;
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
                kids.append(firstPageObject + (i * 2)).append(" 0 R ");
            }
            objects.add(bytes("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>"));
            objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));
            objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>"));
            objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>"));

            for (int i = 0; i < pages.size(); i++) {
                int pageObjectNumber = firstPageObject + (i * 2);
                int contentObjectNumber = pageObjectNumber + 1;
                byte[] streamBytes = pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
                objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_WIDTH + " " + PAGE_HEIGHT + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> /Contents "
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
