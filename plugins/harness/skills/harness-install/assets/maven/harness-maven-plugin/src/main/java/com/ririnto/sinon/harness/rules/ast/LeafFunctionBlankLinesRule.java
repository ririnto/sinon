package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.JavaToken;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rule that forbids multiple consecutive blank lines in leaf function bodies.
 */
public enum LeafFunctionBlankLinesRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "leafFunctionBlankLines";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        try {
            final int maxConsecutiveBlankLines = maxConsecutiveBlankLines(manifest);
            return ctx.stackSources(CATEGORY).stream()
                    .flatMap(file -> validateBlankLinesInLeafFunctions(root, file, severity, maxConsecutiveBlankLines).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    /**
     * Removes excess blank lines from leaf function bodies and writes changed files.
     */
    @Override
    public Collection<Path> format(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final List<Path> changed = new ArrayList<>();
        try {
            final int maxConsecutiveBlankLines = maxConsecutiveBlankLines(manifest);
            for (final Path file : ctx.stackSources(CATEGORY)) {
                if (formatBlankLinesInLeafFunctions(file, maxConsecutiveBlankLines)) {
                    changed.add(file);
                }
            }
        } catch (MojoExecutionException e) {
            throw new MojoExecutionException("failed to enumerate sources: " + e.getMessage(), e);
        }
        return changed;
    }

    private boolean formatBlankLinesInLeafFunctions(Path file, int maxConsecutiveBlankLines) throws MojoExecutionException {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            boolean fileChanged = false;
            for (final MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                if (isLeafMethod(method)) {
                    final boolean methodChanged = removeExcessBlankLinesInMethod(method, maxConsecutiveBlankLines);
                    fileChanged = fileChanged || methodChanged;
                }
            }
            if (fileChanged) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
            return fileChanged;
        } catch (IOException e) {
            throw new MojoExecutionException("failed to format " + file + ": " + e.getMessage(), e);
        }
    }

    private boolean removeExcessBlankLinesInMethod(MethodDeclaration method, int maxConsecutiveBlankLines) {
        final java.util.Optional<BlockStmt> optBody = method.getBody();
        if (optBody.isEmpty()) {
            return false;
        }
        final java.util.Optional<TokenRange> optTokenRange = optBody.get().getTokenRange();
        if (optTokenRange.isEmpty()) {
            return false;
        }
        final TokenRange tokenRange = optTokenRange.get();
        boolean methodChanged = false;
        JavaToken cur = tokenRange.getBegin();
        final JavaToken end = tokenRange.getEnd();
        int consecutive = 0;
        final List<JavaToken> pendingNewlines = new ArrayList<>();
        while (cur != null) {
            final String text = cur.getText();
            final boolean isNewline = "\n".equals(text) || "\r\n".equals(text);
            final boolean isWsWithNewline = text.matches("\\s+") && text.contains("\n");
            JavaToken next = cur.getNextToken().orElse(null);
            if (isNewline) {
                consecutive += 1;
                pendingNewlines.add(cur);
            } else if (isWsWithNewline) {
                final long count = text.chars().filter(c -> c == '\n').count();
                consecutive += (int) count;
                pendingNewlines.add(cur);
            } else if (!text.trim().isEmpty()) {
                if (consecutive > maxConsecutiveBlankLines + 1) {
                    final int excess = consecutive - (maxConsecutiveBlankLines + 1);
                    for (int i = 0; i < excess && !pendingNewlines.isEmpty(); i++) {
                        final JavaToken victim = pendingNewlines.remove(pendingNewlines.size() - 1);
                        victim.deleteToken();
                        methodChanged = true;
                    }
                }
                consecutive = 0;
                pendingNewlines.clear();
            }
            if (cur == end) {
                break;
            }
            cur = next;
        }
        return methodChanged;
    }

    private List<Finding> validateBlankLinesInLeafFunctions(Path root, Path file, String severity, int maxConsecutiveBlankLines) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            return cu.findAll(MethodDeclaration.class).stream()
                    .filter(this::isLeafMethod)
                    .flatMap(method -> method.getBody()
                            .flatMap(body -> body.getTokenRange())
                            .map(tokenRange -> collectBlankLineFindings(root, file, tokenRange, severity, maxConsecutiveBlankLines).stream())
                            .orElseGet(Stream::empty))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }

    private List<Finding> collectBlankLineFindings(Path root, Path file, TokenRange tokens, String severity, int maxConsecutiveBlankLines) {
        final Stream.Builder<Finding> builder = Stream.builder();
        final AtomicInteger lastNewlineCount = new AtomicInteger(0);
        tokens.forEach(token -> {
            final String text = token.getText();
            if ("\n".equals(text) || "\r\n".equals(text)) {
                lastNewlineCount.incrementAndGet();
            } else if (!text.trim().isEmpty() && !text.matches("\\s+")) {
                if (maxConsecutiveBlankLines + 1 < lastNewlineCount.get()) {
                    token.getRange().map(r -> r.begin.line).ifPresent(line -> {
                        if (0 < line) {
                            builder.add(Finding.of(severity, CATEGORY, root.relativize(file) + ":" + (line - 1) + ": too many blank lines in leaf function"));
                        }
                    });
                }
                lastNewlineCount.set(0);
            } else if (text.matches("\\s+") && text.contains("\n")) {
                lastNewlineCount.addAndGet((int) text.chars().filter(c -> c == '\n').count());
            }
        });
        return builder.build().collect(Collectors.toList());
    }

    /**
     * Returns the maximum allowed consecutive blank lines from the manifest.
     */
    private int maxConsecutiveBlankLines(JsonNode manifest) {
        final JsonNode section = manifest.get(CATEGORY);
        final JsonNode parameters = section == null ? null : section.get("parameters");
        final JsonNode value = parameters == null ? null : parameters.get("maxConsecutiveBlankLines");
        if (value != null && value.canConvertToInt()) {
            return Math.max(0, value.asInt());
        }
        return 1;
    }

    private boolean isLeafMethod(MethodDeclaration method) {
        return method.getBody()
                .map(body -> body.findAll(MethodDeclaration.class).isEmpty() && body.findAll(LambdaExpr.class).isEmpty())
                .orElse(false);
    }
}
