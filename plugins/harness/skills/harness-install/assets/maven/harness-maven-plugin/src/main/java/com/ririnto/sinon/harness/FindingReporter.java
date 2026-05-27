package com.ririnto.sinon.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Renders findings in structured diagnostic format.
 * Handles both full-featured findings (with location/fix metadata) and legacy findings
 * (severity/category/message only).
 */
public final class FindingReporter {
    private FindingReporter() {
    }

    /**
     * Renders a list of findings in structured diagnostic format.
     *
     * @param root     project root path used to resolve file paths for snippet rendering
     * @param findings findings to render, may include mixed full and legacy shapes
     * @return list of formatted output lines, ready for logging; "OK" if findings list is empty
     * @throws IOException if file content cannot be read
     */
    public static List<String> renderFindings(Path root, List<Finding> findings) throws IOException {
        return findings.isEmpty() ? List.of("OK") : renderFindingsAndSummary(root, findings);
    }

    private static List<String> renderFindingsAndSummary(Path root, List<Finding> findings) {
        final List<String> body = findings.stream()
                .flatMap(finding -> renderFindingSafely(root, finding).stream())
                .toList();
        final int distinctFiles = (int) findings.stream()
                .map(Finding::file)
                .filter(f -> f != null)
                .distinct()
                .count();
        final long errorCount = findings.stream()
                .filter(f -> "ERROR".equals(f.severity()))
                .count();
        final long warnCount = findings.stream()
                .filter(f -> "WARN".equals(f.severity()))
                .count();
        final long infoCount = findings.stream()
                .filter(f -> "INFO".equals(f.severity()))
                .count();
        final long fixableCount = findings.stream()
                .filter(f -> f.fix() != null && f.fix().safety() == FixSafety.SAFE)
                .count();
        final String fixableLabel = 0 < fixableCount ? String.format(" [*] %d fixable.", fixableCount) : "";
        return Stream.concat(
                body.stream(),
                Stream.of(
                        "",
                        String.format(
                                "Checked %d file(s). %d violation(s): %d error, %d warn, %d info.%s",
                                distinctFiles,
                                findings.size(),
                                errorCount,
                                warnCount,
                                infoCount,
                                fixableLabel)))
                .toList();
    }

