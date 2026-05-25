package com.server.contestControl.contestServer.statement;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class StatementPdfRenderer {

    public byte[] renderProblem(ContestantSafeStatementModel problem) {
        return render(null, List.of(problem));
    }

    public byte[] renderBooklet(String title, List<ContestantSafeStatementModel> problems) {
        return render(title, problems);
    }

    private byte[] render(String bookletTitle, List<ContestantSafeStatementModel> problems) {
        SimplePdf pdf = new SimplePdf();
        boolean hasBookletTitle = hasText(bookletTitle);
        if (hasBookletTitle) {
            pdf.addTitle(bookletTitle);
            pdf.addCenteredMuted("Contestant-safe problem booklet");
            pdf.addParagraph("Hidden tests, admin notes, source code, and private diagnostics are excluded from this export.");
        }

        if (problems.isEmpty()) {
            pdf.addHeading("No problems");
            pdf.addParagraph("This contest does not currently contain any problems.");
            return pdf.toBytes();
        }

        for (int i = 0; i < problems.size(); i++) {
            if (hasBookletTitle || i > 0) {
                pdf.newPage();
            }
            addProblem(pdf, problems.get(i));
        }

        return pdf.toBytes();
    }

    private void addProblem(SimplePdf pdf, ContestantSafeStatementModel problem) {
        pdf.addTitle(problem.title());
        pdf.addLimitLine("time limit per test: " + problem.timeLimit() + " milliseconds");
        pdf.addLimitLine("memory limit per test: " + problem.memoryLimit() + " megabytes");
        pdf.addDivider();

        addSection(pdf, "Statement", problem.statement());
        addSection(pdf, "Input", problem.inputFormat());
        addSection(pdf, "Output", problem.outputFormat());
        addSection(pdf, "Constraints", problem.constraintsText());
        addSamples(pdf, problem.publicSamples());
        addSection(pdf, "Note", problem.publicNotes());
    }

    private void addSection(SimplePdf pdf, String heading, String value) {
        if (!hasText(value)) {
            return;
        }
        pdf.addHeading(heading);
        pdf.addParagraph(value);
    }

    private void addSamples(SimplePdf pdf, List<ContestantSafeStatementModel.Sample> publicSamples) {
        if (publicSamples == null || publicSamples.isEmpty()) {
            return;
        }
        boolean multipleSamples = publicSamples.size() > 1;
        ContestantSafeStatementModel.Sample firstSample = publicSamples.getFirst();
        pdf.ensureExamplesSectionStart(firstSample.input(), firstSample.output(), multipleSamples);
        pdf.addHeading("Examples");
        int sampleNumber = 1;
        for (ContestantSafeStatementModel.Sample sample : publicSamples) {
            pdf.ensureSampleSpace(sample.input(), sample.output(), multipleSamples);
            if (multipleSamples) {
                pdf.addSubheading("Example " + sampleNumber);
            }
            pdf.addIoBlock("Input", sample.input());
            pdf.addIoBlock("Output", sample.output());
            pdf.addSampleGap();
            sampleNumber++;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class SimplePdf {
        private static final int PAGE_WIDTH = 595;
        private static final int PAGE_HEIGHT = 842;
        private static final int MARGIN_LEFT = 57;
        private static final int MARGIN_TOP = 51;
        private static final int MARGIN_BOTTOM = 51;
        private static final int TEXT_WIDTH = PAGE_WIDTH - (MARGIN_LEFT * 2);
        private static final int SAMPLE_BLOCK_WIDTH = Math.min(TEXT_WIDTH, 418);
        private static final int CODE_PADDING_X = 9;
        private static final int CODE_PADDING_TOP = 8;
        private static final int CODE_LINE_HEIGHT = 12;
        private static final int IO_HEADER_HEIGHT = 19;

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

        private void addIoBlock(String label, String value) {
            List<String> lines = codeLines(value);
            int blockHeight = ioBlockHeight(lines);
            ensureSpace(blockHeight + 11);

            int top = y;
            int bottom = y - blockHeight;

            drawFilledRect(MARGIN_LEFT, bottom, SAMPLE_BLOCK_WIDTH, blockHeight, 0.975, 0.980, 0.988);
            drawFilledRect(MARGIN_LEFT, top - IO_HEADER_HEIGHT, SAMPLE_BLOCK_WIDTH, IO_HEADER_HEIGHT, 0.925, 0.945, 0.970);
            drawRect(MARGIN_LEFT, bottom, SAMPLE_BLOCK_WIDTH, blockHeight, 0.72, 0.78, 0.86);
            drawLine(MARGIN_LEFT, top - IO_HEADER_HEIGHT, MARGIN_LEFT + SAMPLE_BLOCK_WIDTH, top - IO_HEADER_HEIGHT, 0.72, 0.78, 0.86);

            drawText(MARGIN_LEFT + CODE_PADDING_X, top - 13, label, 10, "F2");
            int textY = top - IO_HEADER_HEIGHT - CODE_PADDING_TOP - 9;
            for (String line : lines) {
                drawText(MARGIN_LEFT + CODE_PADDING_X, textY, line.isEmpty() ? " " : line, 9, "F3");
                textY -= CODE_LINE_HEIGHT;
            }
            y = bottom - 11;
        }

        private void addSampleGap() {
            y -= 3;
        }

        private void ensureSampleSpace(String input, String output, boolean includeExampleTitle) {
            int sampleTitleHeight = includeExampleTitle ? 20 : 0;
            int inputHeight = ioBlockHeight(codeLines(input)) + 11;
            int outputHeight = ioBlockHeight(codeLines(output)) + 11;
            ensureSpace(sampleTitleHeight + inputHeight + outputHeight + 6);
        }

        private void ensureExamplesSectionStart(String input, String output, boolean includeExampleTitle) {
            ensureSpace(34
                    + (includeExampleTitle ? 20 : 0)
                    + ioBlockHeight(codeLines(input))
                    + ioBlockHeight(codeLines(output))
                    + 28);
        }

        private List<String> codeLines(String value) {
            return wrapCode(safeText(value), Math.max(24, (int) ((SAMPLE_BLOCK_WIDTH - (CODE_PADDING_X * 2)) / (9 * 0.56))));
        }

        private int ioBlockHeight(List<String> lines) {
            int contentHeight = CODE_PADDING_TOP + Math.max(1, lines.size()) * CODE_LINE_HEIGHT + CODE_PADDING_TOP - 2;
            return Math.max(42, IO_HEADER_HEIGHT + contentHeight);
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