    private static List<String> renderFindingSafely(Path root, Finding finding) {
        try {
            return renderFinding(root, finding);
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Renders a single finding, which may span multiple output lines.
     *
     * @param root    project root path
     * @param finding finding to render
     * @return list of lines for this finding
     * @throws IOException if file cannot be read
     */
    private static List<String> renderFinding(Path root, Finding finding) throws IOException {
        return Stream.of(
                Stream.of(renderHeaderLine(finding)),
                finding.file() != null && finding.startLine() != null
                        ? renderSnippet(root, finding).stream()
                        : Stream.<String>empty(),
                renderFixSection(root, finding),
                Stream.of(""))
                .flatMap(s -> s)
                .toList();
    }

    private static Stream<String> renderFixSection(Path root, Finding finding) {
        return finding.fix() == null ? Stream.empty() : buildFixSection(root, finding);
    }

    private static Stream<String> buildFixSection(Path root, Finding finding) {
        final Stream.Builder<String> builder = Stream.builder();
        builder.add("");
        builder.add(String.format("  Safety: %s", safetyLabel(finding.fix().safety())));
        builder.add(String.format("  Help: %s", finding.fix().description()));
        if (finding.fix().edits() != null && !finding.fix().edits().isEmpty()) {
            final FindingEdit edit = finding.fix().edits().get(0);
            builder.add("");
            builder.add("  Before:");
            extractRemovedTextSafely(root, edit).forEach(line -> builder.add("  - " + line));
            builder.add("  After:");
            for (final String line : edit.replacement().split("\n", -1)) {
                builder.add("  + " + line);
            }
        }
        return builder.build();
    }

    private static List<String> extractRemovedTextSafely(Path root, FindingEdit edit) {
        try {
            return extractRemovedText(root, edit);
        } catch (IOException e) {
            return List.of("[unreadable]");
        }
    }

    /**
     * Renders the header line (file location, severity, rule, message).
     *
     * @param finding finding to format
     * @return single header line
     */
    private static String renderHeaderLine(Finding finding) {
        return finding.file() == null
                ? String.format("[%s] %s: %s",
                        finding.severity(),
                        finding.category(),
                        finding.message())
                : finding.startLine() == null
                        ? String.format("%s [%s] %s: %s",
                                finding.file(),
                                finding.severity(),
                                finding.category(),
                                finding.message())
                        : String.format("%s:%d:%d [%s] %s: %s",
                                finding.file(),
                                finding.startLine(),
                                finding.startColumn(),
                                finding.severity(),
                                finding.category(),
                                finding.message());
    }

    /**
     * Renders the snippet section showing one line of context above and below the offending line.
     *
     * @param root    project root path
     * @param finding finding with file and startLine
     * @return list of snippet lines including a leading blank separator
     * @throws IOException if file cannot be read
     */
    private static List<String> renderSnippet(Path root, Finding finding) throws IOException {
        final Path fullPath = root.resolve(finding.file());
        final List<String> fileLines = Files.isRegularFile(fullPath) ? readAllLines(fullPath) : List.of();
        final int offendingLineIdx = finding.startLine() - 1;
        return fileLines.isEmpty() || offendingLineIdx < 0 || fileLines.size() <= offendingLineIdx
                ? List.of()
                : buildSnippetLines(fileLines, offendingLineIdx);
    }

    private static List<String> buildSnippetLines(List<String> fileLines, int offendingLineIdx) {
        final int beforeIdx = offendingLineIdx - 1;
        final int afterIdx = offendingLineIdx + 1;
        final int numWidth = String.valueOf(fileLines.size()).length();
        return Stream.of(
                Stream.of(""),
                0 <= beforeIdx
                        ? Stream.of(String.format("   %s │ %s",
                                padLineNum(beforeIdx + 1, numWidth),
                                fileLines.get(beforeIdx)))
                        : Stream.<String>empty(),
                Stream.of(String.format("  > %s │ %s",
                        padLineNum(offendingLineIdx + 1, numWidth),
                        fileLines.get(offendingLineIdx))),
                afterIdx < fileLines.size()
                        ? Stream.of(String.format("   %s │ %s",
                                padLineNum(afterIdx + 1, numWidth),
                                fileLines.get(afterIdx)))
                        : Stream.<String>empty())
                .flatMap(s -> s)
                .toList();
    }

    /**
     * Extracts the original text at the edit location from the source file.
     *
     * @param root project root path
     * @param edit edit operation with location coordinates
     * @return original text spanning the edit range, one entry per source line
     * @throws IOException if file cannot be read
     */
    private static List<String> extractRemovedText(Path root, FindingEdit edit) throws IOException {
        final Path fullPath = root.resolve(edit.file());
        final List<String> fileLines = Files.isRegularFile(fullPath) ? readAllLines(fullPath) : List.of();
        final int startIdx = edit.startLine() - 1;
        final int endIdx = edit.endLine() - 1;
        return fileLines.isEmpty() || startIdx < 0 || fileLines.size() <= endIdx
                ? List.of()
                : IntStream.rangeClosed(startIdx, endIdx)
                        .mapToObj(i -> extractRemovedLine(edit, fileLines.get(i), i, startIdx, endIdx))
                        .toList();
    }

    private static String extractRemovedLine(FindingEdit edit, String line, int i, int startIdx, int endIdx) {
        return i == startIdx && i == endIdx
                ? line.substring(
                        Math.min(edit.startColumn() - 1, line.length()),
                        Math.min(edit.endColumn(), line.length()))
                : i == startIdx
                        ? line.substring(Math.min(edit.startColumn() - 1, line.length()))
                        : i == endIdx
                                ? line.substring(0, Math.min(edit.endColumn(), line.length()))
                                : line;
    }

    /**
     * Reads all lines from a UTF-8 file.
     *
     * @param path file path to read
     * @return list of lines
     * @throws IOException if file cannot be read
     */
    private static List<String> readAllLines(Path path) throws IOException {
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    /**
     * Right-aligns a line number to the given width.
     *
     * @param lineNum 1-indexed line number
     * @param width   target width for right alignment
     * @return right-aligned numeric string
     */
    private static String padLineNum(int lineNum, int width) {
        return String.format("%" + width + "d", lineNum);
    }

    /**
     * Converts FixSafety enum to human-readable label.
     *
     * @param safety fix safety enum value
     * @return lowercase label
     */
    private static String safetyLabel(FixSafety safety) {
        return switch (safety) {
            case SAFE -> "safe";
            case UNSAFE -> "unsafe";
            case MANUAL -> "manual";
        };
    }
}
